package com.kokoda.ae2web.web.handlers.prerender;

import com.google.gson.JsonObject;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class PrerenderClaimHandler {
    private PrerenderClaimHandler() {}

    public static void handleClaim(ChannelHandlerContext ctx, FullHttpRequest request, HttpHeaders headers) {
        String body = request.content().toString(CharsetUtil.UTF_8);
        if (body == null || body.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Request body required"), headers);
            return;
        }

        JsonObject json;
        try {
            json = GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid JSON"), headers);
            return;
        }

        if (json == null || !json.has("itemIds") || !json.has("clientId")) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Missing itemIds or clientId"), headers);
            return;
        }

        String clientId = json.get("clientId").getAsString();
        List<String> requestedIds = new ArrayList<>();
        json.get("itemIds").getAsJsonArray().forEach(e -> requestedIds.add(e.getAsString()));
        for (String requestedId : requestedIds) {
            int atIndex = requestedId != null ? requestedId.lastIndexOf('@') : -1;
            if (atIndex <= 0 || atIndex >= requestedId.length() - 1) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        errorJson("Invalid iconKey in itemIds (expected namespace:path@hash)"), headers);
                return;
            }
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        PrerenderStorageService.ClaimResult result = storage.claimItemsForRendering(requestedIds, clientId);

        JsonObject response = new JsonObject();
        response.add("claimed", GSON.toJsonTree(result.claimed()));
        response.addProperty("claimedCount", result.claimed().size());
        response.addProperty("alreadyRendered", result.alreadyRendered());
        response.addProperty("lockedBySelf", result.lockedBySelf());
        response.addProperty("lockedByOthers", result.lockedByOthers());
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
    }
}

