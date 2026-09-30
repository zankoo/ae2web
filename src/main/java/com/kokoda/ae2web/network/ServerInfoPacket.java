package com.kokoda.ae2web.network;

import com.kokoda.ae2web.client.PrerenderTrigger;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.auth.PlayerAuthService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> client: the web server settings the in-game prerender client needs.
 *
 * The config file is not synced to clients, so without this the client would guess the port and
 * token from its own local config.
 */
public final class ServerInfoPacket {
    private static final int MAX_TOKEN_LENGTH = 256;

    private final int httpPort;
    private final boolean prerenderEnabled;
    private final int prerenderAutoMaxItems;
    private final String token;

    public ServerInfoPacket(int httpPort, boolean prerenderEnabled, int prerenderAutoMaxItems, String token) {
        this.httpPort = httpPort;
        this.prerenderEnabled = prerenderEnabled;
        this.prerenderAutoMaxItems = prerenderAutoMaxItems;
        this.token = token != null ? token : "";
    }

    /**
     * Builds the packet for a player. When auth is enabled the player's personal API token is included
     * so the prerender client can authenticate as that player.
     */
    public static ServerInfoPacket forPlayer(UUID playerUuid) {
        String token = AE2WebConfig.isAuthEnabled()
                ? PlayerAuthService.getInstance().getOrCreateToken(playerUuid)
                : "";
        return new ServerInfoPacket(
                AE2WebConfig.getHttpPort(),
                AE2WebConfig.isPrerenderEnabled(),
                AE2WebConfig.getPrerenderAutoMaxItems(),
                token);
    }

    public int getHttpPort() {
        return httpPort;
    }

    public boolean isPrerenderEnabled() {
        return prerenderEnabled;
    }

    public int getPrerenderAutoMaxItems() {
        return prerenderAutoMaxItems;
    }

    public String getToken() {
        return token;
    }

    public static void encode(ServerInfoPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.httpPort);
        buf.writeBoolean(pkt.prerenderEnabled);
        buf.writeVarInt(pkt.prerenderAutoMaxItems);
        buf.writeUtf(pkt.token, MAX_TOKEN_LENGTH);
    }

    public static ServerInfoPacket decode(FriendlyByteBuf buf) {
        int httpPort = buf.readVarInt();
        boolean prerenderEnabled = buf.readBoolean();
        int autoMaxItems = buf.readVarInt();
        String token = buf.readUtf(MAX_TOKEN_LENGTH);
        return new ServerInfoPacket(httpPort, prerenderEnabled, autoMaxItems, token);
    }

    public static void handle(ServerInfoPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PrerenderTrigger.onServerInfo(pkt)));
        ctx.setPacketHandled(true);
    }
}
