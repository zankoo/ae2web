package com.kokoda.ae2web.web.sse;

import com.kokoda.ae2web.web.dto.InventoryItemDTO;

import java.util.List;

/**
 * Result of processing pending inventory deltas.
 */
record DeltaFlushResult(
        List<InventoryItemDTO> added,
        List<InventoryPatch> updated,
        List<String> removed
) {
    boolean isEmpty() {
        return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
    }
}
