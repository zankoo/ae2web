package com.kokoda.ae2web.web.sse;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.inventory.InventoryNameUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Processes inventory delta changes, handles batching and flush scheduling.
 */
public final class InventoryDeltaProcessor {
    private static final long DELTA_FLUSH_DELAY_MS = 200L;

    private final InventoryStateManager stateManager;
    private volatile ScheduledExecutorService executor;

    public InventoryDeltaProcessor(InventoryStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setExecutor(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Queue a delta for the terminal. Schedules flush if state is initialized.
     */
    public void queueDelta(String terminalId, AEKey key, Long amount, Boolean craftable,
                           Consumer<String> onFlushScheduled) {
        if (terminalId == null || terminalId.isBlank() || key == null) {
            return;
        }
        TerminalInventoryState state = stateManager.getOrCreate(terminalId);
        String uniqueId = buildUniqueId(key);
        if (uniqueId == null) {
            return;
        }

        synchronized (state.lock) {
            PendingInventoryDelta pending = state.pending.computeIfAbsent(
                uniqueId, id -> new PendingInventoryDelta(key));
            pending.merge(key, amount, craftable, null);

            if (!state.initialized) {
                return;
            }

            scheduleDeltaFlushLocked(terminalId, state, onFlushScheduled);
        }
    }

    /**
     * Queue a name refresh for an icon key across all terminals.
     */
    public void queueNameRefresh(String iconKey, String translatedName,
                                  Consumer<String> onFlushScheduled) {
        if (iconKey == null || iconKey.isBlank() ||
            translatedName == null || translatedName.isBlank()) {
            return;
        }

        String sanitizedName = InventoryNameUtils.stripMinecraftFormatting(translatedName);
        if (sanitizedName.isBlank()) {
            return;
        }

        String itemUniqueId = "item:" + iconKey;
        String fluidUniqueId = "fluid:" + iconKey;

        for (var entry : stateManager.entries()) {
            queueNameRefreshForTerminal(entry.getKey(), entry.getValue(),
                itemUniqueId, fluidUniqueId, sanitizedName, onFlushScheduled);
        }
    }

    /**
     * Extract pending deltas from state for flushing.
     * Returns null if state is not initialized or has no pending deltas.
     */
    public Map<String, PendingInventoryDelta> extractPendingDeltas(String terminalId) {
        TerminalInventoryState state = stateManager.get(terminalId);
        if (state == null) {
            return null;
        }

        synchronized (state.lock) {
            if (!state.initialized) {
                return null;
            }
            if (state.pending.isEmpty()) {
                state.flushFuture = null;
                return null;
            }
            Map<String, PendingInventoryDelta> pending = new HashMap<>(state.pending);
            state.pending.clear();
            state.flushFuture = null;
            return pending;
        }
    }

    /**
     * Build unique ID for an AE key.
     */
    public static String buildUniqueId(AEKey key) {
        if (key instanceof AEItemKey || key instanceof AEFluidKey) {
            String iconKey = IconKeyUtils.iconKey(key);
            String type = (key instanceof AEFluidKey) ? "fluid" : "item";
            return type + ":" + iconKey;
        }
        return null;
    }

    // Private helper methods

    private void queueNameRefreshForTerminal(String terminalId, TerminalInventoryState state,
                                              String itemUniqueId, String fluidUniqueId,
                                              String sanitizedName, Consumer<String> onFlushScheduled) {
        synchronized (state.lock) {
            if (!state.initialized || state.items.isEmpty()) {
                return;
            }

            boolean queued = false;
            queued |= queueNameIfChanged(state, itemUniqueId, sanitizedName);
            queued |= queueNameIfChanged(state, fluidUniqueId, sanitizedName);

            if (queued) {
                scheduleDeltaFlushLocked(terminalId, state, onFlushScheduled);
            }
        }
    }

    private static boolean queueNameIfChanged(TerminalInventoryState state,
                                               String uniqueId, String newName) {
        var item = state.items.get(uniqueId);
        if (item != null && !Objects.equals(item.getName(), newName)) {
            PendingInventoryDelta pending = state.pending.computeIfAbsent(
                uniqueId, id -> new PendingInventoryDelta(null));
            pending.merge(null, null, null, newName);
            return true;
        }
        return false;
    }

    private void scheduleDeltaFlushLocked(String terminalId, TerminalInventoryState state,
                                           Consumer<String> onFlushScheduled) {
        if (state.flushFuture != null && !state.flushFuture.isDone()) {
            return;
        }

        ScheduledExecutorService exec = this.executor;
        if (exec == null) {
            return;
        }

        state.flushFuture = exec.schedule(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> onFlushScheduled.accept(terminalId));
            }
        }, DELTA_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}
