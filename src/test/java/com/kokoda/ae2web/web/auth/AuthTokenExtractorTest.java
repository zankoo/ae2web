package com.kokoda.ae2web.web.auth;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenExtractorTest {
    @Test
    void extractsBearerTokenFromAuthorizationHeader() {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/status");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer abc123");

        assertThat(AuthTokenExtractor.extractToken(req)).isEqualTo("abc123");
    }

    @Test
    void extractsTokenFromQueryParamWhenNoAuthorizationHeader() {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/status?token=xyz");

        assertThat(AuthTokenExtractor.extractToken(req)).isEqualTo("xyz");
    }

    @Test
    void prefersAuthorizationHeaderOverQueryParam() {
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/api/status?token=xyz");
        req.headers().set(HttpHeaderNames.AUTHORIZATION, "Bearer abc123");

        assertThat(AuthTokenExtractor.extractToken(req)).isEqualTo("abc123");
    }
}

