package com.kokoda.ae2web.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyToClipboardPacketTest {
    @Test
    void encodeDecode_roundTripPreservesText() {
        CopyToClipboardPacket pkt = new CopyToClipboardPacket("abc");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        CopyToClipboardPacket.encode(pkt, buf);
        CopyToClipboardPacket decoded = CopyToClipboardPacket.decode(buf);

        assertThat(decoded.getText()).isEqualTo("abc");
    }
}

