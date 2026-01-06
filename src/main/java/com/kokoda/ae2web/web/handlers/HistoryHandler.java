package com.kokoda.ae2web.web.handlers;

import com.kokoda.ae2web.web.history.ProductionHistoryService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

/**
 * Handler for production history API endpoints.
 */
public final class HistoryHandler {
    private HistoryHandler() {}

    /**
     * Handle GET /api/history
     * Query params:
     *   - terminalId: required
     *   - uniqueId: required (comma-separated for batch)
     *   - from: optional, timestamp in ms to filter from
     */
    public static void handleHistory(ChannelHandlerContext ctx,
                                     FullHttpRequest request,
                                     Map<String, List<String>> params,
                                     HttpHeaders headers) {
        UUID userUuid = getAuthenticatedUserUuid(ctx);
        if (userUuid == null) {
            sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED, errorJson("Unauthorized"), headers);
            return;
        }

        if (request.method() != HttpMethod.GET) {
            sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, errorJson("Method not allowed"), headers);
            return;
        }

        String terminalId = getFirstParam(params, "terminalId");
        if (terminalId == null || terminalId.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing terminalId"), headers);
            return;
        }

        String uniqueIdParam = getFirstParam(params, "uniqueId");
        if (uniqueIdParam == null || uniqueIdParam.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing uniqueId"), headers);
            return;
        }

        long fromTimestamp;
        try {
            fromTimestamp = parseLongParam(params, "from", 0L, 0L, Long.MAX_VALUE);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Invalid from"), headers);
            return;
        }

        LinkedHashSet<String> uniqueIdSet = new LinkedHashSet<>();
        for (String raw : uniqueIdParam.split(",")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                uniqueIdSet.add(trimmed);
            }
        }
        if (uniqueIdSet.isEmpty()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing uniqueId"), headers);
            return;
        }
        if (uniqueIdSet.size() > 100) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Too many uniqueId values"), headers);
            return;
        }

        List<String> uniqueIds = List.copyOf(uniqueIdSet);
        long finalFromTimestamp = fromTimestamp;

        runOnIoThread(ctx, headers, () -> {
            ProductionHistoryService service = ProductionHistoryService.getInstance();
            List<ProductionHistoryService.HistoryResponse> responses =
                    service.getHistoryBatch(terminalId, uniqueIds, finalFromTimestamp);
            if (uniqueIds.size() == 1) {
                sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(responses.get(0)), headers);
                return;
            }
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(responses), headers);
        });
    }
}
