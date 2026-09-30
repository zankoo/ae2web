package com.kokoda.ae2web.web.auth;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;

/**
 * Decides whether a browser request's {@code Origin} is allowed to reach the API.
 *
 * Browsers attach {@code Origin} to cross-origin requests (and to same-origin POSTs), so rejecting
 * foreign origins stops other websites from driving the API through the player's browser. Requests
 * without an {@code Origin} (the in-game client, curl, same-origin GETs) are allowed.
 */
public final class OriginPolicy {
    private OriginPolicy() {}

    /**
     * @param origin         value of the {@code Origin} header, may be null
     * @param hosts          request authorities the server is reachable as ({@code Host}, {@code X-Forwarded-Host});
     *                       null entries are ignored
     * @param allowedOrigins extra origins from config; {@code "*"} allows everything
     */
    public static boolean isAllowed(@Nullable String origin, Collection<String> hosts,
                                    Collection<? extends String> allowedOrigins) {
        if (origin == null || origin.isBlank()) {
            return true;
        }

        String normalizedOrigin = normalizeOrigin(origin);
        if (normalizedOrigin == null) {
            return false;
        }

        for (String allowed : allowedOrigins) {
            if (allowed == null) {
                continue;
            }
            if (allowed.trim().equals("*")) {
                return true;
            }
            if (normalizedOrigin.equals(normalizeOrigin(allowed))) {
                return true;
            }
        }

        String scheme = normalizedOrigin.substring(0, normalizedOrigin.indexOf("://"));
        String originAuthority = normalizedOrigin.substring(scheme.length() + 3);
        for (String host : hosts) {
            if (host == null || host.isBlank()) {
                continue;
            }
            // X-Forwarded-Host may be a comma-separated chain; the first entry is the client-facing host.
            String first = host.split(",", 2)[0].trim();
            if (originAuthority.equals(normalizeAuthority(scheme, first))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code scheme://host[:port]} in lower case with the default port removed, or null if
     * the value is not an http(s) origin (this includes the opaque origin {@code "null"}).
     */
    static @Nullable String normalizeOrigin(String origin) {
        String trimmed = origin.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception e) {
            return null;
        }

        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if (scheme == null || authority == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        return scheme + "://" + normalizeAuthority(scheme, authority);
    }

    private static String normalizeAuthority(String scheme, String authority) {
        String lower = authority.trim().toLowerCase(Locale.ROOT);
        String defaultPortSuffix = scheme.equals("https") ? ":443" : ":80";
        if (lower.endsWith(defaultPortSuffix)) {
            return lower.substring(0, lower.length() - defaultPortSuffix.length());
        }
        return lower;
    }
}
