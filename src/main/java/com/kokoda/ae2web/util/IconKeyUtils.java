package com.kokoda.ae2web.util;

import appeng.api.stacks.AEKey;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

import java.security.MessageDigest;

/**
 * Canonical iconKey formatting shared by server, client, and web DTOs.
 *
 * Format: {@code namespace:path@h1_<sha256hex>}
 */
public final class IconKeyUtils {
    private IconKeyUtils() {}

    public static String iconKey(AEKey key) {
        if (key == null) {
            return "";
        }

        ResourceLocation id = key.getId();
        if (id == null) {
            return "";
        }

        try {
            return id + "@h1_" + sha256Hex(serializeKey(key));
        } catch (RuntimeException e) {
            // In some contexts (e.g. plain unit tests), AE2 key type registry may not be initialized yet.
            // Fall back to an id-only hash to keep behavior stable and non-crashing.
            return iconKey(id);
        }
    }

    /**
     * Fallback key for cases where we only have the id string (should be rare).
     * Still uses the same {@code h1_} scheme to keep a single key system.
     */
    public static String iconKey(ResourceLocation id) {
        return id.toString() + "@h1_" + sha256Hex(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] serializeKey(AEKey key) {
        var buf = Unpooled.buffer();
        try {
            FriendlyByteBuf fb = new FriendlyByteBuf(buf);
            AEKey.writeKey(fb, key);

            int len = buf.readableBytes();
            byte[] out = new byte[len];
            buf.getBytes(0, out);
            return out;
        } finally {
            buf.release();
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return toHex(hash);
        } catch (Exception e) {
            // Extremely unlikely; fall back to a stable (but weaker) representation.
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        int i = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            out[i++] = Character.forDigit(v >>> 4, 16);
            out[i++] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(out);
    }
}
