package com.kokoda.ae2web.web.sse;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.dto.InventoryItemDTO;
import com.kokoda.ae2web.web.inventory.InventoryNameUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds inventory snapshots from ME network data.
 */
public final class InventorySnapshotBuilder {

    private InventorySnapshotBuilder() {}

    /**
     * Build a complete inventory snapshot for a terminal.
     */
    public static Map<String, InventoryItemDTO> buildSnapshot(
            WebTerminalBlockEntity terminal,
            PrerenderStorageService storage) {

        Map<String, AEKey> craftableKeyByUniqueId = buildCraftableKeyMap(terminal);
        Map<String, InventoryItemDTO> snapshot = buildFromNetwork(terminal, craftableKeyByUniqueId, storage);
        addCraftableOnlyItems(snapshot, craftableKeyByUniqueId, storage);
        return snapshot;
    }

    /**
     * Build map of craftable keys by uniqueId.
     */
    public static Map<String, AEKey> buildCraftableKeyMap(WebTerminalBlockEntity terminal) {
        Map<String, AEKey> craftableKeyByUniqueId = new HashMap<>();
        ICraftingService craftingService = terminal.getCraftingService();
        if (craftingService == null) {
            return craftableKeyByUniqueId;
        }

        Set<AEKey> craftables = craftingService.getCraftables(
            key -> key instanceof AEItemKey || key instanceof AEFluidKey);
        for (AEKey craftable : craftables) {
            String craftableUniqueId = InventoryDeltaProcessor.buildUniqueId(craftable);
            if (craftableUniqueId != null) {
                craftableKeyByUniqueId.put(craftableUniqueId, craftable);
            }
        }
        return craftableKeyByUniqueId;
    }

    /**
     * Apply buffered deltas to a snapshot.
     */
    public static void applyBufferedDeltas(
            Map<String, InventoryItemDTO> snapshot,
            Map<String, AEKey> craftableKeyByUniqueId,
            Map<String, PendingInventoryDelta> buffered,
            PrerenderStorageService storage) {

        for (var entry : buffered.entrySet()) {
            String uniqueId = entry.getKey();
            PendingInventoryDelta delta = entry.getValue();
            if (uniqueId == null || delta == null || delta.key == null) {
                continue;
            }

            updateCraftableStatus(craftableKeyByUniqueId, uniqueId, delta);
            applyDeltaToSnapshot(snapshot, craftableKeyByUniqueId, uniqueId, delta, storage);
        }
    }

    /**
     * Build an InventoryItemDTO from an AEKey.
     */
    public static InventoryItemDTO buildInventoryItem(
            AEKey key, long amount, boolean craftable, PrerenderStorageService storage) {
        if (key == null) {
            return null;
        }
        if (!(key instanceof AEItemKey) && !(key instanceof AEFluidKey)) {
            return null;
        }

        String iconKey = IconKeyUtils.iconKey(key);
        String type = (key instanceof AEFluidKey) ? "fluid" : "item";
        String uniqueId = type + ":" + iconKey;
        String id = key.getId().toString();
        String namespace = key.getId().getNamespace();

        String translationKey;
        String name;
        List<String> tags = null;
        if (key instanceof AEItemKey itemKey) {
            translationKey = itemKey.getItem().getDescriptionId();
            name = InventoryNameUtils.getTranslatedName(itemKey, storage);
            tags = itemKey.toStack().getTags()
                    .map(tagKey -> tagKey.location().toString())
                    .sorted()
                    .toList();
        } else {
            AEFluidKey fluidKey = (AEFluidKey) key;
            translationKey = fluidKey.getFluid().getFluidType().getDescriptionId();
            name = InventoryNameUtils.getFluidName(fluidKey, storage);
        }

        return new InventoryItemDTO(type, id, uniqueId, iconKey, tags, name,
            translationKey, amount, namespace, craftable);
    }

    // Private helper methods

    private static Map<String, InventoryItemDTO> buildFromNetwork(
            WebTerminalBlockEntity terminal,
            Map<String, AEKey> craftableKeyByUniqueId,
            PrerenderStorageService storage) {

        Map<AEKey, Long> inventory = terminal.getNetworkInventory(WebTerminalBlockEntity.KeyFilter.ALL);
        Map<String, InventoryItemDTO> snapshot = new HashMap<>();

        for (var entry : inventory.entrySet()) {
            AEKey key = entry.getKey();
            Long amount = entry.getValue();
            if (key == null || amount == null || amount <= 0) {
                continue;
            }

            String uniqueId = InventoryDeltaProcessor.buildUniqueId(key);
            if (uniqueId == null) {
                continue;
            }

            boolean craftable = craftableKeyByUniqueId.containsKey(uniqueId);
            InventoryItemDTO dto = buildInventoryItem(key, amount, craftable, storage);
            if (dto != null) {
                snapshot.put(uniqueId, dto);
            }
        }
        return snapshot;
    }

    private static void addCraftableOnlyItems(
            Map<String, InventoryItemDTO> snapshot,
            Map<String, AEKey> craftableKeyByUniqueId,
            PrerenderStorageService storage) {

        for (var entry : craftableKeyByUniqueId.entrySet()) {
            String craftableUniqueId = entry.getKey();
            if (snapshot.containsKey(craftableUniqueId)) {
                continue;
            }

            InventoryItemDTO dto = buildInventoryItem(entry.getValue(), 0L, true, storage);
            if (dto != null) {
                snapshot.put(craftableUniqueId, dto);
            }
        }
    }

    private static void updateCraftableStatus(
            Map<String, AEKey> craftableKeyByUniqueId,
            String uniqueId, PendingInventoryDelta delta) {
        if (delta.craftable != null) {
            if (delta.craftable) {
                craftableKeyByUniqueId.put(uniqueId, delta.key);
            } else {
                craftableKeyByUniqueId.remove(uniqueId);
            }
        }
    }

    private static void applyDeltaToSnapshot(
            Map<String, InventoryItemDTO> snapshot,
            Map<String, AEKey> craftableKeyByUniqueId,
            String uniqueId, PendingInventoryDelta delta,
            PrerenderStorageService storage) {

        boolean craftable = craftableKeyByUniqueId.containsKey(uniqueId);
        InventoryItemDTO existing = snapshot.get(uniqueId);
        long baseAmount = existing != null ? existing.getAmount() : 0L;
        long nextAmount = delta.amount != null ? delta.amount : baseAmount;

        if (nextAmount <= 0 && !craftable) {
            snapshot.remove(uniqueId);
            return;
        }

        if (existing == null) {
            InventoryItemDTO created = buildInventoryItem(delta.key, nextAmount, craftable, storage);
            if (created != null) {
                if (delta.name != null && !Objects.equals(created.getName(), delta.name)) {
                    created = created.withName(delta.name);
                }
                snapshot.put(uniqueId, created);
            }
        } else {
            InventoryItemDTO updated = existing.withAmountAndCraftable(nextAmount, craftable);
            if (delta.name != null && !Objects.equals(existing.getName(), delta.name)) {
                updated = updated.withName(delta.name);
            }
            snapshot.put(uniqueId, updated);
        }
    }
}
