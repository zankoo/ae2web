package com.kokoda.ae2web.web.inventory;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;

public final class InventoryNameUtils {
    private InventoryNameUtils() {
    }

    public static String getTranslatedName(AEItemKey itemKey, PrerenderStorageService storage) {
        if (itemKey == null) {
            return "";
        }
        // Try to use cached translation from prerender (client-side language)
        String iconKey = IconKeyUtils.iconKey(itemKey);
        String cached = storage.getTranslation(iconKey);
        if (cached != null && !cached.isBlank()) {
            return stripMinecraftFormatting(cached);
        }
        // Fallback to server-side name (may be in server's language)
        return stripMinecraftFormatting(itemKey.getDisplayName().getString());
    }

    public static String getFluidName(AEFluidKey fluidKey, PrerenderStorageService storage) {
        if (fluidKey == null) {
            return "";
        }
        // Try to use cached translation from prerender (client-side language)
        String iconKey = IconKeyUtils.iconKey(fluidKey);
        String cached = storage.getTranslation(iconKey);
        if (cached != null && !cached.isBlank()) {
            return stripMinecraftFormatting(cached);
        }
        // Fallback to server-side name (may be in server's language)
        return stripMinecraftFormatting(fluidKey.getDisplayName().getString());
    }

    /**
     * Remove Minecraft formatting codes (§ followed by color/format character).
     * Examples: "256安§4UHV§r激光源仓" -> "256安UHV激光源仓"
     */
    public static String stripMinecraftFormatting(String text) {
        if (text == null) {
            return "";
        }
        // §0-§9, §a-§f: colors, §k: obfuscated, §l: bold, §m: strikethrough,
        // §n: underline, §o: italic, §r: reset (case insensitive)
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}

