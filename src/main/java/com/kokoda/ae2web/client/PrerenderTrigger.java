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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Automatically triggers pre-rendering when player joins server.
 */
@OnlyIn(Dist.CLIENT)
public class PrerenderTrigger {
    private static final int CHECK_DELAY_TICKS = 100; // 5 seconds after join
    private static final int MAX_AUTO_RENDER = 500;

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

            // Handle port in server address
            if (serverIp.contains(":")) {
                String[] parts = serverIp.split(":");
                serverIp = parts[0];
            }

            serverUrl = "http://" + serverIp + ":" + port;
        } else {
            // Single player or LAN - use localhost
            serverUrl = "http://localhost:" + AE2WebConfig.getHttpPort();
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

        int maxItems = Math.min(MAX_AUTO_RENDER, AE2WebConfig.getPrerenderAutoMaxItems());

        PrerenderHttpClient client = PrerenderHttpClient.getInstance();
        client.fetchMissingIcons(currentServerUrl, maxItems)
                .thenAccept(missingIds -> {
                    // Check if still connected (serverUrl not cleared)
                    if (serverUrl == null) {
                        AE2WebMod.LOGGER.debug("Player logged out, cancelling prerender");
                        return;
                    }

                    if (missingIds.isEmpty()) {
                        AE2WebMod.LOGGER.info("No missing icons found");
                        return;
                    }

                    AE2WebMod.LOGGER.info("Found {} missing icons, claiming for rendering", missingIds.size());

                    // Claim items to avoid concurrent rendering
                    client.claimItems(currentServerUrl, missingIds)
                            .thenAccept(claimedIds -> {
                                // Check if still connected
                                if (serverUrl == null) {
                                    AE2WebMod.LOGGER.debug("Player logged out, cancelling prerender");
                                    return;
                                }

                                if (claimedIds.isEmpty()) {
                                    AE2WebMod.LOGGER.info("No items claimed (may be rendered by another client)");
                                    return;
                                }

                                notifyPlayer(claimedIds.size());
                                startAutoRender(claimedIds, currentServerUrl);
                            });
                })
                .exceptionally(e -> {
                    AE2WebMod.LOGGER.warn("Failed to check for missing icons", e);
                    return null;
                });
    }

    private static void startAutoRender(List<String> itemIds, String targetServerUrl) {
        AE2WebMod.LOGGER.info("Starting auto-render for {} items", itemIds.size());

        List<ResourceLocation> resourceLocations = itemIds.stream()
                .map(ResourceLocation::tryParse)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        RenderScheduler scheduler = RenderScheduler.getInstance();
        scheduler.setProgressListener(progress -> {
            if (progress.current() == null) {
                // Render complete
                notifyPlayerComplete(progress.completed());
            }
        });

        scheduler.submitBatch(resourceLocations, targetServerUrl);
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
}
