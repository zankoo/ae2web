package com.kokoda.ae2web.client.command;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.PrerenderTrigger;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.kokoda.ae2web.client.render.RenderScheduler;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Client-side command: /ae2web render
 */
@OnlyIn(Dist.CLIENT)
public class RenderCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ae2web")
                        .then(Commands.literal("render")
                                .executes(ctx -> executeRender(ctx, false, 500))
                                .then(Commands.literal("status")
                                        .executes(RenderCommand::executeStatus))
                                .then(Commands.literal("cancel")
                                        .executes(RenderCommand::executeCancel))
                                .then(Commands.literal("full")
                                        .executes(ctx -> executeRender(ctx, true, 5000))
                                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 10000))
                                                .executes(ctx -> executeRender(ctx, true,
                                                        IntegerArgumentType.getInteger(ctx, "limit"))))))
        );
    }

    private static int executeRender(CommandContext<CommandSourceStack> ctx, boolean full, int limit) {
        String serverUrl = PrerenderTrigger.getServerUrl();
        if (serverUrl == null) {
            serverUrl = "http://localhost:" + AE2WebConfig.getHttpPort();
            PrerenderTrigger.setServerUrl(serverUrl);
        }

        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            sendMessage(ctx, "Rendering already in progress. Use /ae2web render cancel to stop.");
            return 0;
        }

        sendMessage(ctx, "Fetching missing icons from server...");

        String finalServerUrl = serverUrl;
        PrerenderHttpClient client = PrerenderHttpClient.getInstance();

        client.fetchMissingIcons(serverUrl, limit)
                .thenAccept(missingIds -> {
                    if (missingIds.isEmpty()) {
                        Minecraft.getInstance().execute(() ->
                                sendMessage(ctx, "No missing icons found."));
                        return;
                    }

                    // Claim items
                    client.claimItems(finalServerUrl, missingIds)
                            .thenAccept(claimedIds -> {
                                if (claimedIds.isEmpty()) {
                                    Minecraft.getInstance().execute(() ->
                                            sendMessage(ctx, "No items to render (already being rendered by another client)."));
                                    return;
                                }

                                Minecraft.getInstance().execute(() -> {
                                    sendMessage(ctx, "Starting render for " + claimedIds.size() + " items...");
                                    startRender(ctx, claimedIds, finalServerUrl);
                                });
                            });
                })
                .exceptionally(e -> {
                    AE2WebMod.LOGGER.error("Failed to fetch missing icons", e);
                    Minecraft.getInstance().execute(() ->
                            sendMessage(ctx, "Failed to connect to server: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    private static void startRender(CommandContext<CommandSourceStack> ctx, List<String> itemIds, String serverUrl) {
        List<ResourceLocation> resourceLocations = itemIds.stream()
                .map(ResourceLocation::tryParse)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        RenderScheduler scheduler = RenderScheduler.getInstance();
        scheduler.setProgressListener(progress -> {
            if (progress.current() == null) {
                // Render complete
                Minecraft.getInstance().execute(() ->
                        sendMessage(ctx, "Render complete: " + progress.completed() + " items"));
            } else if (progress.completed() % 50 == 0) {
                // Progress update every 50 items
                Minecraft.getInstance().execute(() ->
                        sendMessage(ctx, "Rendering: " + progress.completed() + "/" + progress.total()));
            }
        });

        scheduler.submitBatch(resourceLocations, serverUrl);
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        RenderScheduler scheduler = RenderScheduler.getInstance();

        if (!scheduler.isActive()) {
            sendMessage(ctx, "No rendering in progress.");

            String serverUrl = PrerenderTrigger.getServerUrl();
            if (serverUrl != null) {
                PrerenderHttpClient.getInstance().getStats(serverUrl)
                        .thenAccept(stats -> {
                            if (!stats.isEmpty()) {
                                Minecraft.getInstance().execute(() -> {
                                    sendMessage(ctx, "Server stats:");
                                    sendMessage(ctx, "  Total items: " + stats.getOrDefault("totalItems", 0));
                                    sendMessage(ctx, "  Rendered: " + stats.getOrDefault("renderedCount", 0));
                                    sendMessage(ctx, "  Missing: " + stats.getOrDefault("missingCount", 0));
                                });
                            }
                        });
            }
            return 1;
        }

        int completed = scheduler.getCompletedCount();
        int total = scheduler.getTotalTasks();
        int pending = scheduler.getPendingCount();
        float progress = scheduler.getProgress() * 100;

        sendMessage(ctx, String.format("Rendering: %d/%d (%.1f%%), %d pending",
                completed, total, progress, pending));

        return 1;
    }

    private static int executeCancel(CommandContext<CommandSourceStack> ctx) {
        RenderScheduler scheduler = RenderScheduler.getInstance();

        if (!scheduler.isActive()) {
            sendMessage(ctx, "No rendering in progress.");
            return 0;
        }

        scheduler.cancel();
        sendMessage(ctx, "Rendering cancelled.");
        return 1;
    }

    private static void sendMessage(CommandContext<CommandSourceStack> ctx, String message) {
        try {
            ctx.getSource().sendSuccess(() -> Component.literal("[AE2Web] " + message), false);
        } catch (Exception e) {
            // Fallback for client-side command
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[AE2Web] " + message), false);
            }
        }
    }
}
