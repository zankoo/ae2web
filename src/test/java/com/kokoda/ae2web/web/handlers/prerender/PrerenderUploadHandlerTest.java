package com.kokoda.ae2web.web.handlers.prerender;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kokoda.ae2web.web.WebServer;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderUploadHandlerTest {
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
    void uploadRejectsInvalidBase64() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        String body = "{\"itemId\":\"minecraft:stone@h1_ok\",\"png\":\"!!!!\"}";
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/upload",
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));

        PrerenderUploadHandler.handleUpload(ctx, req, headers);

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
            assertThat(resp.content().toString(CharsetUtil.UTF_8)).contains("Invalid base64");
        } finally {
            ReferenceCountUtil.release(resp);
        }
    }

    @Test
    void uploadSavesIconAndReturnsOk() {
        String iconKey = "minecraft:stone@h1_ok";
        byte[] png = minimalPngHeader();
        String base64 = Base64.getEncoder().encodeToString(png);

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        var headers = new DefaultHttpHeaders();

        String body = "{\"itemId\":\"" + iconKey + "\",\"png\":\"" + base64 + "\",\"name\":\"Stone\",\"tooltip\":[\"Stone\"]}";
        var req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/api/prerender/upload",
                Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));

        PrerenderUploadHandler.handleUpload(ctx, req, headers);

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            channel.runPendingTasks();
            assertThat(channel.outboundMessages()).isNotEmpty();
        });

        FullHttpResponse resp = (FullHttpResponse) channel.readOutbound();
        try {
            assertThat(resp.status()).isEqualTo(HttpResponseStatus.OK);
            JsonObject json = JsonParser.parseString(resp.content().toString(CharsetUtil.UTF_8)).getAsJsonObject();
            assertThat(json.get("status").getAsString()).isEqualTo("saved");
            assertThat(json.get("itemId").getAsString()).isEqualTo(iconKey);
        } finally {
            ReferenceCountUtil.release(resp);
        }

        assertThat(storage.hasIcon(iconKey)).isTrue();
        assertThat(storage.getTranslation(iconKey)).isEqualTo("Stone");
        assertThat(storage.getTooltip(iconKey)).containsExactly("Stone");
    }

    private static byte[] minimalPngHeader() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
    }
}
