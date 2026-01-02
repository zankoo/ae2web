package com.kokoda.ae2web.web.handlers;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseHandlerTest {
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
}

