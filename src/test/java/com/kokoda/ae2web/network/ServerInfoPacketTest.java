package com.kokoda.ae2web.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoPacketTest {
    @Test
    void encodeDecode_roundTripPreservesFields() {
        ServerInfoPacket pkt = new ServerInfoPacket(9000, false, 250, "secret-token");

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ServerInfoPacket.encode(pkt, buf);
        ServerInfoPacket decoded = ServerInfoPacket.decode(buf);

        assertThat(decoded.getHttpPort()).isEqualTo(9000);
        assertThat(decoded.isPrerenderEnabled()).isFalse();
        assertThat(decoded.getPrerenderAutoMaxItems()).isEqualTo(250);
        assertThat(decoded.getToken()).isEqualTo("secret-token");
    }

    @Test
    void nullTokenIsEncodedAsEmpty() {
        ServerInfoPacket pkt = new ServerInfoPacket(8080, true, 0, null);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ServerInfoPacket.encode(pkt, buf);

        assertThat(ServerInfoPacket.decode(buf).getToken()).isEmpty();
    }
}
