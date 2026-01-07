package com.kokoda.ae2web.web.sse;

import appeng.api.stacks.AEKey;

/**
 * Represents a pending inventory change that will be merged before sending.
 * Accumulates multiple updates for the same key into a single delta.
 */
final class PendingInventoryDelta {
    AEKey key;
    Long amount;
    Boolean craftable;
    String name;

    PendingInventoryDelta(AEKey key) {
        this.key = key;
    }

    void merge(AEKey nextKey, Long nextAmount, Boolean nextCraftable, String nextName) {
        if (nextKey != null) {
            this.key = nextKey;
        }
        if (nextAmount != null) {
            this.amount = nextAmount;
        }
        if (nextCraftable != null) {
            this.craftable = nextCraftable;
        }
        if (nextName != null) {
            this.name = nextName;
        }
    }
}
