package com.kokoda.ae2web.web.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.List;
import java.util.Map;

/**
 * Functional interface for handling HTTP API requests.
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * Handle an HTTP request.
     *
     * @param ctx     Netty channel context
     * @param request Full HTTP request
     * @param params  Query string parameters
     * @param headers Response headers (pre-populated with CORS)
     */
    void handle(ChannelHandlerContext ctx, FullHttpRequest request,
                Map<String, List<String>> params, HttpHeaders headers);
}
