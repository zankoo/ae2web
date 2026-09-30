package com.kokoda.ae2web.client;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.kokoda.ae2web.client.render.RenderScheduler;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.network.ServerInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Automatically pre-renders missing icons while connected.
 *
 * Design goals:
 * - Keep the web UI fully NBT-accurate (new variants appear constantly in ME)
 * - Avoid infinite retry loops (per-icon backoff + per-session failure cap)
 * - Avoid registry-scale scans during steady-state (server missing endpoint defaults to active keys only)
 */
@OnlyIn(Dist.CLIENT)
public class PrerenderTrigger {
    private static final int JOIN_DELAY_SECONDS = 5;
    private static final int CLAIM_BATCH_SIZE = 2000; // Batch size to avoid HTTP 413
    private static final int DEFAULT_FETCH_LIMIT = 2000; // Keep payload sizes bounded
    private static final int ACTIVE_POLL_SECONDS = 5;
    private static final int IDLE_POLL_SECONDS = 15;
    private static final int BOOTSTRAP_POLL_SECONDS = 1;

    private static final int MAX_FAILURES_PER_ICONKEY = 5;

    private record RetryState(int failures, long nextAllowedAtMs) {}
    private static final ConcurrentHashMap<String, RetryState> retryState = new ConcurrentHashMap<>();

    private static volatile ScheduledExecutorService pollExecutor = null;
    private static final Object executorLock = new Object();

    private static volatile String serverUrl = null;
    private static volatile boolean pollingEnabled = false;
    private static volatile boolean bootstrapChecked = false;
    private static volatile boolean bootstrapAll = false;
    private static volatile boolean bootstrapNotified = false;

    /**
     * Called on the client thread when the server sends its web settings after login.
     */
    public static void onServerInfo(ServerInfoPacket info) {
        ServerConnectionInfo.set(info);
        if (!info.isPrerenderEnabled()) {
            return;
        }

        serverUrl = buildServerUrl(info.getHttpPort());

        synchronized (executorLock) {
            if (pollExecutor == null || pollExecutor.isShutdown()) {
                pollExecutor = Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r, "AE2Web-PrerenderPoll");
                    t.setDaemon(true);
                    return t;
                });
            }
        }

        pollingEnabled = true;
        retryState.clear();
        bootstrapChecked = false;
        bootstrapAll = false;
        bootstrapNotified = false;
        AE2WebMod.LOGGER.info("Player joined, will start prerender polling in {} seconds (serverUrl={})",
                JOIN_DELAY_SECONDS, serverUrl);

        schedulePoll(serverUrl, JOIN_DELAY_SECONDS);
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pollingEnabled = false;
        serverUrl = null;
        ServerConnectionInfo.clear();
        bootstrapChecked = false;
        bootstrapAll = false;
        bootstrapNotified = false;

        // Cancel any ongoing rendering
        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            scheduler.cancel();
        }

        PrerenderHttpClient.shutdownInstance();

        synchronized (executorLock) {
            if (pollExecutor != null && !pollExecutor.isShutdown()) {
                pollExecutor.shutdown();
            }
            pollExecutor = null;
        }
        retryState.clear();
    }

    private static void schedulePoll(String targetServerUrl, long delaySeconds) {
        ScheduledExecutorService executor = pollExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        executor.schedule(() -> pollOnce(targetServerUrl), delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Process claims in batches to avoid HTTP 413 errors.
     * Each batch is claimed and rendered immediately, then the next batch is processed.
     */
    private static CompletableFuture<Integer> processBatchedClaimsSequential(List<PrerenderHttpClient.MissingIconItem> remainingItems,
                                                                            String targetServerUrl,
                                                                            PrerenderHttpClient client) {
        if (remainingItems.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        // Check if still connected to the same server
        if (!targetServerUrl.equals(serverUrl)) {
            return CompletableFuture.completedFuture(0);
        }

        int batchSize = Math.min(CLAIM_BATCH_SIZE, remainingItems.size());
        List<PrerenderHttpClient.MissingIconItem> batch = new ArrayList<>(remainingItems.subList(0, batchSize));
        List<PrerenderHttpClient.MissingIconItem> remaining = remainingItems.size() > batchSize
                ? new ArrayList<>(remainingItems.subList(batchSize, remainingItems.size()))
                : List.of();

        List<String> iconKeys = batch.stream()
                .map(PrerenderHttpClient.MissingIconItem::iconKey)
                .collect(Collectors.toList());

        return client.claimItems(targetServerUrl, iconKeys)
                .handle((result, ex) -> {
                    if (ex != null) {
                        return null;
                    }
                    return result;
                })
                .thenCompose(result -> {
                    if (!targetServerUrl.equals(serverUrl)) {
                        return CompletableFuture.completedFuture(0);
                    }

                    List<PrerenderHttpClient.MissingIconItem> claimedItems = List.of();
                    if (result != null) {
                        if (!result.claimed().isEmpty()) {
                            claimedItems = batch.stream()
                                    .filter(item -> result.claimed().contains(item.iconKey()))
                                    .collect(Collectors.toList());
                        }
                    }

                    CompletableFuture<Integer> renderFuture;
                    List<String> claimedIconKeys;
                    if (claimedItems.isEmpty()) {
                        renderFuture = CompletableFuture.completedFuture(0);
                        claimedIconKeys = List.of();
                    } else {
                        RenderScheduler scheduler = RenderScheduler.getInstance();
                        renderFuture = scheduler.submitBatchWithNbtTracked(claimedItems, targetServerUrl);
                        claimedIconKeys = claimedItems.stream().map(PrerenderHttpClient.MissingIconItem::iconKey).toList();
                    }

                    return renderFuture
                            .thenCompose(renderedThisBatch -> client.checkItems(targetServerUrl, claimedIconKeys)
                                    .thenApply(check -> {
                                        updateRetryStateFromCheck(check);
                                        return renderedThisBatch;
                                    }))
                            .thenCompose(renderedThisBatch ->
                                    processBatchedClaimsSequential(remaining, targetServerUrl, client)
                                            .thenApply(renderedRest -> renderedThisBatch + renderedRest)
                            );
                });
    }

    private static void pollOnce(String targetServerUrl) {
        if (!pollingEnabled) {
            return;
        }

        if (targetServerUrl == null || !targetServerUrl.equals(serverUrl)) {
            return;
        }

        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            schedulePoll(targetServerUrl, 1);
            return;
        }

        ServerInfoPacket info = ServerConnectionInfo.get();
        int configMax = info != null ? info.getPrerenderAutoMaxItems() : 0;
        int fetchLimit = configMax <= 0 ? DEFAULT_FETCH_LIMIT : Math.min(DEFAULT_FETCH_LIMIT, configMax);

        PrerenderHttpClient client = PrerenderHttpClient.getInstance();
        CompletableFuture<List<PrerenderHttpClient.MissingIconItem>> missingFuture;
        if (!bootstrapChecked) {
            bootstrapChecked = true;
            missingFuture = client.getStats(targetServerUrl)
                    .thenCompose(stats -> {
                        if (!pollingEnabled || !targetServerUrl.equals(serverUrl)) {
                            return CompletableFuture.completedFuture(List.of());
                        }

                        Integer renderedTotal = stats.get("renderedTotalCount");
                        bootstrapAll = renderedTotal != null && renderedTotal <= 0;
                        if (bootstrapAll && !bootstrapNotified) {
                            bootstrapNotified = true;
                            notifyBootstrap();
                        }

                        if (bootstrapAll) {
                            return client.fetchMissingIcons(targetServerUrl, DEFAULT_FETCH_LIMIT, "all");
                        }
                        return client.fetchMissingIcons(targetServerUrl, fetchLimit);
                    });
        } else if (bootstrapAll) {
            missingFuture = client.fetchMissingIcons(targetServerUrl, DEFAULT_FETCH_LIMIT, "all");
        } else {
            missingFuture = client.fetchMissingIcons(targetServerUrl, fetchLimit);
        }

        missingFuture.thenCompose(missingItems -> {
                    if (!pollingEnabled || !targetServerUrl.equals(serverUrl)) {
                        return CompletableFuture.completedFuture(null);
                    }

                    if (missingItems.isEmpty()) {
                        if (bootstrapAll) {
                            bootstrapAll = false;
                        }
                        schedulePoll(targetServerUrl, IDLE_POLL_SECONDS);
                        return CompletableFuture.completedFuture(null);
                    }

                    List<PrerenderHttpClient.MissingIconItem> eligible = filterEligible(missingItems);
                    if (eligible.isEmpty()) {
                        schedulePoll(targetServerUrl, IDLE_POLL_SECONDS);
                        return CompletableFuture.completedFuture(null);
                    }

                    notifyPlayer(eligible.size());

                    return processBatchedClaimsSequential(new ArrayList<>(eligible), targetServerUrl, client)
                            .thenAccept(PrerenderTrigger::notifyPlayerComplete)
                            .thenRun(() -> schedulePoll(targetServerUrl, bootstrapAll ? BOOTSTRAP_POLL_SECONDS : ACTIVE_POLL_SECONDS));
                })
                .exceptionally(e -> {
                    AE2WebMod.LOGGER.debug("Prerender poll failed: {}", e.getMessage());
                    schedulePoll(targetServerUrl, IDLE_POLL_SECONDS);
                    return null;
                });
    }

    private static List<PrerenderHttpClient.MissingIconItem> filterEligible(List<PrerenderHttpClient.MissingIconItem> missingItems) {
        long now = System.currentTimeMillis();

        Map<String, PrerenderHttpClient.MissingIconItem> unique = new LinkedHashMap<>();
        for (PrerenderHttpClient.MissingIconItem item : missingItems) {
            if (item == null || item.iconKey() == null || item.iconKey().isBlank()) {
                continue;
            }
            if (!unique.containsKey(item.iconKey())) {
                unique.put(item.iconKey(), item);
            }
        }

        List<PrerenderHttpClient.MissingIconItem> eligible = new ArrayList<>();
        for (PrerenderHttpClient.MissingIconItem item : unique.values()) {
            RetryState state = retryState.get(item.iconKey());
            if (state == null) {
                eligible.add(item);
                continue;
            }
            if (state.failures() >= MAX_FAILURES_PER_ICONKEY) {
                continue;
            }
            if (now >= state.nextAllowedAtMs()) {
                eligible.add(item);
            }
        }
        return eligible;
    }

    private static void updateRetryStateFromCheck(PrerenderHttpClient.CheckResult check) {
        if (check == null) {
            return;
        }

        for (String ok : check.existing()) {
            retryState.remove(ok);
        }

        long now = System.currentTimeMillis();
        for (String stillMissing : check.missing()) {
            retryState.compute(stillMissing, (k, v) -> {
                int failures = v == null ? 1 : (v.failures() + 1);
                if (failures >= MAX_FAILURES_PER_ICONKEY) {
                    AE2WebMod.LOGGER.warn("Icon {} exceeded failure limit ({}), suppressing for this session",
                            stillMissing, MAX_FAILURES_PER_ICONKEY);
                }
                long backoffSeconds = backoffSeconds(failures);
                return new RetryState(failures, now + backoffSeconds * 1000L);
            });
        }
    }

    private static long backoffSeconds(int failures) {
        return switch (failures) {
            case 0 -> 0;
            case 1 -> 5;
            case 2 -> 30;
            case 3 -> 120;
            case 4 -> 600;
            default -> 1800;
        };
    }

    private static void notifyPlayer(int count) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            mc.execute(() -> {
                player.displayClientMessage(
                        Component.literal("[AE2Web] Rendering " + count + " missing item icons..."),
                        true
                );
            });
        }
    }

    private static void notifyPlayerComplete(int count) {
        if (count <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            mc.execute(() -> {
                player.displayClientMessage(
                        Component.literal("[AE2Web] Rendered " + count + " item icons"),
                        true
                );
            });
        }
    }

    private static void notifyBootstrap() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            mc.execute(() -> {
                player.displayClientMessage(
                        Component.literal("[AE2Web] Detected empty prerender cache. Bootstrapping full icon render..."),
                        false
                );
            });
        }
    }

    /**
     * Get the current server URL (for use by RenderCommand).
     */
    public static String getServerUrl() {
        return serverUrl;
    }

    /**
     * The current server URL, or one built from the server-provided (or local fallback) port.
     */
    public static String getOrResolveServerUrl() {
        String current = serverUrl;
        if (current != null) {
            return current;
        }
        ServerInfoPacket info = ServerConnectionInfo.get();
        int port = info != null ? info.getHttpPort() : AE2WebConfig.getHttpPort();
        current = buildServerUrl(port);
        serverUrl = current;
        return current;
    }

    private static String buildServerUrl(int port) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            // Multiplayer - use server IP
            return "http://" + extractHost(mc.getCurrentServer().ip) + ":" + port;
        }
        // Single player or LAN host - use localhost
        return "http://localhost:" + port;
    }

    /**
     * Set the server URL manually.
     */
    public static void setServerUrl(String url) {
        serverUrl = url;
    }

    /**
     * Extract host from server address, handling IPv4, IPv6, and ports.
     * Examples:
     * - "192.168.1.1:25565" -> "192.168.1.1"
     * - "192.168.1.1" -> "192.168.1.1"
     * - "[::1]:25565" -> "[::1]"
     * - "[2001:db8::1]:25565" -> "[2001:db8::1]"
     * - "::1" -> "[::1]" (wrap in brackets for URL compatibility)
     */
    private static String extractHost(String address) {
        if (address == null || address.isEmpty()) {
            return "localhost";
        }

        // IPv6 with brackets: [ip]:port or [ip]
        if (address.startsWith("[")) {
            int bracketEnd = address.indexOf(']');
            if (bracketEnd > 0) {
                return address.substring(0, bracketEnd + 1);
            }
            return address;
        }

        // Check if it looks like IPv6 without brackets (contains multiple colons)
        int colonCount = address.length() - address.replace(":", "").length();
        if (colonCount > 1) {
            // IPv6 without brackets - wrap it
            return "[" + address + "]";
        }

        // IPv4 with port: ip:port
        int lastColon = address.lastIndexOf(':');
        if (lastColon > 0) {
            return address.substring(0, lastColon);
        }

        // Plain hostname or IPv4 without port
        return address;
    }
}
