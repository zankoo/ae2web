package com.kokoda.ae2web.util;

import org.jetbrains.annotations.Nullable;

import java.net.HttpURLConnection;

public final class PrerenderUtils {
    private PrerenderUtils() {}

    public static boolean hasMore(int totalMissing, int limit) {
        return totalMissing > limit;
    }

    public static boolean shouldGc(long lastSeenMs, long cutoffMs, boolean locked, boolean baseVariant) {
        if (locked || baseVariant) {
            return false;
        }
        if (lastSeenMs <= 0) {
            return false;
        }
        return lastSeenMs < cutoffMs;
    }

    public static void applyBearerAuth(HttpURLConnection conn, @Nullable String token) {
        if (conn == null) {
            return;
        }
        if (token == null || token.isBlank()) {
            return;
        }
        conn.setRequestProperty("Authorization", "Bearer " + token);
    }
}
