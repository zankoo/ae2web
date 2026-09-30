package com.kokoda.ae2web.web.auth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginPolicyTest {
    private static List<String> hosts(String... values) {
        return Arrays.asList(values);
    }

    @Test
    void missingOriginIsAllowed() {
        assertThat(OriginPolicy.isAllowed(null, hosts("localhost:8080"), List.of())).isTrue();
        assertThat(OriginPolicy.isAllowed("  ", hosts("localhost:8080"), List.of())).isTrue();
    }

    @Test
    void sameOriginIsAllowed() {
        assertThat(OriginPolicy.isAllowed("http://localhost:8080", hosts("localhost:8080"), List.of())).isTrue();
        assertThat(OriginPolicy.isAllowed("http://LocalHost:8080", hosts("localhost:8080"), List.of())).isTrue();
    }

    @Test
    void defaultPortsAreNormalized() {
        assertThat(OriginPolicy.isAllowed("http://example.com", hosts("example.com:80"), List.of())).isTrue();
        assertThat(OriginPolicy.isAllowed("https://example.com", hosts("example.com"), List.of())).isTrue();
        assertThat(OriginPolicy.isAllowed("https://example.com", hosts("example.com:443"), List.of())).isTrue();
    }

    @Test
    void foreignOriginIsRejected() {
        assertThat(OriginPolicy.isAllowed("http://evil.example", hosts("localhost:8080"), List.of())).isFalse();
        assertThat(OriginPolicy.isAllowed("http://localhost:5173", hosts("localhost:8080"), List.of())).isFalse();
    }

    @Test
    void opaqueAndNonHttpOriginsAreRejected() {
        assertThat(OriginPolicy.isAllowed("null", hosts("localhost:8080"), List.of())).isFalse();
        assertThat(OriginPolicy.isAllowed("file://", hosts("localhost:8080"), List.of())).isFalse();
        assertThat(OriginPolicy.isAllowed("chrome-extension://abc", hosts("abc"), List.of())).isFalse();
    }

    @Test
    void forwardedHostMatchesBehindReverseProxy() {
        List<String> h = hosts("127.0.0.1:8080", "ae2.example.com, 10.0.0.1");
        assertThat(OriginPolicy.isAllowed("https://ae2.example.com", h, List.of())).isTrue();
    }

    @Test
    void nullHostsAreIgnored() {
        assertThat(OriginPolicy.isAllowed("http://evil.example", hosts(null, null), List.of())).isFalse();
    }

    @Test
    void configuredOriginsAreAllowed() {
        List<String> allowed = List.of("https://dashboard.example.com/");
        assertThat(OriginPolicy.isAllowed("https://dashboard.example.com", hosts("localhost:8080"), allowed)).isTrue();
        assertThat(OriginPolicy.isAllowed("https://other.example.com", hosts("localhost:8080"), allowed)).isFalse();
    }

    @Test
    void wildcardAllowsAnyOrigin() {
        assertThat(OriginPolicy.isAllowed("http://evil.example", hosts("localhost:8080"), List.of("*"))).isTrue();
    }
}
