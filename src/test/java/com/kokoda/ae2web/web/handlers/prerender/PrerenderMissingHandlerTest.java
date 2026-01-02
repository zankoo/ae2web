package com.kokoda.ae2web.web.handlers.prerender;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderMissingHandlerTest {
    @Test
    void rejectsInvalidScope() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderMissingHandler.handleMissing(ctx, Map.of("scope", List.of("nope")), headers);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Invalid scope");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void rejectsInvalidLimit() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderMissingHandler.handleMissing(ctx, Map.of("limit", List.of("0")), headers);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Invalid limit parameter");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void returns503WhenServerUnavailable() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderMissingHandler.handleMissing(ctx, Map.of(), headers);

        FullHttpResponse resp = channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Server not available");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }
}

