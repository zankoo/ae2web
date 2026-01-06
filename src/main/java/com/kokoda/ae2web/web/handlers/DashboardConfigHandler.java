package com.kokoda.ae2web.web.handlers;

import com.kokoda.ae2web.web.config.PlayerDashboardConfigService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class DashboardConfigHandler {
    private DashboardConfigHandler() {}

    public static void handleDashboardConfig(ChannelHandlerContext ctx,
                                             FullHttpRequest request,
                                             Map<String, List<String>> params,
                                             HttpHeaders headers) {
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

        if (request.method() == HttpMethod.GET) {
            runOnIoThread(ctx, headers, () -> handleGet(ctx, headers, userUuid, terminalId));
            return;
        }

        if (request.method() == HttpMethod.POST) {
            String body = request.content().toString(CharsetUtil.UTF_8);
            PlayerDashboardConfigService.DashboardConfig config;
            try {
                config = GSON.fromJson(body, PlayerDashboardConfigService.DashboardConfig.class);
            } catch (Exception e) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Invalid JSON"), headers);
                return;
            }

            PlayerDashboardConfigService.DashboardConfig parsed = config;
            runOnIoThread(ctx, headers, () -> handlePost(ctx, headers, userUuid, terminalId, parsed));
            return;
        }

        sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, errorJson("Method not allowed"), headers);
    }

    private static void handleGet(ChannelHandlerContext ctx, HttpHeaders headers, UUID userUuid, String terminalId) {
        PlayerDashboardConfigService service = PlayerDashboardConfigService.getInstance();
        PlayerDashboardConfigService.DashboardConfig config = service.getConfig(userUuid, terminalId);
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(config), headers);
    }

    private static void handlePost(ChannelHandlerContext ctx, HttpHeaders headers, UUID userUuid, String terminalId,
                                   PlayerDashboardConfigService.DashboardConfig config) {
        PlayerDashboardConfigService service = PlayerDashboardConfigService.getInstance();
        service.saveConfig(userUuid, terminalId, config);
        PlayerDashboardConfigService.DashboardConfig saved = service.getConfig(userUuid, terminalId);
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(saved), headers);
    }
}
