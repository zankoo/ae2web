package com.kokoda.ae2web.network;

import com.kokoda.ae2web.client.ClientClipboard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class CopyToClipboardPacket {
    private static final int MAX_TEXT_LENGTH = 4096;

    private final String text;

    public CopyToClipboardPacket(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    public static void encode(CopyToClipboardPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.text, MAX_TEXT_LENGTH);
    }

    public static CopyToClipboardPacket decode(FriendlyByteBuf buf) {
        return new CopyToClipboardPacket(buf.readUtf(MAX_TEXT_LENGTH));
    }

    public static void handle(CopyToClipboardPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> ClientClipboard.setClipboard(pkt.text)));
        ctx.setPacketHandled(true);
    }
}

