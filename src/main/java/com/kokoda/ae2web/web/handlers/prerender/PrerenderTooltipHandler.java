package com.kokoda.ae2web.web.handlers.prerender;

import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class PrerenderTooltipHandler {
    private PrerenderTooltipHandler() {}

    public static void handleTooltip(ChannelHandlerContext ctx,
                                     Map<String, List<String>> params,
                                     HttpHeaders headers) {
        String id = getFirstParam(params, "id");
        if (id == null || id.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing id"), headers);
            return;
        }
        int atIndex = id.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= id.length() - 1) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid id (expected iconKey: namespace:path@hash)"), headers);
            return;
        }

        ExecutorService ioExecutor = WebServer.getInstance().getIoExecutor();
        CompletableFuture
                .supplyAsync(() -> {
                    PrerenderStorageService storage = PrerenderStorageService.getInstance();
                    storage.initialize();
                    return storage.getTooltip(id);
                }, ioExecutor)
                .handle((tooltip, ex) -> {
                    ctx.executor().execute(() -> {
                        if (ex != null) {
                            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                    errorJson("Failed to load tooltip"), headers);
                            return;
                        }

                        if (tooltip == null || tooltip.isEmpty()) {
                            headers.set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store");
                            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Tooltip not found"), headers);
                            return;
                        }

                        headers.set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");
                        sendJsonResponse(ctx, HttpResponseStatus.OK,
                                GSON.toJson(Map.of("iconKey", id, "tooltip", tooltip)), headers);
                    });
                    return null;
                });
    }
}

