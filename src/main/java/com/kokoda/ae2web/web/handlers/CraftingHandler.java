package com.kokoda.ae2web.web.handlers;

import com.kokoda.ae2web.web.handlers.crafting.CpuHandler;
import com.kokoda.ae2web.web.handlers.crafting.CraftRequestHandler;
import com.kokoda.ae2web.web.handlers.crafting.CraftingExecutors;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.List;
import java.util.Map;

/**
 * Handles /api/craft and /api/cpus endpoints.
 */
public final class CraftingHandler {

    private CraftingHandler() {}

    public static void shutdownExecutor() {
        CraftingExecutors.shutdown();
    }

    public static void handleCraft(ChannelHandlerContext ctx, FullHttpRequest request,
                                   Map<String, List<String>> params, HttpHeaders headers) {
        CraftRequestHandler.handleCraft(ctx, request, params, headers);
    }

    public static void handleCpus(ChannelHandlerContext ctx, FullHttpRequest request,
                                  Map<String, List<String>> params, HttpHeaders headers) {
        CpuHandler.handleCpus(ctx, request, params, headers);
    }
}
