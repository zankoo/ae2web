package com.kokoda.ae2web.web.sse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.web.dto.InventoryItemDTO;
import com.kokoda.ae2web.web.handlers.SseHandler;
import com.kokoda.ae2web.web.history.ProductionHistoryService;
import io.netty.channel.Channel;

import java.util.List;
import java.util.Set;

/**
 * Broadcasts inventory events to SSE subscribers and records history.
 */
public final class InventoryEventBroadcaster {
    private static final Gson SSE_GSON = new Gson();

    private final SubscriberRegistry subscriberRegistry;

    public InventoryEventBroadcaster(SubscriberRegistry subscriberRegistry) {
        this.subscriberRegistry = subscriberRegistry;
    }

    /**
     * Broadcast a delta update to all subscribers of a terminal.
     */
    public void broadcastDelta(String terminalId, long version, DeltaFlushResult result) {
        String payload = buildDeltaPayload(terminalId, version, result);
        broadcastPayload(terminalId, payload);
    }

    /**
     * Record delta history for production tracking.
     */
    public void recordHistory(String terminalId, DeltaFlushResult result) {
        long now = System.currentTimeMillis();
        ProductionHistoryService historyService = ProductionHistoryService.getInstance();

        for (InventoryItemDTO dto : result.added()) {
            historyService.recordDataPoint(terminalId, dto.getUniqueId(), dto.getAmount(), now);
        }
        for (InventoryPatch patch : result.updated()) {
            historyService.recordDataPoint(terminalId, patch.getUniqueId(), patch.getAmount(), now);
        }
        for (String uniqueId : result.removed()) {
            historyService.recordDataPoint(terminalId, uniqueId, 0L, now);
        }
    }

    /**
     * Notify subscribers of a version change (legacy notification).
     */
    public void notifyVersionChange(String terminalId, long newVersion) {
        Set<Channel> subs = subscriberRegistry.getSubscribers(terminalId);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (Channel channel : subs) {
            if (!channel.isActive()) {
                continue;
            }
            channel.eventLoop().execute(() -> {
                if (!channel.isActive()) {
                    return;
                }
                if (channel.attr(SseHandler.SUBSCRIBED_TERMINAL).get() == null) {
                    return;
                }
                SseHandler.writeInventoryEvent(channel, terminalId, newVersion);
            });
        }
    }

    /**
     * Send initial snapshot to a newly connected channel.
     */
    public void sendSnapshot(Channel channel, String terminalId, long version,
                             List<InventoryItemDTO> items) {
        SseHandler.writeHelloEvent(channel, terminalId, version);
        SseHandler.writeInventorySnapshotEvent(channel, terminalId, version, items);
    }

    // Private helpers

    private void broadcastPayload(String terminalId, String payload) {
        Set<Channel> subs = subscriberRegistry.getSubscribers(terminalId);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (Channel channel : subs) {
            if (!channel.isActive()) {
                continue;
            }
            channel.eventLoop().execute(() -> {
                if (!channel.isActive()) {
                    return;
                }
                if (channel.attr(SseHandler.SUBSCRIBED_TERMINAL).get() == null) {
                    return;
                }
                SseHandler.writeInventoryDeltaEvent(channel, terminalId, payload);
            });
        }
    }

    private static String buildDeltaPayload(String terminalId, long version, DeltaFlushResult result) {
        JsonObject payload = new JsonObject();
        payload.addProperty("terminalId", terminalId);
        payload.addProperty("version", version);

        if (!result.added().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (InventoryItemDTO dto : result.added()) {
                arr.add(SSE_GSON.toJsonTree(dto));
            }
            payload.add("added", arr);
        }
        if (!result.updated().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (InventoryPatch patch : result.updated()) {
                arr.add(SSE_GSON.toJsonTree(patch));
            }
            payload.add("updated", arr);
        }
        if (!result.removed().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String id : result.removed()) {
                arr.add(id);
            }
            payload.add("removed", arr);
        }

        return payload.toString();
    }
}
