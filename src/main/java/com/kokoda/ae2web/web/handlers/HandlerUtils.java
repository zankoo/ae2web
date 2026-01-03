package com.kokoda.ae2web.web.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.web.WebServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.AttributeKey;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared utility methods for HTTP handlers.
 */
public final class HandlerUtils {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final AttributeKey<UUID> AUTHENTICATED_USER_UUID =
            AttributeKey.valueOf("ae2web.userUuid");

    private HandlerUtils() {}

    public static String errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return GSON.toJson(error);
    }

    public static void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                        String content, HttpHeaders headers) {
        headers.set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        sendResponse(ctx, status, content, headers);
    }

    public static void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                    String content, HttpHeaders headers) {
        ByteBuf buf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static void sendBinaryResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                          byte[] content, HttpHeaders headers) {
        ByteBuf buf = Unpooled.wrappedBuffer(content);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    public static @Nullable String getFirstParam(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    public static int parseIntParam(Map<String, List<String>> params, String name,
                                    int defaultValue, int min, int max) {
        String raw = getFirstParam(params, name);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException("Empty value");
        }

        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number");
        }

        if (value < min || value > max) {
            throw new IllegalArgumentException("Out of range");
        }

        return value;
    }

    public static void runOnServerThread(ChannelHandlerContext ctx, HttpHeaders headers, Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Server not available"), headers);
            return;
        }

        server.execute(() -> executeSafely(ctx, headers, task));
    }

    public static void scheduleOnServerThread(Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(task);
        }
    }

    private static void executeSafely(ChannelHandlerContext ctx, HttpHeaders headers, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error handling API request", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorJson("Internal Server Error"), headers);
        }
    }

    public static @Nullable WebTerminalBlockEntity getConnectedTerminal() {
        WebTerminalBlockEntity terminal = WebServer.getInstance().getFirstTerminal();
        if (terminal == null) {
            return null;
        }
        return terminal.isConnectedToGrid() ? terminal : null;
    }

    public static @Nullable WebTerminalBlockEntity getConnectedTerminal(String terminalId) {
        if (terminalId != null && !terminalId.isEmpty()) {
            WebTerminalBlockEntity terminal = WebServer.getInstance().getTerminal(terminalId);
            if (terminal != null && terminal.isConnectedToGrid()) {
                return terminal;
            }
            return null;
        }
        return getConnectedTerminal();
    }

    public static @Nullable UUID getAuthenticatedUserUuid(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return null;
        }
        return ctx.channel().attr(AUTHENTICATED_USER_UUID).get();
    }
}
