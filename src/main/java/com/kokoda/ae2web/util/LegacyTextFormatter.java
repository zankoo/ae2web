package com.kokoda.ae2web.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Utility for converting Minecraft text components to legacy formatting codes.
 * Used for tooltip rendering with Minecraft's section (§) formatting.
 */
public final class LegacyTextFormatter {
    private LegacyTextFormatter() {
    }

    public static String componentToLegacy(Component component) {
        if (component == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        component.visit((style, text) -> {
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }
            out.append(styleToLegacy(style));
            out.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    public static String styleToLegacy(Style style) {
        if (style == null) {
            return "§r";
        }

        StringBuilder out = new StringBuilder("§r");

        TextColor color = style.getColor();
        if (color != null) {
            String code = rgbToLegacyColorCode(color.getValue());
            if (code != null) {
                out.append('§').append(code);
            } else {
                out.append(rgbToLegacyHex(color.getValue()));
            }
        }

        if (style.isBold()) out.append("§l");
        if (style.isItalic()) out.append("§o");
        if (style.isUnderlined()) out.append("§n");
        if (style.isStrikethrough()) out.append("§m");
        if (style.isObfuscated()) out.append("§k");

        return out.toString();
    }

    private static String rgbToLegacyHex(int rgb) {
        String hex = String.format("%06x", (rgb & 0xFFFFFF));
        StringBuilder out = new StringBuilder("§x");
        for (int i = 0; i < hex.length(); i++) {
            out.append('§').append(hex.charAt(i));
        }
        return out.toString();
    }

    private static String rgbToLegacyColorCode(int rgb) {
        int v = rgb & 0xFFFFFF;
        if (v == 0x000000) return "0";
        if (v == 0x0000AA) return "1";
        if (v == 0x00AA00) return "2";
        if (v == 0x00AAAA) return "3";
        if (v == 0xAA0000) return "4";
        if (v == 0xAA00AA) return "5";
        if (v == 0xFFAA00) return "6";
        if (v == 0xAAAAAA) return "7";
        if (v == 0x555555) return "8";
        if (v == 0x5555FF) return "9";
        if (v == 0x55FF55) return "a";
        if (v == 0x55FFFF) return "b";
        if (v == 0xFF5555) return "c";
        if (v == 0xFF55FF) return "d";
        if (v == 0xFFFF55) return "e";
        if (v == 0xFFFFFF) return "f";
        return null;
    }
}
