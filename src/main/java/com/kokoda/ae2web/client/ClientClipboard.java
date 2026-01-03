package com.kokoda.ae2web.client;

import net.minecraft.client.Minecraft;

public final class ClientClipboard {
    private ClientClipboard() {}

    public static void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text != null ? text : "");
    }
}

