package com.kokoda.ae2web.client;

import com.kokoda.ae2web.network.ServerInfoPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Web server settings received from the connected server via {@link ServerInfoPacket}.
 */
@OnlyIn(Dist.CLIENT)
public final class ServerConnectionInfo {
    private static volatile @Nullable ServerInfoPacket current = null;

    private ServerConnectionInfo() {}

    public static void set(ServerInfoPacket info) {
        current = info;
    }

    public static void clear() {
        current = null;
    }

    public static @Nullable ServerInfoPacket get() {
        return current;
    }

    /** Personal API token for this player, or null when the server has auth disabled. */
    public static @Nullable String token() {
        ServerInfoPacket info = current;
        if (info == null || info.getToken().isBlank()) {
            return null;
        }
        return info.getToken();
    }
}
