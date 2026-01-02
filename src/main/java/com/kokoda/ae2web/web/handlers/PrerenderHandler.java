package com.kokoda.ae2web.web.handlers;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.handlers.prerender.PrerenderClaimHandler;
import com.kokoda.ae2web.web.handlers.prerender.PrerenderImageHandler;
import com.kokoda.ae2web.web.handlers.prerender.PrerenderMissingHandler;
import com.kokoda.ae2web.web.handlers.prerender.PrerenderTooltipHandler;
import com.kokoda.ae2web.web.handlers.prerender.PrerenderUploadHandler;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

/**
 * Handles /api/prerender/* endpoints for icon prerendering.
 */
public final class PrerenderHandler {

    private PrerenderHandler() {}

    /**
     * Route prerender requests to specific handlers.
     */
    public static void route(ChannelHandlerContext ctx, FullHttpRequest request,
                            String path, HttpMethod method,
                            Map<String, List<String>> params, HttpHeaders headers) {
        if (!AE2WebConfig.isPrerenderEnabled()) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Prerendering is disabled"), headers);
            return;
        }

        switch (path) {
            case "/api/prerender/missing" -> PrerenderMissingHandler.handleMissing(ctx, params, headers);
            case "/api/prerender/all" -> handleAll(ctx, params, headers);
            case "/api/prerender/upload" -> {
                if (method == HttpMethod.POST) {
                    PrerenderUploadHandler.handleUpload(ctx, request, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            case "/api/prerender/image" -> PrerenderImageHandler.handleImage(ctx, params, headers);
            case "/api/prerender/tooltip" -> PrerenderTooltipHandler.handleTooltip(ctx, params, headers);
            case "/api/prerender/claim" -> {
                if (method == HttpMethod.POST) {
                    PrerenderClaimHandler.handleClaim(ctx, request, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            case "/api/prerender/stats" -> handleStats(ctx, headers);
            case "/api/prerender/check" -> {
                if (method == HttpMethod.POST) {
                    handleCheck(ctx, request, headers);
                } else {
                    sendMethodNotAllowed(ctx, headers);
                }
            }
            default -> sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND,
                    errorJson("Prerender endpoint not found: " + path), headers);
        }
    }

    private static void sendMethodNotAllowed(ChannelHandlerContext ctx, HttpHeaders headers) {
        sendJsonResponse(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED,
                errorJson("Method not allowed"), headers);
    }

    private static void handleAll(ChannelHandlerContext ctx,
                                  Map<String, List<String>> params, HttpHeaders headers) {
        int limit;
        try {
            limit = parseIntParam(params, "limit", 100, 1, 100000);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid limit parameter"), headers);
            return;
        }

        runOnServerThread(ctx, headers, () -> {
            List<String> allIds = new ArrayList<>();
            int count = 0;

            // Add all items
            for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
                if ("minecraft:air".equals(itemId.toString())) {
                    continue;
                }
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    continue;
                }
                allIds.add(IconKeyUtils.iconKey(AEItemKey.of(item)));
                if (++count >= limit) {
                    break;
                }
            }

            // Add all fluids (excluding EMPTY)
            if (count < limit) {
                for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
                    var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                    if (fluid != null && fluid != Fluids.EMPTY) {
                        allIds.add(IconKeyUtils.iconKey(AEFluidKey.of(fluid)));
                        if (++count >= limit) {
                            break;
                        }
                    }
                }
            }

            int totalItems = (int) ForgeRegistries.ITEMS.getKeys().stream()
                    .filter(id -> !"minecraft:air".equals(id.toString()))
                    .count();
            int totalFluids = (int) ForgeRegistries.FLUIDS.getKeys().stream()
                    .filter(id -> {
                        var f = ForgeRegistries.FLUIDS.getValue(id);
                        return f != null && f != Fluids.EMPTY;
                    }).count();

            Map<String, Object> response = new HashMap<>();
            response.put("items", allIds);
            response.put("total", totalItems + totalFluids);
            response.put("returned", allIds.size());

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private static void handleStats(ChannelHandlerContext ctx, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            PrerenderStorageService storage = PrerenderStorageService.getInstance();
            storage.initialize();

            Map<String, Integer> stats = storage.getStats();
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(stats), headers);
        });
    }

    private static void handleCheck(ChannelHandlerContext ctx, FullHttpRequest request, HttpHeaders headers) {
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

        if (json == null || !json.has("itemIds")) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Missing itemIds"), headers);
            return;
        }

        List<String> itemIds = new ArrayList<>();
        json.get("itemIds").getAsJsonArray().forEach(e -> itemIds.add(e.getAsString()));
        for (String itemId : itemIds) {
            int atIndex = itemId != null ? itemId.lastIndexOf('@') : -1;
            if (atIndex <= 0 || atIndex >= itemId.length() - 1) {
                sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                        errorJson("Invalid iconKey in itemIds (expected namespace:path@hash)"), headers);
                return;
            }
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        List<String> existing = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : itemIds) {
            if (storage.hasIcon(id)) {
                existing.add(id);
            } else {
                missing.add(id);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("existing", existing);
        response.put("missing", missing);
        sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
    }
}
