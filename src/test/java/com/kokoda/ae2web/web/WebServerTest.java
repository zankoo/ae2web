package com.kokoda.ae2web.web;

import com.kokoda.ae2web.web.handlers.SseHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebServerTest {
    @AfterEach
    void resetSingleton() {
        WebServer.getInstance().stop();
    }

    @Test
    void subscribeAndUnsubscribeInventoryTracksSubscribers() {
        var server = WebServer.getInstance();
        var channel = new io.netty.channel.embedded.EmbeddedChannel();

        server.subscribeInventory("t1", channel);
        assertThat(server.hasInventorySubscribers("t1")).isTrue();

        server.unsubscribeInventory("t1", channel);
        assertThat(server.hasInventorySubscribers("t1")).isFalse();
    }

    @Test
    void notifyInventoryChangedWritesEventToSubscribedChannel() {
        var server = WebServer.getInstance();
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        channel.attr(SseHandler.SUBSCRIBED_TERMINAL).set("t1");
        server.subscribeInventory("t1", channel);

        server.notifyInventoryChanged("t1");
        channel.runPendingTasks();

        Object outbound = channel.readOutbound();
        try {
            assertThat(outbound).isInstanceOf(HttpContent.class);
            String payload = ((HttpContent) outbound).content().toString(CharsetUtil.UTF_8);
            assertThat(payload).contains("event: inventory");
            assertThat(payload).contains("\"terminalId\":\"t1\"");
            assertThat(payload).contains("\"version\":1");
        } finally {
            ReferenceCountUtil.release(outbound);
        }

        assertThat(server.getInventoryVersion("t1")).isEqualTo(1L);
    }

    @Test
    void notifyInventoryChangedSkipsChannelsWithoutSubscriptionMarker() {
        var server = WebServer.getInstance();
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        server.subscribeInventory("t1", channel);

        server.notifyInventoryChanged("t1");
        channel.runPendingTasks();

        Object outbound = channel.readOutbound();
        ReferenceCountUtil.release(outbound);
        assertThat(outbound).isNull();
    }

    @Test
    void unregisterTerminalClosesSubscribedChannels() {
        var server = WebServer.getInstance();

        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        server.subscribeInventory("t1", channel);
        assertThat(channel.isActive()).isTrue();

        server.unregisterTerminalById("t1");
        channel.runPendingTasks();

        assertThat(channel.isActive()).isFalse();
        assertThat(server.hasInventorySubscribers("t1")).isFalse();
    }

    @Test
    void inventoryVersionDefaultsToZero() {
        var server = WebServer.getInstance();
        assertThat(server.getInventoryVersion(null)).isZero();
        assertThat(server.getInventoryVersion("")).isZero();
        assertThat(server.getInventoryVersion("missing")).isZero();
    }
}
