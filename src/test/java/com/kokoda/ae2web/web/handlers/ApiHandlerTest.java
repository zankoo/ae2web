package com.kokoda.ae2web.web.handlers;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiHandlerTest {
    @Test
    void optionsAlwaysReturnsOk() {
        EmbeddedChannel channel = new EmbeddedChannel(new ApiHandler());
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "/api/status", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(req);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void rootServesIndexHtml() {
        EmbeddedChannel channel = new EmbeddedChannel(new ApiHandler());
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(req);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            assertThat(resp.headers().get(HttpHeaderNames.CONTENT_TYPE)).contains("text/html");
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).containsIgnoringCase("<!doctype html>");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void staticPathTraversalReturnsNotFound() {
        EmbeddedChannel channel = new EmbeddedChannel(new ApiHandler());
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/../nope", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(req);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void unknownApiEndpointReturnsNotFound() {
        EmbeddedChannel channel = new EmbeddedChannel(new ApiHandler());
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/nope", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(req);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("API endpoint not found");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void statusReturns503WhenServerUnavailable() {
        EmbeddedChannel channel = new EmbeddedChannel(new ApiHandler());
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/status", Unpooled.EMPTY_BUFFER);
        channel.writeInbound(req);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Server not available");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }
}
