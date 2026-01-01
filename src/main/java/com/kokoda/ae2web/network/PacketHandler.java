package com.kokoda.ae2web.network;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
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

        AE2WebMod.LOGGER.info("Registered network packets");
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
