package com.kokoda.ae2web.web.handlers.prerender;

import com.google.gson.JsonObject;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class PrerenderUploadHandler {
    private PrerenderUploadHandler() {}

    public static void handleUpload(ChannelHandlerContext ctx, FullHttpRequest request, HttpHeaders headers) {
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

        if (json == null || !json.has("itemId") || !json.has("png")) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Missing itemId or png"), headers);
            return;
        }

        String itemId = json.get("itemId").getAsString();
        String pngBase64 = json.get("png").getAsString();
        String name = json.has("name") ? json.get("name").getAsString() : null;
        List<String> tooltip = null;
        if (json.has("tooltip") && json.get("tooltip").isJsonArray()) {
            tooltip = new ArrayList<>();
            for (var el : json.getAsJsonArray("tooltip")) {
                if (el != null && el.isJsonPrimitive()) {
                    tooltip.add(el.getAsString());
                }
            }
        }
        final String nameFinal = name;
        final List<String> tooltipFinal = tooltip;

        if (itemId == null || itemId.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid itemId"), headers);
            return;
        }
        int atIndex = itemId.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= itemId.length() - 1) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid itemId (expected iconKey: namespace:path@hash)"), headers);
            return;
        }

        byte[] pngData;
        try {
            pngData = Base64.getDecoder().decode(pngBase64);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid base64 encoding"), headers);
            return;
        }

        ExecutorService ioExecutor = WebServer.getInstance().getIoExecutor();
        CompletableFuture
                .supplyAsync(() -> {
                    PrerenderStorageService storage = PrerenderStorageService.getInstance();
                    storage.initialize();
                    return storage.saveIcon(itemId, pngData, nameFinal, tooltipFinal);
                }, ioExecutor)
                .handle((saved, ex) -> {
                    ctx.executor().execute(() -> {
                        if (ex != null) {
                            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                    errorJson("Failed to save icon"), headers);
                            return;
                        }
                        if (saved != null && saved) {
                            JsonObject response = new JsonObject();
                            response.addProperty("status", "saved");
                            response.addProperty("itemId", itemId);
                            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
                        } else {
                            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                                    errorJson("Failed to save icon"), headers);
                        }
                    });
                    return null;
                });
    }
}
