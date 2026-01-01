package com.kokoda.ae2web.network;

import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RenameTerminalPacket {
    private final BlockPos pos;
    private final String newName;

    public RenameTerminalPacket(BlockPos pos, String newName) {
        this.pos = pos;
        this.newName = newName != null ? newName : "";
    }

    public static void encode(RenameTerminalPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.newName, 256);
    }

    public static RenameTerminalPacket decode(FriendlyByteBuf buf) {
        return new RenameTerminalPacket(buf.readBlockPos(), buf.readUtf(256));
    }

    public static void handle(RenameTerminalPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Validate distance
            if (player.distanceToSqr(pkt.pos.getX(), pkt.pos.getY(), pkt.pos.getZ()) > 64) {
                return;
            }

            BlockEntity be = player.level().getBlockEntity(pkt.pos);
            if (be instanceof WebTerminalBlockEntity terminal) {
                terminal.setCustomName(pkt.newName);
            }
        });
        ctx.setPacketHandled(true);
    }
}
