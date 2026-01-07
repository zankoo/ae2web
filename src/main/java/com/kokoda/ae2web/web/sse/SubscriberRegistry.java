package com.kokoda.ae2web.web.sse;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages SSE subscriber channels and terminal version tracking.
 * Thread-safe using ConcurrentHashMap for all collections.
 */
public final class SubscriberRegistry {
    private final Map<String, AtomicLong> terminalVersions = new ConcurrentHashMap<>();
    private final Map<String, Set<Channel>> terminalSubscribers = new ConcurrentHashMap<>();

    /**
     * Check if terminal has active subscribers.
     */
    public boolean hasSubscribers(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return false;
        }
        Set<Channel> subs = terminalSubscribers.get(terminalId);
        return subs != null && !subs.isEmpty();
    }

    /**
     * Get current version for terminal.
     */
    public long getVersion(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return 0L;
        }
        AtomicLong v = terminalVersions.get(terminalId);
        return v == null ? 0L : v.get();
    }

    /**
     * Increment and return new version for terminal.
     */
    public long incrementVersion(String terminalId) {
        AtomicLong version = terminalVersions.computeIfAbsent(
            terminalId, id -> new AtomicLong(0L));
        return version.incrementAndGet();
    }

    /**
     * Ensure version tracking exists for terminal.
     */
    public void ensureVersion(String terminalId) {
        terminalVersions.computeIfAbsent(terminalId, id -> new AtomicLong(0L));
    }

    /**
     * Add subscriber channel for terminal.
     */
    public void subscribe(String terminalId, Channel channel) {
        if (terminalId == null || terminalId.isBlank() || channel == null) {
            return;
        }
        terminalVersions.computeIfAbsent(terminalId, id -> new AtomicLong(0L));
        terminalSubscribers
            .computeIfAbsent(terminalId, id -> ConcurrentHashMap.newKeySet())
            .add(channel);
    }

    /**
     * Remove subscriber channel for terminal.
     * @return true if this was the last subscriber (terminal has no more subscribers)
     */
    public boolean unsubscribe(String terminalId, Channel channel) {
        if (terminalId == null || terminalId.isBlank() || channel == null) {
            return false;
        }
        Set<Channel> subs = terminalSubscribers.get(terminalId);
        if (subs == null) {
            return false;
        }
        subs.remove(channel);
        if (subs.isEmpty()) {
            terminalSubscribers.remove(terminalId, subs);
            return true;
        }
        return false;
    }

    /**
     * Get all subscribers for terminal.
     */
    public Set<Channel> getSubscribers(String terminalId) {
        return terminalSubscribers.get(terminalId);
    }

    /**
     * Remove all subscribers for terminal.
     * @return the removed subscribers (for caller to close)
     */
    public Set<Channel> unregisterTerminal(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return null;
        }
        terminalVersions.remove(terminalId);
        return terminalSubscribers.remove(terminalId);
    }

    /**
     * Clear all subscribers and versions.
     */
    public void clear() {
        terminalVersions.clear();
        terminalSubscribers.clear();
    }
}
