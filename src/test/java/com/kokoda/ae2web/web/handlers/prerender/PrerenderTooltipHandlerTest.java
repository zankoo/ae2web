package com.kokoda.ae2web.web.handlers.prerender;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderTooltipHandlerTest {
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
        WebServer.getInstance().stop();
    }

    @Test
    void tooltipRejectsMissingId() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderTooltipHandler.handleTooltip(ctx, Map.of(), headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Missing id");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void tooltipReturns404WhenMissing() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderTooltipHandler.handleTooltip(ctx, Map.of("id", List.of("minecraft:stone@h1_missing")), headers);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            channel.runPendingTasks();
            assertThat(channel.outboundMessages()).isNotEmpty();
        });

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.NOT_FOUND);
            assertThat(resp.headers().get(HttpHeaderNames.CACHE_CONTROL)).contains("no-cache");
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Tooltip not found");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void tooltipReturnsJsonWhenExists() {
        String iconKey = "minecraft:stone@h1_ok";
        storage.saveIcon(iconKey, minimalPngHeader(), "Stone", List.of("Stone", "Line2"));

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        PrerenderTooltipHandler.handleTooltip(ctx, Map.of("id", List.of(iconKey)), headers);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            channel.runPendingTasks();
            assertThat(channel.outboundMessages()).isNotEmpty();
        });

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);

            JsonObject json = JsonParser.parseString(resp.content().toString(CharsetUtil.UTF_8)).getAsJsonObject();
            assertThat(json.get("iconKey").getAsString()).isEqualTo(iconKey);
            assertThat(json.getAsJsonArray("tooltip").asList()).extracting(e -> e.getAsString())
                    .containsExactly("Stone", "Line2");
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
