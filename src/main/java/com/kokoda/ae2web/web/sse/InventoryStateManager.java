package com.kokoda.ae2web.web.sse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages TerminalInventoryState lifecycle for each terminal.
 * Handles state creation, retrieval, reset, and cleanup.
 */
public final class InventoryStateManager {
    private final Map<String, TerminalInventoryState> terminalStates = new ConcurrentHashMap<>();

    /**
     * Get or create state for terminal.
     */
    public TerminalInventoryState getOrCreate(String terminalId) {
        return terminalStates.computeIfAbsent(terminalId, id -> new TerminalInventoryState());
    }

    /**
     * Get state for terminal if exists.
     */
    public TerminalInventoryState get(String terminalId) {
        return terminalStates.get(terminalId);
    }

    /**
     * Check if state exists and is initialized.
     */
    public boolean isInitialized(String terminalId) {
        TerminalInventoryState state = terminalStates.get(terminalId);
        if (state == null) {
            return false;
        }
        synchronized (state.lock) {
            return state.initialized;
        }
    }

    /**
     * Reset state without removing it.
     */
    public void resetState(String terminalId) {
        TerminalInventoryState state = terminalStates.get(terminalId);
        if (state != null) {
            resetStateLocked(state);
        }
    }

    /**
     * Cancel scheduled tasks and remove state entirely.
     */
    public void removeState(String terminalId) {
        TerminalInventoryState state = terminalStates.remove(terminalId);
        if (state != null) {
            synchronized (state.lock) {
                if (state.flushFuture != null) {
                    state.flushFuture.cancel(false);
                }
            }
        }
    }

    /**
     * Clear all terminal states.
     */
    public void clear() {
        for (TerminalInventoryState state : terminalStates.values()) {
            resetStateLocked(state);
        }
        terminalStates.clear();
    }

    /**
     * Get all terminal states (for iteration).
     */
    public Iterable<Map.Entry<String, TerminalInventoryState>> entries() {
        return terminalStates.entrySet();
    }

    private static void resetStateLocked(TerminalInventoryState state) {
        synchronized (state.lock) {
            state.items.clear();
            state.craftableUniqueIds.clear();
            state.pending.clear();
            if (state.flushFuture != null) {
                state.flushFuture.cancel(false);
            }
            state.flushFuture = null;
            state.initialized = false;
        }
    }
}
