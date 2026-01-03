package com.kokoda.ae2web.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class ChatComponentUtils {
    private static final Component COPY_HOVER_TEXT = Component.literal("点击复制").withStyle(ChatFormatting.GRAY);

    private ChatComponentUtils() {}

    public static Component copyToClipboardText(String value, ChatFormatting valueColor) {
        String safeValue = value != null ? value : "";
        ChatFormatting safeColor = valueColor != null ? valueColor : ChatFormatting.WHITE;

        return Component.literal(safeValue)
                .withStyle(safeColor)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, safeValue))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, COPY_HOVER_TEXT)));
    }
}
