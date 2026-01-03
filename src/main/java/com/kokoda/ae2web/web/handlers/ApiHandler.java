package com.kokoda.ae2web.web.handlers;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.auth.AuthTokenExtractor;
import com.kokoda.ae2web.web.auth.PlayerAuthService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.io.InputStream;
import java.util.Locale;
import java.util.*;
import java.util.UUID;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

/**
 * Main HTTP request router.
 * Delegates to specialized handlers for each endpoint group.
 */
public class ApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        HttpMethod method = request.method();
        String path = new QueryStringDecoder(uri).path();

        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, X-AE2Web-Lang");

        if (method == HttpMethod.OPTIONS) {
            sendResponse(ctx, HttpResponseStatus.OK, "", headers);
            return;
        }

        ctx.channel().attr(AUTHENTICATED_USER_UUID).set(null);

        if (path != null && path.startsWith("/api/")) {
            if (!checkAuth(request, headers, ctx)) {
                return;
            }
        }

        try {
            routeRequest(ctx, request, uri, method, headers);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error handling request: {}", uri, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorJson("Internal Server Error"), headers);
        }
    }

    private boolean checkAuth(FullHttpRequest request, HttpHeaders headers, ChannelHandlerContext ctx) {
        String token = AuthTokenExtractor.extractToken(request);

        UUID userUuid = null;
        if (token != null) {
            userUuid = PlayerAuthService.getInstance().authenticate(token);
        }
        ctx.channel().attr(AUTHENTICATED_USER_UUID).set(userUuid);

        if (!AE2WebConfig.isAuthEnabled()) {
            return true;
        }

        if (userUuid != null) {
            return true;
        }

        // Legacy shared token support (does not map to a player UUID).
        if (token != null && token.equals(AE2WebConfig.getAuthToken())) {
            return true;
        }

        sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED,
                errorJson("Unauthorized - Invalid or missing token"), headers);
        return false;
    }

    private void routeRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                              String uri, HttpMethod method, HttpHeaders headers) {
        if (!uri.startsWith("/api/")) {
            serveStaticPath(ctx, uri, headers);
            return;
        }

        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        Map<String, List<String>> params = decoder.parameters();

        routeApiRequest(ctx, request, path, method, params, headers);
    }

    private void routeApiRequest(ChannelHandlerContext ctx, FullHttpRequest request,
                                 String path, HttpMethod method,
                                 Map<String, List<String>> params, HttpHeaders headers) {
        switch (path) {
            case "/api/status" ->
                    StatusHandler.handleStatus(ctx, request, params, headers);
            case "/api/terminals" ->
                    StatusHandler.handleTerminals(ctx, request, params, headers);
            case "/api/items" ->
                    InventoryHandler.handleItems(ctx, request, params, headers);
            case "/api/events/inventory" ->
                    SseHandler.handleInventoryEvents(ctx, request, params, headers);
            case "/api/craft" -> {
                if (method == HttpMethod.POST) {
                    CraftingHandler.handleCraft(ctx, request, params, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            case "/api/cpus" ->
                    CraftingHandler.handleCpus(ctx, request, params, headers);
            case "/api/cpus/process" -> {
                if (method == HttpMethod.GET) {
                    CraftingHandler.handleCpuProcess(ctx, request, params, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            case "/api/user/inventoryConfig" -> {
                if (method == HttpMethod.GET || method == HttpMethod.POST) {
                    UserConfigHandler.handleInventoryConfig(ctx, request, params, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            default -> {
                if (path.startsWith("/api/prerender/")) {
                    PrerenderHandler.route(ctx, request, path, method, params, headers);
                } else {
                    sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                            errorJson("API endpoint not found: " + path), headers);
                }
            }
        }
    }

    private void sendMethodNotAllowed(ChannelHandlerContext ctx, HttpHeaders headers) {
        sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                errorJson("Method not allowed"), headers);
    }

    private void serveStaticPath(ChannelHandlerContext ctx, String uri, HttpHeaders headers) {
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        if (path == null || path.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Not Found"), headers);
            return;
        }

        if (path.equals("/")) {
            serveStaticResource(ctx, "index.html", headers);
            return;
        }

        if (!path.startsWith("/")) {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Not Found"), headers);
            return;
        }

        String resourcePath = path.substring(1);
        if (resourcePath.isBlank()
                || resourcePath.contains("..")
                || resourcePath.contains("\\")
                || resourcePath.startsWith("/")) {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Not Found"), headers);
            return;
        }

        serveStaticResource(ctx, resourcePath, headers);
    }

    private static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".map")) return "application/json";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".webmanifest")) return "application/manifest+json";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void serveStaticResource(ChannelHandlerContext ctx, String filename, HttpHeaders headers) {
        try {
            byte[] content;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("web/" + filename)) {
                if (is == null) {
                    sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Not Found"), headers);
                    return;
                }
                content = is.readAllBytes();
            }

            String contentType = contentTypeFor(filename);
            if (contentType.startsWith("text/") || contentType.equals("application/javascript") || contentType.equals("application/json")) {
                headers.set(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=UTF-8");
            } else {
                headers.set(HttpHeaderNames.CONTENT_TYPE, contentType);
            }

            sendBinaryResponse(ctx, HttpResponseStatus.OK, content, headers);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error serving static file: {}", filename, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    errorJson("Error serving file"), headers);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        AE2WebMod.LOGGER.error("HTTP handler error", cause);
        ctx.close();
    }
}
