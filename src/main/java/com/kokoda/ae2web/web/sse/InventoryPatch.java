package com.kokoda.ae2web.web.sse;

/**
 * Represents a finalized inventory patch to be sent over SSE.
 * Immutable data class containing the item change details.
 */
final class InventoryPatch {
    private final String uniqueId;
    private final long amount;
    private final boolean craftable;
    private final String name;

    InventoryPatch(String uniqueId, long amount, boolean craftable, String name) {
        this.uniqueId = uniqueId;
        this.amount = amount;
        this.craftable = craftable;
        this.name = name;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public long getAmount() {
        return amount;
    }

    public boolean isCraftable() {
        return craftable;
    }

    public String getName() {
        return name;
    }
}
