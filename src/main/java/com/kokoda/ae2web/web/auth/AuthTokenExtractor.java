package com.kokoda.ae2web.web.auth;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AuthTokenExtractor {
    private static final String BEARER_PREFIX = "Bearer ";

    private AuthTokenExtractor() {}

    public static @Nullable String extractToken(FullHttpRequest request) {
        if (request == null) {
            return null;
        }

        String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (!token.isBlank()) {
                return token;
            }
        }

        try {
            QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
            List<String> tokens = decoder.parameters().get("token");
            if (tokens != null && !tokens.isEmpty()) {
                String token = tokens.get(0);
                if (token != null && !token.isBlank()) {
                    return token;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }
}

