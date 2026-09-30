package com.kokoda.ae2web.client.command;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.PrerenderTrigger;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.kokoda.ae2web.client.render.RenderScheduler;
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
                                .executes(ctx -> executeRender(ctx, 0))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100000))
                                        .executes(ctx -> executeRender(ctx, IntegerArgumentType.getInteger(ctx, "limit"))))
                                .then(Commands.literal("status")
                                        .executes(RenderCommand::executeStatus))
                                .then(Commands.literal("cancel")
                                        .executes(RenderCommand::executeCancel))
                                .then(Commands.literal("full")
                                        .executes(RenderCommand::executeRenderFull)))
        );
    }

    /**
     * Render MISSING items.
     * @param limit 0 = unlimited, otherwise limit the number of items to render
     */
    private static int executeRender(CommandContext<CommandSourceStack> ctx, int limit) {
        String serverUrl = PrerenderTrigger.getOrResolveServerUrl();

        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            sendMessage(ctx, "Rendering already in progress. Use /ae2web render cancel to stop.");
            return 0;
        }

        String limitStr = limit > 0 ? " (limit: " + limit + ")" : "";
        sendMessage(ctx, "Fetching missing icons from server" + limitStr + "...");

        String finalServerUrl = serverUrl;
        int fetchLimit = limit > 0 ? limit : 100000;
        PrerenderHttpClient client = PrerenderHttpClient.getInstance();

        client.fetchMissingIcons(finalServerUrl, fetchLimit)
                .thenAccept(missingItems -> {
                    if (missingItems.isEmpty()) {
                        Minecraft.getInstance().execute(() ->
                                sendMessage(ctx, "No missing icons found."));
                        return;
                    }

                    Minecraft.getInstance().execute(() ->
                            sendMessage(ctx, "Got " + missingItems.size() + " missing icons, claiming..."));

                    // Extract iconKeys for claiming
                    List<String> iconKeys = missingItems.stream()
                            .map(PrerenderHttpClient.MissingIconItem::iconKey)
                            .collect(Collectors.toList());

                    client.claimItems(finalServerUrl, iconKeys)
                            .thenAccept(result -> {
                                if (result.claimed().isEmpty()) {
                                    Minecraft.getInstance().execute(() -> {
                                        if (result.hasLockedByOthers()) {
                                            sendMessage(ctx, "All " + result.lockedByOthers() + " items being rendered by another client.");
                                        } else if (result.alreadyRendered() > 0) {
                                            sendMessage(ctx, "All " + result.alreadyRendered() + " items already rendered.");
                                        } else {
                                            sendMessage(ctx, "No items to render.");
                                        }
                                    });
                                    return;
                                }

                                // Filter to only include claimed items
                                List<PrerenderHttpClient.MissingIconItem> claimedItems = missingItems.stream()
                                        .filter(item -> result.claimed().contains(item.iconKey()))
                                        .collect(Collectors.toList());

                                Minecraft.getInstance().execute(() -> {
                                    String extra = "";
                                    if (result.lockedBySelf() > 0) {
                                        extra = " (including " + result.lockedBySelf() + " already claimed by this client)";
                                    }
                                    sendMessage(ctx, "Starting render for " + result.claimed().size() + " items" + extra + "...");
                                    startRenderWithNbt(ctx, claimedItems, finalServerUrl);
                                });
                            });
                })
                .exceptionally(e -> {
                    AE2WebMod.LOGGER.error("Failed to fetch missing icons", e);
                    Minecraft.getInstance().execute(() ->
                            sendMessage(ctx, "Failed: " + e.getMessage()));
                    return null;
                });

        return 1;
    }

    /**
     * Force re-render ALL items (including already rendered ones).
     */
    private static int executeRenderFull(CommandContext<CommandSourceStack> ctx) {
        String serverUrl = PrerenderTrigger.getOrResolveServerUrl();

        RenderScheduler scheduler = RenderScheduler.getInstance();
        if (scheduler.isActive()) {
            sendMessage(ctx, "Rendering already in progress. Use /ae2web render cancel to stop.");
            return 0;
        }

        sendMessage(ctx, "Fetching ALL items for full re-render...");

        String finalServerUrl = serverUrl;
        PrerenderHttpClient client = PrerenderHttpClient.getInstance();

        // Get stats to know total item count
        client.getStats(serverUrl).thenAccept(stats -> {
            int total = stats.getOrDefault("totalItems", 0);
            if (total == 0) {
                Minecraft.getInstance().execute(() ->
                        sendMessage(ctx, "No items found on server."));
                return;
            }

            Minecraft.getInstance().execute(() ->
                    sendMessage(ctx, "Found " + total + " total items, fetching list for full re-render..."));

            // Fetch ALL items (not just missing)
            client.fetchAllItems(finalServerUrl, 100000)
                    .thenAccept(allIds -> {
                        if (allIds.isEmpty()) {
                            Minecraft.getInstance().execute(() ->
                                    sendMessage(ctx, "Server returned empty list. Try again."));
                            return;
                        }

                        Minecraft.getInstance().execute(() -> {
                            sendMessage(ctx, "Starting FULL re-render for " + allIds.size() + " items...");
                            startRenderDirect(ctx, allIds, finalServerUrl);
                        });
                    })
                    .exceptionally(e -> {
                        AE2WebMod.LOGGER.error("Failed to fetch all items", e);
                        Minecraft.getInstance().execute(() ->
                                sendMessage(ctx, "Failed: " + e.getMessage()));
                        return null;
                    });
        }).exceptionally(e -> {
            AE2WebMod.LOGGER.error("Failed to get stats", e);
            Minecraft.getInstance().execute(() ->
                    sendMessage(ctx, "Failed to connect to server: " + e.getMessage()));
            return null;
        });

        return 1;
    }

    /**
     * Start rendering without claiming (for full re-render).
     */
    private static void startRenderDirect(CommandContext<CommandSourceStack> ctx, List<String> iconKeys, String serverUrl) {
        RenderScheduler scheduler = RenderScheduler.getInstance();
        scheduler.setProgressListener(progress -> {
            if (progress.current() == null) {
                Minecraft.getInstance().execute(() ->
                        sendMessage(ctx, "Full re-render complete: " + progress.completed() + " items"));
            } else if (progress.completed() % 100 == 0) {
                Minecraft.getInstance().execute(() ->
                        sendMessage(ctx, "Re-rendering: " + progress.completed() + "/" + progress.total()));
            }
        });

        scheduler.submitBatchWithIconKeys(iconKeys, serverUrl);
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

    private static void startRenderWithNbt(CommandContext<CommandSourceStack> ctx,
                                           List<PrerenderHttpClient.MissingIconItem> items,
                                           String serverUrl) {
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

        scheduler.submitBatchWithNbt(items, serverUrl);
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
                                    sendMessage(ctx, "  NBT variants: " + stats.getOrDefault("nbtVariantCount", 0));
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
