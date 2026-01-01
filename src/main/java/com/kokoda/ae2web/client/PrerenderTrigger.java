package com.kokoda.ae2web.client;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.kokoda.ae2web.client.render.RenderScheduler;
import com.kokoda.ae2web.config.AE2WebConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Automatically triggers pre-rendering when player joins server.
 */
@OnlyIn(Dist.CLIENT)
public class PrerenderTrigger {
    private static final int CHECK_DELAY_TICKS = 100; // 5 seconds after join
    private static final int CLAIM_BATCH_SIZE = 2000; // Batch size to avoid HTTP 413
    private static final int RETRY_CHECK_LIMIT = 2000; // Fetch more missing icons per retry check

    private static final ConcurrentHashMap<String, Integer> retryAttempts = new ConcurrentHashMap<>();
    private static final int MAX_RETRIES_PER_ICONKEY = 3;
    private static final long RETRY_DELAY_SECONDS = 5;

    private static volatile ScheduledExecutorService retryExecutor = null;
    private static final Object executorLock = new Object();

    private static volatile boolean pendingCheck = false;
    private static volatile int delayCounter = 0;
    private static volatile String serverUrl = null;

    @SubscribeEvent
    public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!AE2WebConfig.isPrerenderEnabled()) {
            return;
        }

        // Determine server URL
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            // Multiplayer - use server IP
            String serverIp = mc.getCurrentServer().ip;
            int port = AE2WebConfig.getHttpPort();

            // Extract host from server address, handling IPv4 and IPv6
            serverIp = extractHost(serverIp);

            serverUrl = "http://" + serverIp + ":" + port;
        } else {
            // Single player or LAN - use localhost
            serverUrl = "http://localhost:" + AE2WebConfig.getHttpPort();
        }

        synchronized (executorLock) {
            if (retryExecutor == null || retryExecutor.isShutdown()) {
                retryExecutor = Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r, "AE2Web-RetryScheduler");
                    t.setDaemon(true);
                    return t;
                });
            }
        }

        pendingCheck = true;
        delayCounter = CHECK_DELAY_TICKS;
        AE2WebMod.LOGGER.info("Player joined, will check for missing icons in {} ticks", CHECK_DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingCheck = false;
        delayCounter = 0;
        serverUrl = null;

        // Cancel any ongoing rendering
        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            scheduler.cancel();
        }

        PrerenderHttpClient.shutdownInstance();

        synchronized (executorLock) {
            if (retryExecutor != null && !retryExecutor.isShutdown()) {
                retryExecutor.shutdown();
                try {
                    if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        retryExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    retryExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            retryExecutor = null;
        }
        retryAttempts.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!pendingCheck) return;

        if (--delayCounter > 0) return;

        pendingCheck = false;

        // Trigger the check
        triggerMissingIconCheck();
    }

    private static void triggerMissingIconCheck() {
        // Capture serverUrl at the start to avoid race conditions in async callbacks
        String currentServerUrl = serverUrl;
        if (currentServerUrl == null) return;

        AE2WebMod.LOGGER.info("Checking for missing icons at {}", currentServerUrl);

        int configMax = AE2WebConfig.getPrerenderAutoMaxItems();
        int maxItems = configMax <= 0 ? 100000 : configMax; // 0 = unlimited

        PrerenderHttpClient client = PrerenderHttpClient.getInstance();
        client.fetchMissingIcons(currentServerUrl, maxItems)
                .thenAccept(missingItems -> {
                    // Check if still connected to the same server (not logged out or reconnected elsewhere)
                    if (!currentServerUrl.equals(serverUrl)) {
                        AE2WebMod.LOGGER.debug("Server changed or player logged out, cancelling prerender");
                        return;
                    }

                    if (missingItems.isEmpty()) {
                        AE2WebMod.LOGGER.info("No missing icons found");
                        return;
                    }

                    AE2WebMod.LOGGER.info("Found {} missing icons, processing in batches of {}",
                            missingItems.size(), CLAIM_BATCH_SIZE);

                    // Notify player of total count
                    notifyPlayer(missingItems.size());

                    processBatchedClaimsSequential(new ArrayList<>(missingItems), currentServerUrl, client)
                            .thenAccept(renderedCount -> {
                                // Check if still connected to the same server (not logged out or reconnected elsewhere)
                                if (!currentServerUrl.equals(serverUrl)) {
                                    AE2WebMod.LOGGER.debug("Server changed or player logged out, skipping completion handling");
                                    return;
                                }

                                notifyPlayerComplete(renderedCount);
                                scheduleRetryCheck(currentServerUrl);
                            });
                })
                .exceptionally(e -> {
                    AE2WebMod.LOGGER.warn("Failed to check for missing icons", e);
                    return null;
                });
    }

    /**
     * Process claims in batches to avoid HTTP 413 errors.
     * Each batch is claimed and rendered immediately, then the next batch is processed.
     */
    private static CompletableFuture<Integer> processBatchedClaimsSequential(List<PrerenderHttpClient.MissingIconItem> remainingItems,
                                                                            String targetServerUrl,
                                                                            PrerenderHttpClient client) {
        if (remainingItems.isEmpty()) {
            AE2WebMod.LOGGER.info("No more items to claim, batch processing complete");
            return CompletableFuture.completedFuture(0);
        }

        // Check if still connected to the same server
        if (!targetServerUrl.equals(serverUrl)) {
            AE2WebMod.LOGGER.warn("Server changed or player logged out, cancelling batched claims (remaining: {})",
                    remainingItems.size());
            return CompletableFuture.completedFuture(0);
        }

        int batchSize = Math.min(CLAIM_BATCH_SIZE, remainingItems.size());
        List<PrerenderHttpClient.MissingIconItem> batch = new ArrayList<>(remainingItems.subList(0, batchSize));
        List<PrerenderHttpClient.MissingIconItem> remaining = remainingItems.size() > batchSize
                ? new ArrayList<>(remainingItems.subList(batchSize, remainingItems.size()))
                : List.of();

        AE2WebMod.LOGGER.info("Processing batch: {} items (batch size: {}), {} remaining after this batch",
                remainingItems.size(), batchSize, remaining.size());

        List<String> iconKeys = batch.stream()
                .map(PrerenderHttpClient.MissingIconItem::iconKey)
                .collect(Collectors.toList());

        return client.claimItems(targetServerUrl, iconKeys)
                .handle((result, ex) -> {
                    if (ex != null) {
                        AE2WebMod.LOGGER.error("Failed to claim batch of {} items: {}", batchSize, ex.getMessage(), ex);
                        return null;
                    }
                    return result;
                })
                .thenCompose(result -> {
                    if (!targetServerUrl.equals(serverUrl)) {
                        AE2WebMod.LOGGER.warn("Server changed during claim, aborting batch processing");
                        return CompletableFuture.completedFuture(0);
                    }

                    List<PrerenderHttpClient.MissingIconItem> claimedItems = List.of();
                    if (result != null) {
                        AE2WebMod.LOGGER.info("Claim result: {} claimed, {} already rendered, {} locked by self, {} locked by others",
                                result.claimed().size(), result.alreadyRendered(),
                                result.lockedBySelf(), result.lockedByOthers());

                        if (!result.claimed().isEmpty()) {
                            claimedItems = batch.stream()
                                    .filter(item -> result.claimed().contains(item.iconKey()))
                                    .collect(Collectors.toList());
                        }
                    }

                    CompletableFuture<Integer> renderFuture;
                    if (claimedItems.isEmpty()) {
                        renderFuture = CompletableFuture.completedFuture(0);
                    } else {
                        AE2WebMod.LOGGER.info("Rendering {} claimed items from this batch", claimedItems.size());
                        RenderScheduler scheduler = RenderScheduler.getInstance();
                        renderFuture = scheduler.submitBatchWithNbtTracked(claimedItems, targetServerUrl);
                    }

                    return renderFuture.thenCompose(renderedThisBatch ->
                            processBatchedClaimsSequential(remaining, targetServerUrl, client)
                                    .thenApply(renderedRest -> renderedThisBatch + renderedRest)
                    );
                });
    }

    /**
     * Schedule a check for remaining missing items after render completes.
     * If items are still missing, automatically trigger another render cycle.
     */
    private static boolean shouldRetry(String iconKey) {
        int attempts = retryAttempts.compute(iconKey, (k, v) -> v == null ? 1 : v + 1);
        if (attempts > MAX_RETRIES_PER_ICONKEY) {
            if (attempts == MAX_RETRIES_PER_ICONKEY + 1) {
                AE2WebMod.LOGGER.warn("Icon {} exceeded retry limit ({} attempts)",
                        iconKey, MAX_RETRIES_PER_ICONKEY);
            }
            return false;
        }
        return true;
    }

    private static void scheduleRetryCheck(String targetServerUrl) {
        ScheduledExecutorService executor = retryExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        AE2WebMod.LOGGER.info("Scheduling retry check in {} seconds for server: {}",
                RETRY_DELAY_SECONDS, targetServerUrl);

        executor.schedule(() -> {
            try {
                AE2WebMod.LOGGER.info("Retry check triggered, checking for remaining missing items...");

                // Check if still connected to same server
                if (!targetServerUrl.equals(serverUrl)) {
                    AE2WebMod.LOGGER.warn("Server changed or player logged out, skipping retry check (expected: {}, current: {})",
                            targetServerUrl, serverUrl);
                    return;
                }

                PrerenderHttpClient client = PrerenderHttpClient.getInstance();
                AE2WebMod.LOGGER.info("Fetching missing icons from server to check retry status...");

                client.fetchMissingIcons(targetServerUrl, RETRY_CHECK_LIMIT)
                        .thenAccept(missingItems -> {
                            // Check again in case of server change during async operation
                            if (!targetServerUrl.equals(serverUrl)) {
                                AE2WebMod.LOGGER.warn("Server changed during retry check fetch, cancelling");
                                return;
                            }

                            if (missingItems.isEmpty()) {
                                AE2WebMod.LOGGER.info("Retry check complete: All items rendered successfully, no retry needed");
                                return;
                            }

                            List<PrerenderHttpClient.MissingIconItem> retryableItems = missingItems.stream()
                                    .filter(item -> shouldRetry(item.iconKey()))
                                    .collect(Collectors.toList());

                            if (retryableItems.isEmpty()) {
                                AE2WebMod.LOGGER.info("No retryable items remaining (all exceeded retry limit)");
                                return;
                            }

                            AE2WebMod.LOGGER.warn("RETRY TRIGGERED: Found {} items still missing after render (retryable: {}), initiating retry...",
                                    missingItems.size(), retryableItems.size());

                            // Trigger a new render cycle with the remaining missing items
                            Minecraft.getInstance().execute(() -> {
                                // Extract iconKeys for claiming
                                List<String> iconKeys = retryableItems.stream()
                                        .map(PrerenderHttpClient.MissingIconItem::iconKey)
                                        .collect(Collectors.toList());

                                AE2WebMod.LOGGER.info("Claiming {} items for retry render...", iconKeys.size());

                                // Claim and render the remaining items
                                client.claimItems(targetServerUrl, iconKeys)
                                        .thenAccept(result -> {
                                            if (!targetServerUrl.equals(serverUrl)) {
                                                AE2WebMod.LOGGER.warn("Server changed during retry claim, aborting");
                                                return;
                                            }

                                            AE2WebMod.LOGGER.info("Retry claim result: {} claimed, {} already rendered, {} locked by others",
                                                    result.claimed().size(), result.alreadyRendered(), result.lockedByOthers());

                                            if (!result.claimed().isEmpty()) {
                                                // Filter to only include claimed items
                                                List<PrerenderHttpClient.MissingIconItem> claimedItems = retryableItems.stream()
                                                        .filter(item -> result.claimed().contains(item.iconKey()))
                                                        .collect(Collectors.toList());

                                                AE2WebMod.LOGGER.info("Starting retry render for {} claimed items", claimedItems.size());
                                                RenderScheduler.getInstance()
                                                        .submitBatchWithNbtTracked(claimedItems, targetServerUrl)
                                                        .thenAccept(renderedCount -> {
                                                            if (!targetServerUrl.equals(serverUrl)) {
                                                                AE2WebMod.LOGGER.debug("Server changed or player logged out, skipping retry completion handling");
                                                                return;
                                                            }
                                                            notifyPlayerComplete(renderedCount);
                                                            scheduleRetryCheck(targetServerUrl);
                                                        });
                                            } else {
                                                AE2WebMod.LOGGER.warn("Retry claim returned 0 items - all items may be locked or already rendered");
                                            }
                                        })
                                        .exceptionally(claimEx -> {
                                            AE2WebMod.LOGGER.error("Failed to claim items during retry: {}", claimEx.getMessage(), claimEx);
                                            return null;
                                        });
                            });
                        })
                        .exceptionally(e -> {
                            AE2WebMod.LOGGER.error("Failed to check for remaining missing items during retry: {}", e.getMessage(), e);
                            return null;
                        });
            } catch (Exception e) {
                AE2WebMod.LOGGER.error("Exception in retry check", e);
            }
        }, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
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

    /**
     * Get the current server URL (for use by RenderCommand).
     */
    public static String getServerUrl() {
        return serverUrl;
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
