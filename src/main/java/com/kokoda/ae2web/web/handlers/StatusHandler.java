package com.kokoda.ae2web.web.handlers;

import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.web.WebServer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.minecraftforge.fml.ModList;

import java.util.*;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

/**
 * Handles /api/status and /api/terminals endpoints.
 */
public final class StatusHandler {

    private StatusHandler() {}

    public static void handleStatus(ChannelHandlerContext ctx, FullHttpRequest request,
                                    Map<String, List<String>> params, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            JsonObject status = new JsonObject();
            status.addProperty("running", WebServer.getInstance().isRunning());
            status.addProperty("terminals", WebServer.getInstance().getTerminals().size());
            status.addProperty("version", getModVersion());
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(status), headers);
        });
    }

    public static void handleTerminals(ChannelHandlerContext ctx, FullHttpRequest request,
                                       Map<String, List<String>> params, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            List<Map<String, Object>> terminalList = new ArrayList<>();

            for (WebTerminalBlockEntity terminal : WebServer.getInstance().getTerminals().values()) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", terminal.getTerminalId());
                info.put("name", terminal.getCustomName());
                info.put("displayName", terminal.getDisplayNameString());
                info.put("position", Map.of(
                        "x", terminal.getPosition().getX(),
                        "y", terminal.getPosition().getY(),
                        "z", terminal.getPosition().getZ()
                ));
                if (terminal.getTerminalLevel() != null) {
                    info.put("dimension", terminal.getTerminalLevel().dimension().location().toString());
                }
                info.put("connected", terminal.isConnectedToGrid());
                terminalList.add(info);
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK,
                    GSON.toJson(Map.of("terminals", terminalList)), headers);
        });
    }

    private static String getModVersion() {
        try {
            return ModList.get()
                    .getModContainerById(AE2WebMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
