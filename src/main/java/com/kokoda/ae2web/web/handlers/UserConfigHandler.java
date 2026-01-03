package com.kokoda.ae2web.web.handlers;

import com.kokoda.ae2web.web.config.PlayerInventoryConfigService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class UserConfigHandler {
    private UserConfigHandler() {}

    public static void handleInventoryConfig(ChannelHandlerContext ctx,
                                             FullHttpRequest request,
                                             Map<String, List<String>> params,
                                             HttpHeaders headers) {
        if (ctx == null || request == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Bad request"), headers);
            return;
        }

        UUID userUuid = getAuthenticatedUserUuid(ctx);
        if (userUuid == null) {
            sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED, errorJson("Unauthorized"), headers);
            return;
        }

        String terminalId = getFirstParam(params, "terminalId");
        if (terminalId == null || terminalId.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing terminalId"), headers);
            return;
        }

        if (request.method() == io.netty.handler.codec.http.HttpMethod.GET) {
            runOnServerThread(ctx, headers, () -> handleGet(ctx, headers, userUuid, terminalId));
            return;
        }

        if (request.method() == io.netty.handler.codec.http.HttpMethod.POST) {
            // Important: FullHttpRequest is auto-released by Netty after the handler returns.
            // Copy the body now so we don't read from a released ByteBuf on the server thread.
            String body = request.content().toString(CharsetUtil.UTF_8);
            PlayerInventoryConfigService.InventoryConfig config;
            try {
                config = GSON.fromJson(body, PlayerInventoryConfigService.InventoryConfig.class);
            } catch (Exception e) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Invalid JSON"), headers);
                return;
            }

            PlayerInventoryConfigService.InventoryConfig parsed = config;
            runOnServerThread(ctx, headers, () -> handlePost(ctx, headers, userUuid, terminalId, parsed));
            return;
        }

        sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, errorJson("Method not allowed"), headers);
    }

    private static void handleGet(ChannelHandlerContext ctx, HttpHeaders headers, UUID userUuid, String terminalId) {
        PlayerInventoryConfigService service = PlayerInventoryConfigService.getInstance();
        PlayerInventoryConfigService.InventoryConfig config = service.getConfig(userUuid, terminalId);
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(config), headers);
    }

    private static void handlePost(ChannelHandlerContext ctx, HttpHeaders headers, UUID userUuid, String terminalId,
                                   PlayerInventoryConfigService.InventoryConfig config) {
        PlayerInventoryConfigService service = PlayerInventoryConfigService.getInstance();
        service.saveConfig(userUuid, terminalId, config);
        PlayerInventoryConfigService.InventoryConfig saved = service.getConfig(userUuid, terminalId);
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(saved), headers);
    }
}
