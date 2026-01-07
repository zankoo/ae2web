package com.kokoda.ae2web.web.sse;

import com.kokoda.ae2web.web.dto.InventoryItemDTO;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Holds the inventory state for a single terminal's SSE subscribers.
 * Contains the cached items, pending deltas, and flush scheduling.
 */
final class TerminalInventoryState {
    final Object lock = new Object();
    final Map<String, InventoryItemDTO> items = new HashMap<>();
    final Set<String> craftableUniqueIds = new HashSet<>();
    final Map<String, PendingInventoryDelta> pending = new HashMap<>();
    ScheduledFuture<?> flushFuture;
    boolean initialized = false;
}
