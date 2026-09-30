package com.kokoda.ae2web.web.sse;

import appeng.api.stacks.AEKey;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.web.dto.InventoryItemDTO;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import com.kokoda.ae2web.web.terminal.TerminalRegistry;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Facade service coordinating SSE inventory subscriptions and delta broadcasting.
 * Delegates to specialized components for each responsibility.
 */
public final class InventorySseService {
    private static final InventorySseService instance = new InventorySseService();

    private final SubscriberRegistry subscriberRegistry;
    private final InventoryStateManager stateManager;
    private final InventoryDeltaProcessor deltaProcessor;
    private final InventoryEventBroadcaster broadcaster;

    private InventorySseService() {
        this.subscriberRegistry = new SubscriberRegistry();
        this.stateManager = new InventoryStateManager();
        this.deltaProcessor = new InventoryDeltaProcessor(stateManager);
        this.broadcaster = new InventoryEventBroadcaster(subscriberRegistry);
    }

    public static InventorySseService getInstance() {
        return instance;
    }

    public void setExecutor(ScheduledExecutorService executor) {
        deltaProcessor.setExecutor(executor);
    }

    // === Subscriber Management ===

    public boolean hasSubscribers(String terminalId) {
        return subscriberRegistry.hasSubscribers(terminalId);
    }

    public long getVersion(String terminalId) {
        return subscriberRegistry.getVersion(terminalId);
    }

    public void subscribe(String terminalId, Channel channel) {
        subscriberRegistry.subscribe(terminalId, channel);
        stateManager.getOrCreate(terminalId);
    }

    public void unsubscribe(String terminalId, Channel channel) {
        boolean wasLast = subscriberRegistry.unsubscribe(terminalId, channel);
        if (wasLast) {
            stateManager.resetState(terminalId);
        }
    }

    public void unregisterTerminal(String terminalId) {
        Set<Channel> subs = subscriberRegistry.unregisterTerminal(terminalId);
        if (subs != null) {
            for (Channel ch : subs) {
                ch.eventLoop().execute(() -> {
                    if (ch.isActive()) {
                        ch.close();
                    }
                });
            }
        }
        stateManager.removeState(terminalId);
    }

    // === Delta Processing ===

    public void queueDelta(String terminalId, AEKey key, Long amount, Boolean craftable) {
        deltaProcessor.queueDelta(terminalId, key, amount, craftable, this::flushInventoryDeltas);
    }

    public void queueNameRefresh(String iconKey, String translatedName) {
        deltaProcessor.queueNameRefresh(iconKey, translatedName, this::flushInventoryDeltas);
    }

    public void notifyChanged(String terminalId) {
        long newVersion = subscriberRegistry.incrementVersion(terminalId);
        broadcaster.notifyVersionChange(terminalId, newVersion);
    }

    // === Snapshot ===

    public void sendSnapshot(Channel channel, String terminalId) {
        if (channel == null || !channel.isActive() ||
            terminalId == null || terminalId.isBlank()) {
            return;
        }

        WebTerminalBlockEntity terminal = TerminalRegistry.getInstance().get(terminalId);
        if (terminal == null || !terminal.isConnectedToGrid()) {
            return;
        }

        TerminalInventoryState state = stateManager.getOrCreate(terminalId);
        ensureInitialized(terminalId, terminal, state);

        long version = subscriberRegistry.getVersion(terminalId);
        List<InventoryItemDTO> items;
        synchronized (state.lock) {
            items = new ArrayList<>(state.items.values());
        }

        broadcaster.sendSnapshot(channel, terminalId, version, items);
    }

    // === Lifecycle ===

    public void clear() {
        subscriberRegistry.clear();
        stateManager.clear();
    }

    // === Private ===

    private void ensureInitialized(String terminalId, WebTerminalBlockEntity terminal,
                                    TerminalInventoryState state) {
        synchronized (state.lock) {
            if (state.initialized) {
                return;
            }
            // Deltas queued before now are older than the snapshot built below; applying them on top of it
            // would roll amounts back. Only deltas queued while the snapshot is being built are kept.
            state.pending.clear();
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        Map<String, AEKey> craftableKeyByUniqueId = InventorySnapshotBuilder.buildCraftableKeyMap(terminal);
        Map<String, InventoryItemDTO> next = InventorySnapshotBuilder.buildSnapshot(terminal, storage);

        Map<String, PendingInventoryDelta> buffered;
        synchronized (state.lock) {
            buffered = new HashMap<>(state.pending);
            state.pending.clear();
        }

        if (!buffered.isEmpty()) {
            InventorySnapshotBuilder.applyBufferedDeltas(next, craftableKeyByUniqueId, buffered, storage);
        }

        synchronized (state.lock) {
            state.items.clear();
            state.items.putAll(next);
            state.craftableUniqueIds.clear();
            state.craftableUniqueIds.addAll(craftableKeyByUniqueId.keySet());
            state.initialized = true;
            state.flushFuture = null;
        }

        storage.markSeen(next.values().stream().map(InventoryItemDTO::getIconKey).toList());
    }

    private void flushInventoryDeltas(String terminalId) {
        Map<String, PendingInventoryDelta> pending = deltaProcessor.extractPendingDeltas(terminalId);
        if (pending == null) {
            return;
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        TerminalInventoryState state = stateManager.get(terminalId);
        if (state == null) {
            return;
        }

        DeltaFlushResult result = processPendingDeltas(state, pending, storage);
        if (result.isEmpty()) {
            return;
        }

        long newVersion = subscriberRegistry.incrementVersion(terminalId);
        broadcaster.recordHistory(terminalId, result);
        markAddedItemsSeen(result.added(), storage);
        broadcaster.broadcastDelta(terminalId, newVersion, result);
    }

    private static DeltaFlushResult processPendingDeltas(
            TerminalInventoryState state,
            Map<String, PendingInventoryDelta> pending,
            PrerenderStorageService storage) {

        List<InventoryItemDTO> added = new ArrayList<>();
        List<InventoryPatch> updated = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        synchronized (state.lock) {
            for (var entry : pending.entrySet()) {
                String uniqueId = entry.getKey();
                PendingInventoryDelta delta = entry.getValue();
                if (delta == null || uniqueId == null) {
                    continue;
                }

                processSingleDelta(state, uniqueId, delta, storage, added, updated, removed);
            }
        }

        return new DeltaFlushResult(added, updated, removed);
    }

    private static void processSingleDelta(
            TerminalInventoryState state, String uniqueId,
            PendingInventoryDelta delta, PrerenderStorageService storage,
            List<InventoryItemDTO> added, List<InventoryPatch> updated,
            List<String> removed) {

        // Update craftable set
        if (delta.craftable != null) {
            if (delta.craftable) {
                state.craftableUniqueIds.add(uniqueId);
            } else {
                state.craftableUniqueIds.remove(uniqueId);
            }
        }

        InventoryItemDTO before = state.items.get(uniqueId);

        // Compute next values
        long beforeAmount = before != null ? before.getAmount() : 0L;
        boolean beforeCraftable = before != null && before.isCraftable();
        String beforeName = before != null ? before.getName() : null;

        long nextAmount = delta.amount != null ? delta.amount : beforeAmount;
        boolean nextCraftable = delta.craftable != null ? delta.craftable : beforeCraftable;
        String nextName = delta.name != null ? delta.name : beforeName;

        // Handle removal
        if (nextAmount <= 0 && !nextCraftable) {
            if (before != null) {
                state.items.remove(uniqueId);
                removed.add(uniqueId);
            }
            return;
        }

        // Handle new item
        if (before == null) {
            if (delta.key == null) {
                return;
            }
            InventoryItemDTO created = InventorySnapshotBuilder.buildInventoryItem(
                delta.key, nextAmount, nextCraftable, storage);
            if (created != null) {
                if (nextName != null && !Objects.equals(created.getName(), nextName)) {
                    created = created.withName(nextName);
                }
                state.items.put(uniqueId, created);
                added.add(created);
            }
            return;
        }

        // Handle existing item update
        boolean amountChanged = before.getAmount() != nextAmount;
        boolean craftableChanged = before.isCraftable() != nextCraftable;
        boolean nameChanged = !Objects.equals(before.getName(), nextName);

        if (!amountChanged && !craftableChanged && !nameChanged) {
            return;
        }

        InventoryItemDTO after = before;
        if (amountChanged || craftableChanged) {
            after = after.withAmountAndCraftable(nextAmount, nextCraftable);
        }
        if (nameChanged && nextName != null) {
            after = after.withName(nextName);
        }
        state.items.put(uniqueId, after);
        updated.add(new InventoryPatch(uniqueId, nextAmount, nextCraftable,
            nameChanged ? nextName : null));
    }

    private static void markAddedItemsSeen(List<InventoryItemDTO> added,
                                            PrerenderStorageService storage) {
        if (!added.isEmpty()) {
            storage.markSeen(added.stream().map(InventoryItemDTO::getIconKey).toList());
        }
    }
}
