package com.kokoda.ae2web.web.handlers.prerender;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderClaimHandlerTest {
    @BeforeEach
    void resetStorage() {
        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();
        storage.clearAll();
    }

    @Test
    void claimRejectsEmptyBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/claim",
                Unpooled.copiedBuffer("", CharsetUtil.UTF_8));

        PrerenderClaimHandler.handleClaim(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Request body required");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void claimRejectsInvalidJson() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/claim",
                Unpooled.copiedBuffer("{", CharsetUtil.UTF_8));

        PrerenderClaimHandler.handleClaim(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Invalid JSON");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void claimRejectsMissingFields() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/claim",
                Unpooled.copiedBuffer("{\"itemIds\":[]}", CharsetUtil.UTF_8));

        PrerenderClaimHandler.handleClaim(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Missing itemIds or clientId");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void claimRejectsInvalidIconKeys() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/claim",
                Unpooled.copiedBuffer("{\"clientId\":\"c1\",\"itemIds\":[\"minecraft:stone\"]}", CharsetUtil.UTF_8));

        PrerenderClaimHandler.handleClaim(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Invalid iconKey");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void claimReturnsClaimedIdsAndCounts() {
        String a = "minecraft:stone@h1_a";
        String b = "minecraft:dirt@h1_b";

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/claim",
                Unpooled.copiedBuffer("{\"clientId\":\"c1\",\"itemIds\":[\"" + a + "\",\"" + b + "\"]}", CharsetUtil.UTF_8));

        PrerenderClaimHandler.handleClaim(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            JsonObject json = JsonParser.parseString(resp.content().toString(CharsetUtil.UTF_8)).getAsJsonObject();

            assertThat(json.get("claimedCount").getAsInt()).isEqualTo(2);
            assertThat(json.getAsJsonArray("claimed").asList()).extracting(e -> e.getAsString())
                    .containsExactly(a, b);
            assertThat(json.get("lockedByOthers").getAsInt()).isZero();
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }
}

