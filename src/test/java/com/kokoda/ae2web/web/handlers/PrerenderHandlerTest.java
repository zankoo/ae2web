package com.kokoda.ae2web.web.handlers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderHandlerTest {
    private PrerenderStorageService storage;

    @BeforeEach
    void setUp() {
        storage = PrerenderStorageService.getInstance();
        storage.initialize();
        storage.clearAll();
    }

    @AfterEach
    void tearDown() {
        storage.clearAll();
    }

    @Test
    void checkRejectsMissingBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/check",
                Unpooled.copiedBuffer("", CharsetUtil.UTF_8));
        PrerenderHandler.route(ctx, req, "/api/prerender/check", HttpMethod.POST, Map.of(), headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Request body required");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void checkRejectsInvalidIconKeyInItemIds() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        String body = "{\"itemIds\":[\"minecraft:stone\"]}";
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/check",
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        PrerenderHandler.route(ctx, req, "/api/prerender/check", HttpMethod.POST, Map.of(), headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8))
                    .contains("Invalid iconKey");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void checkSplitsExistingAndMissing() {
        String existingKey = "minecraft:stone@h1_ok";
        storage.saveIcon(existingKey, minimalPngHeader(), null, null);

        String missingKey = "minecraft:dirt@h1_missing";

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        String body = "{\"itemIds\":[\"" + existingKey + "\",\"" + missingKey + "\"]}";
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/check",
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        PrerenderHandler.route(ctx, req, "/api/prerender/check", HttpMethod.POST, Map.of(), headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);

            JsonObject json = JsonParser.parseString(resp.content().toString(CharsetUtil.UTF_8)).getAsJsonObject();
            assertThat(json.getAsJsonArray("existing").asList()).extracting(e -> e.getAsString())
                    .containsExactly(existingKey);
            assertThat(json.getAsJsonArray("missing").asList()).extracting(e -> e.getAsString())
                    .containsExactly(missingKey);
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    private static byte[] minimalPngHeader() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
    }
}

