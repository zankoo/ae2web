package com.kokoda.ae2web.client.render;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.util.LegacyTextFormatter;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Schedules item rendering across multiple frames.
 * Optimized for fast rendering - user experience on web UI is prioritized over client smoothness.
 */
@OnlyIn(Dist.CLIENT)
public class RenderScheduler {
    private static final int DEFAULT_ITEMS_PER_FRAME = 50; // Optimized for speed

    private static RenderScheduler instance;

    private final ArrayDeque<RenderTask> taskQueue = new ArrayDeque<>();
    private final ItemIconRenderer renderer;
    private int itemsPerFrame = DEFAULT_ITEMS_PER_FRAME;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger successfulCount = new AtomicInteger(0);
    private final AtomicInteger inFlightCount = new AtomicInteger(0);
    private final AtomicBoolean queueDrained = new AtomicBoolean(false);
    private final AtomicLong runId = new AtomicLong(0);
    private volatile int totalTasks = 0;
    private volatile Consumer<RenderProgress> progressListener;
    private long lastProgressUpdate = 0;

    private RenderScheduler() {
        this.renderer = ItemIconRenderer.getInstance();
    }

    public static RenderScheduler getInstance() {
        if (instance == null) {
            instance = new RenderScheduler();
        }
        return instance;
    }

    public record RenderProgress(int completed, int total, ResourceLocation current) {}

    /**
     * Result of rendering an item: PNG bytes and translated name.
     */
    public record RenderResult(byte[] png, String translatedName, List<String> tooltip) {}

    /**
     * Render task with optional NBT data and iconKey.
     * Completion is defined as: server responded to upload (icon saved or upload failed).
     */
    private record RenderTask(ResourceLocation itemId, String iconKey, byte[] nbtData,
                              String serverUrl, long runId, CompletableFuture<Boolean> completion,
                              Consumer<RenderResult> onRenderComplete) {}

    private void startNewRunIfNeededLocked() {
        if (isActive.get()) {
            return;
        }

        runId.incrementAndGet();
        completedCount.set(0);
        successfulCount.set(0);
        inFlightCount.set(0);
        totalTasks = 0;
        queueDrained.set(false);
    }

    /**
     * Submit a batch of items for rendering.
     * If called while already rendering, adds items to the queue without resetting progress.
     */
    public void submitBatch(List<ResourceLocation> itemIds, String serverUrl) {
        if (itemIds.isEmpty()) return;

        synchronized (taskQueue) {
            startNewRunIfNeededLocked();
            long currentRunId = runId.get();
            queueDrained.set(false);

            int added = 0;
            for (ResourceLocation itemId : itemIds) {
                String iconKey = computeDefaultIconKey(itemId);
                taskQueue.add(new RenderTask(itemId, iconKey, null, serverUrl, currentRunId, new CompletableFuture<>(), null));
                added++;
            }
            totalTasks += added;
            isActive.set(true);
        }

        AE2WebMod.LOGGER.info("Submitted {} items for rendering", itemIds.size());
    }

    /**
     * Submit a batch of iconKeys for rendering (for full re-render).
     * iconKey format: {@code namespace:path@hash}.
     */
    public void submitBatchWithIconKeys(List<String> iconKeys, String serverUrl) {
        if (iconKeys.isEmpty()) return;

        synchronized (taskQueue) {
            startNewRunIfNeededLocked();
            long currentRunId = runId.get();
            queueDrained.set(false);

            int added = 0;
            for (String iconKey : iconKeys) {
                if (iconKey == null || iconKey.isBlank()) {
                    continue;
                }

                int atIndex = iconKey.lastIndexOf('@');
                String itemIdStr = atIndex > 0 ? iconKey.substring(0, atIndex) : iconKey;
                ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
                if (itemId == null) {
                    AE2WebMod.LOGGER.warn("Invalid item ID in iconKey: {}", iconKey);
                    continue;
                }

                added++;
                taskQueue.add(new RenderTask(itemId, iconKey, null, serverUrl, currentRunId, new CompletableFuture<>(), null));
            }

            totalTasks += added;
            isActive.set(true);

            AE2WebMod.LOGGER.info("Submitted {} iconKeys for rendering", added);
        }
    }

    /**
     * Submit a batch of items with NBT data for rendering.
     */
    public void submitBatchWithNbt(List<PrerenderHttpClient.MissingIconItem> items, String serverUrl) {
        submitBatchWithNbtTracked(items, serverUrl);
    }

    /**
     * Submit a batch of items with NBT data for rendering and complete a future once every task from this submission
     * has finished rendering (successfully or not). This avoids relying on "queue exhausted" semantics when producers
     * may enqueue more work later.
     */
    public CompletableFuture<Integer> submitBatchWithNbtTracked(List<PrerenderHttpClient.MissingIconItem> items, String serverUrl) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        List<CompletableFuture<Boolean>> completions = new ArrayList<>();
        int added;

        synchronized (taskQueue) {
            startNewRunIfNeededLocked();
            long currentRunId = runId.get();
            queueDrained.set(false);

            added = 0;
            for (PrerenderHttpClient.MissingIconItem item : items) {
                String iconKey = item.iconKey();
                int atIndex = iconKey.lastIndexOf('@');
                String itemIdStr = atIndex > 0 ? iconKey.substring(0, atIndex) : iconKey;
                ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);

                if (itemId == null) {
                    AE2WebMod.LOGGER.warn("Invalid item ID in iconKey: {}", iconKey);
                    continue;
                }

                CompletableFuture<Boolean> completion = new CompletableFuture<>();
                completions.add(completion);
                taskQueue.add(new RenderTask(itemId, iconKey, item.stackNbt(), serverUrl, currentRunId, completion, null));
                added++;
            }

            totalTasks += added;
            isActive.set(true);
        }

        if (added == 0) {
            return CompletableFuture.completedFuture(0);
        }

        AE2WebMod.LOGGER.info("Submitted {} items with NBT for tracked rendering", added);

        CompletableFuture<?> allDone = CompletableFuture.allOf(completions.toArray(new CompletableFuture[0]));
        return allDone.thenApply(ignored -> (int) completions.stream().filter(f -> f.getNow(false)).count());
    }

    /**
     * Submit a single item for rendering.
     */
    public void submitTask(ResourceLocation itemId, Consumer<RenderResult> onComplete) {
        synchronized (taskQueue) {
            startNewRunIfNeededLocked();
            long currentRunId = runId.get();
            queueDrained.set(false);

            String iconKey = computeDefaultIconKey(itemId);
            taskQueue.add(new RenderTask(itemId, iconKey, null, null, currentRunId, new CompletableFuture<>(), onComplete));
            totalTasks += 1;
            isActive.set(true);
        }
    }

    private static String computeDefaultIconKey(ResourceLocation id) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item != null) {
            return IconKeyUtils.iconKey(AEItemKey.of(item));
        }

        var fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid != null && fluid != Fluids.EMPTY) {
            return IconKeyUtils.iconKey(AEFluidKey.of(fluid));
        }

        // Fallback: ensure we still upload an iconKey-shaped identifier.
        return IconKeyUtils.iconKey(id);
    }

    /**
     * Called each frame from client tick event.
     * Must be called on the main client thread (which is also the render thread).
     */
    public void tick() {
        if (!isActive.get() || isPaused.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // ClientTickEvent runs on the main client thread which is the render thread
        // Directly call doRender since we're already on the correct thread
        if (RenderSystem.isOnRenderThread()) {
            doRender();
        } else {
            // This shouldn't happen in normal operation, but log if it does
            AE2WebMod.LOGGER.warn("RenderScheduler.tick() called from wrong thread");
        }
    }

    private void doRender() {
        renderer.initialize();

        for (int i = 0; i < itemsPerFrame; i++) {
            RenderTask task;
            synchronized (taskQueue) {
                task = taskQueue.poll();
                if (task == null) {
                    if (queueDrained.compareAndSet(false, true)) {
                        AE2WebMod.LOGGER.info("Render queue drained - waiting for {} in-flight uploads", inFlightCount.get());
                    }

                    tryFinalizeRun(task == null ? runId.get() : task.runId());
                    return;
                }
            }

            if (task.runId() != runId.get()) {
                task.completion().complete(false);
                continue;
            }

            inFlightCount.incrementAndGet();

            try {
                // Try rendering as item first
                ItemStack stack = null;
                if (task.nbtData != null && task.nbtData.length > 0) {
                    try {
                        CompoundTag data = NbtIo.readCompressed(new ByteArrayInputStream(task.nbtData));
                        stack = ItemStack.of(data);
                    } catch (IOException e) {
                        AE2WebMod.LOGGER.warn("Failed to deserialize stackNbt for {}", task.iconKey, e);
                    } catch (Exception e) {
                        AE2WebMod.LOGGER.warn("Invalid stackNbt for {}", task.iconKey, e);
                    }
                }

                if (stack == null || stack.isEmpty()) {
                    stack = ItemIconRenderer.getItemStack(task.itemId);
                }
                if (stack != null && !stack.isEmpty()) {
                    byte[] png = renderer.renderItemToPng(stack);
                    String translatedName = stack.getHoverName().getString();
                    List<String> tooltip = buildTooltipLines(stack);
                    handleRendered(task, new RenderResult(png, translatedName, tooltip));
                } else {
                    // Try rendering as fluid
                    FluidStack fluidStack = ItemIconRenderer.getFluidStack(task.itemId);
                    if (fluidStack != null && !fluidStack.isEmpty()) {
                        byte[] png = renderer.renderFluidToPng(fluidStack);
                        String translatedName = fluidStack.getDisplayName().getString();
                        handleRendered(task, new RenderResult(png, translatedName, List.of(translatedName)));
                    } else {
                        AE2WebMod.LOGGER.warn("Failed to render {} (iconKey={}): not found as item or fluid", task.itemId, task.iconKey);
                        handleRendered(task, null);
                    }
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to render: {}", task.itemId, e);
                handleRendered(task, null);
            }
        }
    }

    private void handleRendered(RenderTask task, RenderResult result) {
        try {
            if (task.onRenderComplete() != null) {
                task.onRenderComplete().accept(result);
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Render callback failed for {}", task.itemId(), e);
        }

        if (result == null || result.png() == null) {
            completeTask(task, false);
            return;
        }

        if (task.serverUrl() == null || task.serverUrl().isBlank()) {
            completeTask(task, false);
            return;
        }

        PrerenderHttpClient.getInstance()
                .uploadIcon(task.serverUrl(), task.iconKey(), result.png(), result.translatedName(), result.tooltip())
                .handle((success, ex) -> {
                    if (ex != null) {
                        AE2WebMod.LOGGER.warn("Upload exception for {}: {}", task.iconKey(), ex.getMessage());
                        completeTask(task, false);
                    } else {
                        if (success == null || !success) {
                            AE2WebMod.LOGGER.warn("Failed to upload icon for {}", task.iconKey());
                        }
                        completeTask(task, success != null && success);
                    }
                    return null;
                });
    }

    private static List<String> buildTooltipLines(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;

            TooltipFlag flag;
            try {
                boolean advanced = mc.options.advancedItemTooltips;
                flag = advanced ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
            } catch (Exception e) {
                AE2WebMod.LOGGER.debug("Failed to read advancedItemTooltips option, using NORMAL", e);
                flag = TooltipFlag.Default.NORMAL;
            }

            return stack.getTooltipLines(player, flag).stream()
                    .map(LegacyTextFormatter::componentToLegacy)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void completeTask(RenderTask task, boolean uploadSuccess) {
        task.completion().complete(uploadSuccess);

        long currentRunId = runId.get();
        if (task.runId() != currentRunId) {
            return;
        }

        if (uploadSuccess) {
            successfulCount.incrementAndGet();
        }

        inFlightCount.decrementAndGet();
        completedCount.incrementAndGet();

        Minecraft.getInstance().execute(() -> {
            if (task.runId() != runId.get()) {
                return;
            }
            notifyProgress(task.itemId());
            tryFinalizeRun(task.runId());
        });
    }

    private void tryFinalizeRun(long expectedRunId) {
        if (expectedRunId != runId.get()) {
            return;
        }

        if (inFlightCount.get() != 0) {
            return;
        }

        synchronized (taskQueue) {
            if (expectedRunId != runId.get()) {
                return;
            }
            if (!taskQueue.isEmpty()) {
                queueDrained.set(false);
                return;
            }
            if (inFlightCount.get() != 0) {
                return;
            }

            isActive.set(false);
            queueDrained.set(false);
        }

        notifyComplete();
    }

    private void notifyProgress(ResourceLocation current) {
        if (progressListener == null) return;

        long now = System.currentTimeMillis();
        if (now - lastProgressUpdate < 200) return; // Throttle updates

        lastProgressUpdate = now;
        progressListener.accept(new RenderProgress(completedCount.get(), totalTasks, current));
    }

    private void notifyComplete() {
        if (progressListener != null) {
            progressListener.accept(new RenderProgress(completedCount.get(), totalTasks, null));
        }
        AE2WebMod.LOGGER.info("Render complete: saved {}/{} icons", successfulCount.get(), totalTasks);
    }

    public int getPendingCount() {
        synchronized (taskQueue) {
            return taskQueue.size();
        }
    }

    public int getCompletedCount() {
        return completedCount.get();
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public float getProgress() {
        if (totalTasks == 0) return 1.0f;
        return (float) completedCount.get() / totalTasks;
    }

    public boolean isActive() {
        return isActive.get();
    }

    public void pause() {
        isPaused.set(true);
    }

    public void resume() {
        isPaused.set(false);
    }

    public void cancel() {
        synchronized (taskQueue) {
            taskQueue.clear();
            runId.incrementAndGet();
            completedCount.set(0);
            successfulCount.set(0);
            inFlightCount.set(0);
            totalTasks = 0;
            queueDrained.set(false);
            isActive.set(false);
            isPaused.set(false);
        }
        AE2WebMod.LOGGER.info("Render cancelled");
    }

    public void setProgressListener(Consumer<RenderProgress> listener) {
        this.progressListener = listener;
    }

    public void setItemsPerFrame(int itemsPerFrame) {
        this.itemsPerFrame = Math.max(1, itemsPerFrame); // No upper limit - prioritize render speed
    }
}
