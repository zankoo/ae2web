package com.kokoda.ae2web.client.render;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.network.PrerenderHttpClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Schedules item rendering across multiple frames to avoid lag.
 * Processes N items per frame based on configuration.
 */
@OnlyIn(Dist.CLIENT)
public class RenderScheduler {
    private static final int DEFAULT_ITEMS_PER_FRAME = 3;

    private static RenderScheduler instance;

    private final ArrayDeque<RenderTask> taskQueue = new ArrayDeque<>();
    private final ItemIconRenderer renderer;
    private int itemsPerFrame = DEFAULT_ITEMS_PER_FRAME;
    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private int totalTasks = 0;
    private Consumer<RenderProgress> progressListener;
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

    private record RenderTask(ResourceLocation itemId, Consumer<byte[]> onComplete) {}

    /**
     * Submit a batch of items for rendering.
     */
    public void submitBatch(List<ResourceLocation> itemIds, String serverUrl) {
        if (itemIds.isEmpty()) return;

        synchronized (taskQueue) {
            for (ResourceLocation itemId : itemIds) {
                taskQueue.add(new RenderTask(itemId, pngBytes -> {
                    if (pngBytes != null) {
                        PrerenderHttpClient.getInstance().uploadIcon(serverUrl, itemId.toString(), pngBytes);
                    }
                }));
            }
            totalTasks = taskQueue.size();
            completedCount.set(0);
            isActive.set(true);
        }

        AE2WebMod.LOGGER.info("Submitted {} items for rendering", itemIds.size());
    }

    /**
     * Submit a single item for rendering.
     */
    public void submitTask(ResourceLocation itemId, Consumer<byte[]> onComplete) {
        synchronized (taskQueue) {
            taskQueue.add(new RenderTask(itemId, onComplete));
            totalTasks = taskQueue.size() + completedCount.get();
            isActive.set(true);
        }
    }

    /**
     * Called each frame from client tick event.
     */
    public void tick() {
        if (!isActive.get() || isPaused.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Ensure we're on render thread
        if (!RenderSystem.isOnRenderThread()) {
            mc.execute(this::doRender);
        } else {
            doRender();
        }
    }

    private void doRender() {
        renderer.initialize();

        for (int i = 0; i < itemsPerFrame; i++) {
            RenderTask task;
            synchronized (taskQueue) {
                task = taskQueue.poll();
                if (task == null) {
                    isActive.set(false);
                    notifyComplete();
                    return;
                }
            }

            try {
                ItemStack stack = ItemIconRenderer.getItemStack(task.itemId);
                if (stack != null && !stack.isEmpty()) {
                    byte[] png = renderer.renderItemToPng(stack);
                    task.onComplete.accept(png);
                } else {
                    task.onComplete.accept(null);
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to render item: {}", task.itemId, e);
                task.onComplete.accept(null);
            }

            completedCount.incrementAndGet();
            notifyProgress(task.itemId);
        }
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
        AE2WebMod.LOGGER.info("Render complete: {} items", completedCount.get());
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
            isActive.set(false);
            isPaused.set(false);
        }
        AE2WebMod.LOGGER.info("Render cancelled");
    }

    public void setProgressListener(Consumer<RenderProgress> listener) {
        this.progressListener = listener;
    }

    public void setItemsPerFrame(int itemsPerFrame) {
        this.itemsPerFrame = Math.max(1, Math.min(20, itemsPerFrame));
    }
}
