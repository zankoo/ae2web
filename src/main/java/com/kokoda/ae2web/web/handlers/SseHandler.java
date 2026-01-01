package com.kokoda.ae2web.web.handlers;

import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.web.WebServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.getFirstParam;

/**
 * Server-Sent Events endpoints (one-way server -> browser).
 */
public final class SseHandler {
    private static final AttributeKey<ScheduledFuture<?>> KEEPALIVE_TASK =
            AttributeKey.valueOf("ae2web.sse.keepalive");

    public static final AttributeKey<String> SUBSCRIBED_TERMINAL =
            AttributeKey.valueOf("ae2web.sse.terminalId");

    private SseHandler() {}

    public static void handleInventoryEvents(ChannelHandlerContext ctx,
                                             FullHttpRequest request,
                                             Map<String, List<String>> params,
                                             HttpHeaders headers) {
        if (request.method() != HttpMethod.GET) {
            sendMethodNotAllowed(ctx, headers);
            return;
        }

        String requestedTerminalId = getFirstParam(params, "terminalId");
        String terminalId = resolveTerminalId(requestedTerminalId);
        if (terminalId == null) {
            headers.set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store");
            HandlerUtils.sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HandlerUtils.errorJson("No connected terminal available"), headers);
            return;
        }

        Channel channel = ctx.channel();
        channel.attr(SUBSCRIBED_TERMINAL).set(terminalId);
        WebServer.getInstance().subscribeInventory(terminalId, channel);

        // SSE response headers
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ctx.writeAndFlush(response);

        // Initial hello + current version
        long version = WebServer.getInstance().getInventoryVersion(terminalId);
        writeSse(channel, "retry: 2000\n");
        writeHello(channel, terminalId, version);

        // Keepalive comments so proxies don't kill the connection.
        ScheduledFuture<?> keepalive = ctx.executor().scheduleAtFixedRate(
                () -> writeSse(channel, ":keepalive\n\n"),
                15, 15, TimeUnit.SECONDS
        );
        channel.attr(KEEPALIVE_TASK).set(keepalive);

        channel.closeFuture().addListener(f -> cleanupSubscription(channel));
    }

    private static void cleanupSubscription(Channel channel) {
        String terminalId = channel.attr(SUBSCRIBED_TERMINAL).getAndSet(null);
        if (terminalId != null) {
            WebServer.getInstance().unsubscribeInventory(terminalId, channel);
        }

        ScheduledFuture<?> keepalive = channel.attr(KEEPALIVE_TASK).getAndSet(null);
        if (keepalive != null) {
            keepalive.cancel(false);
        }
    }

    private static String resolveTerminalId(String requestedTerminalId) {
        if (requestedTerminalId != null && !requestedTerminalId.isBlank()) {
            WebTerminalBlockEntity t = WebServer.getInstance().getTerminal(requestedTerminalId);
            if (t != null && t.isConnectedToGrid()) {
                return requestedTerminalId;
            }
            return null;
        }

        WebTerminalBlockEntity first = WebServer.getInstance().getFirstTerminal();
        if (first != null && first.isConnectedToGrid()) {
            return first.getTerminalId();
        }
        return null;
    }

    private static void sendMethodNotAllowed(ChannelHandlerContext ctx, HttpHeaders headers) {
        HandlerUtils.sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                HandlerUtils.errorJson("Method not allowed"), headers);
    }

    private static void writeHello(Channel channel, String terminalId, long version) {
        JsonObject data = new JsonObject();
        data.addProperty("terminalId", terminalId);
        data.addProperty("version", version);
        writeEvent(channel, "hello", data.toString());
    }

    public static void writeInventoryEvent(Channel channel, String terminalId, long version) {
        if (channel == null || !channel.isActive()) {
            return;
        }

        String subscribed = channel.attr(SUBSCRIBED_TERMINAL).get();
        if (subscribed == null || !subscribed.equals(terminalId)) {
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("terminalId", terminalId);
        data.addProperty("version", version);
        writeEvent(channel, "inventory", data.toString());
    }

    private static void writeEvent(Channel channel, String eventName, String data) {
        if (data == null) {
            data = "";
        }
        String payload = "event: " + eventName + "\n" +
                         "data: " + data + "\n\n";
        writeSse(channel, payload);
    }

    private static void writeSse(Channel channel, String payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if (channel == null || !channel.isActive()) {
            return;
        }

        try {
            channel.writeAndFlush(new DefaultHttpContent(Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8)));
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("SSE write failed", e);
        }
    }
}

