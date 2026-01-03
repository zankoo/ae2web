package com.kokoda.ae2web.network;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2WebMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void init() {
        CHANNEL.registerMessage(
                packetId++,
                RenameTerminalPacket.class,
                RenameTerminalPacket::encode,
                RenameTerminalPacket::decode,
                RenameTerminalPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                CopyToClipboardPacket.class,
                CopyToClipboardPacket::encode,
                CopyToClipboardPacket::decode,
                CopyToClipboardPacket::handle
        );

        AE2WebMod.LOGGER.info("Registered network packets");
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    public static void sendToPlayer(ServerPlayer player, Object msg) {
        if (player == null || msg == null) {
            return;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
