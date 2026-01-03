package com.kokoda.ae2web.server.command;

import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.network.CopyToClipboardPacket;
import com.kokoda.ae2web.network.PacketHandler;
import com.kokoda.ae2web.util.ChatComponentUtils;
import com.kokoda.ae2web.web.auth.PlayerAuthService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class TokenCommand {
    private TokenCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ae2web")
                        .then(Commands.literal("token")
                                .executes(TokenCommand::executeShowToken))
        );
    }

    private static int executeShowToken(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }

        UUID playerUuid = player.getUUID();
        String token = PlayerAuthService.getInstance().getOrCreateToken(playerUuid);
        String url = "http://localhost:" + AE2WebConfig.getHttpPort() + "/?token=" + token;

        source.sendSuccess(() -> Component.literal("[AE2 Web Terminal]").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Token: ").withStyle(ChatFormatting.GRAY)
                .append(ChatComponentUtils.copyToClipboardText(token, ChatFormatting.YELLOW)), false);
        source.sendSuccess(() -> Component.literal("Personal URL: ").withStyle(ChatFormatting.GRAY)
                .append(ChatComponentUtils.copyToClipboardText(url, ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("Keep this token secret.").withStyle(ChatFormatting.DARK_GRAY), false);

        PacketHandler.sendToPlayer(player, new CopyToClipboardPacket(token));
        source.sendSuccess(() -> Component.literal("已复制 Token 到剪贴板。").withStyle(ChatFormatting.DARK_GRAY), false);
        return 1;
    }
}
