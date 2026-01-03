package com.kokoda.ae2web.web.handlers;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.kokoda.ae2web.web.dto.InventoryItemDTO;

import static org.assertj.core.api.Assertions.assertThat;

class SseHandlerTest {
    @Test
    void writeHelloEventWritesSsePayload() {
        var channel = new EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");

        SseHandler.writeHelloEvent(channel, "t1", 5);

        Object outbound = channel.readOutbound();
        try {
            assertThat(outbound).isInstanceOf(HttpContent.class);
            String payload = ((HttpContent) outbound).content().toString(CharsetUtil.UTF_8);
            assertThat(payload).contains("event: hello");
            assertThat(payload).contains("\"terminalId\":\"t1\"");
            assertThat(payload).contains("\"version\":5");
            assertThat(payload).endsWith("\n\n");
        } finally {
            ReferenceCountUtil.release(outbound);
        }
    }

    @Test
    void writeInventoryEventDoesNothingWhenTerminalDoesNotMatch() {
        var channel = new EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");

        SseHandler.writeInventoryEvent(channel, "t2", 1);

        Object outbound = channel.readOutbound();
        ReferenceCountUtil.release(outbound);
        assertThat(outbound).isNull();
    }

    @Test
    void writeInventoryEventWritesSsePayload() {
        var channel = new EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");

        SseHandler.writeInventoryEvent(channel, "t1", 5);

        Object outbound = channel.readOutbound();
        try {
            assertThat(outbound).isInstanceOf(HttpContent.class);
            String payload = ((HttpContent) outbound).content().toString(CharsetUtil.UTF_8);
            assertThat(payload).contains("event: inventory");
            assertThat(payload).contains("\"terminalId\":\"t1\"");
            assertThat(payload).contains("\"version\":5");
            assertThat(payload).endsWith("\n\n");
        } finally {
            ReferenceCountUtil.release(outbound);
        }
    }

    @Test
    void writeInventorySnapshotEventWritesSnapshotPayload() {
        var channel = new EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");

        var items = List.of(new InventoryItemDTO(
                "item",
                "minecraft:stone",
                "item:minecraft:stone@h1_test",
                "minecraft:stone@h1_test",
                "Stone",
                "block.minecraft.stone",
                64,
                "minecraft",
                false
        ));

        SseHandler.writeInventorySnapshotEvent(channel, "t1", 12, items);

        Object outbound = channel.readOutbound();
        try {
            assertThat(outbound).isInstanceOf(HttpContent.class);
            String payload = ((HttpContent) outbound).content().toString(CharsetUtil.UTF_8);
            assertThat(payload).contains("event: snapshot");
            assertThat(payload).contains("\"terminalId\":\"t1\"");
            assertThat(payload).contains("\"version\":12");
            assertThat(payload).contains("\"items\"");
            assertThat(payload).contains("\"uniqueId\":\"item:minecraft:stone@h1_test\"");
            assertThat(payload).contains("\"total\":1");
        } finally {
            ReferenceCountUtil.release(outbound);
        }
    }

    @Test
    void writeInventoryDeltaEventWritesDeltaPayload() {
        var channel = new EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");

        SseHandler.writeInventoryDeltaEvent(channel, "t1", "{\"terminalId\":\"t1\",\"version\":2}");

        Object outbound = channel.readOutbound();
        try {
            assertThat(outbound).isInstanceOf(HttpContent.class);
            String payload = ((HttpContent) outbound).content().toString(CharsetUtil.UTF_8);
            assertThat(payload).contains("event: delta");
            assertThat(payload).contains("\"terminalId\":\"t1\"");
            assertThat(payload).contains("\"version\":2");
        } finally {
            ReferenceCountUtil.release(outbound);
        }
    }
}
