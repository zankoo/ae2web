package com.kokoda.ae2web.web.handlers;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HandlerUtilsTest {
    @Test
    void errorJsonIncludesErrorField() {
        String json = HandlerUtils.errorJson("nope");
        assertThat(json).contains("\"error\"");
        assertThat(json).contains("nope");
    }

    @Test
    void getFirstParamReturnsNullWhenMissingOrEmpty() {
        assertThat(HandlerUtils.getFirstParam(Map.of(), "x")).isNull();
        assertThat(HandlerUtils.getFirstParam(Map.of("x", List.of()), "x")).isNull();
    }

    @Test
    void getFirstParamReturnsFirstValue() {
        assertThat(HandlerUtils.getFirstParam(Map.of("x", List.of("a", "b")), "x")).isEqualTo("a");
    }

    @Test
    void parseIntParamReturnsDefaultWhenMissing() {
        int value = HandlerUtils.parseIntParam(Map.of(), "limit", 10, 1, 100);
        assertThat(value).isEqualTo(10);
    }

    @Test
    void parseIntParamRejectsBlankInvalidAndOutOfRange() {
        assertThatThrownBy(() -> HandlerUtils.parseIntParam(Map.of("x", List.of("")), "x", 0, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty");
        assertThatThrownBy(() -> HandlerUtils.parseIntParam(Map.of("x", List.of("nope")), "x", 0, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
        assertThatThrownBy(() -> HandlerUtils.parseIntParam(Map.of("x", List.of("999")), "x", 0, 1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Out of range");
    }

    @Test
    void parseIntParamParsesValidNumberInRange() {
        int value = HandlerUtils.parseIntParam(Map.of("x", List.of("5")), "x", 0, 1, 10);
        assertThat(value).isEqualTo(5);
    }

    @Test
    void runOnServerThreadReturns503WhenServerUnavailable() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        HandlerUtils.runOnServerThread(ctx, headers, () -> {
            throw new AssertionError("Task should not run when server is unavailable");
        });

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Server not available");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }
}

