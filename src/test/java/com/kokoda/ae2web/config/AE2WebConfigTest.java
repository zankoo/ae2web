package com.kokoda.ae2web.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AE2WebConfigTest {
    @Test
    void gettersReturnDefaultsWhenConfigNotLoaded() {
        assertThat(AE2WebConfig.getHttpPort()).isEqualTo(8080);
        assertThat(AE2WebConfig.getBindAddress()).isEqualTo("0.0.0.0");
        assertThat(AE2WebConfig.isAuthEnabled()).isFalse();
        assertThat(AE2WebConfig.getAuthToken()).isEqualTo("change-me-to-a-secure-token");

        assertThat(AE2WebConfig.isPrerenderEnabled()).isTrue();
        assertThat(AE2WebConfig.getPrerenderMaxUploadSize()).isEqualTo(65536);
        assertThat(AE2WebConfig.getPrerenderCacheSize()).isEqualTo(1024);
        assertThat(AE2WebConfig.getPrerenderAutoMaxItems()).isZero();
        assertThat(AE2WebConfig.getPrerenderGcTtlSeconds()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(AE2WebConfig.getPrerenderGcIntervalSeconds()).isEqualTo(300);
        assertThat(AE2WebConfig.getAllowedOrigins()).isEmpty();
    }

    @Test
    void sharedTokenNeverMatchesWhenDefaultOrBlank() {
        String defaultToken = AE2WebConfig.getAuthToken();
        assertThat(AE2WebConfig.matchesSharedToken(defaultToken, defaultToken)).isFalse();
        assertThat(AE2WebConfig.matchesSharedToken("", "")).isFalse();
        assertThat(AE2WebConfig.matchesSharedToken(null, "x")).isFalse();
    }

    @Test
    void sharedTokenMatchesConfiguredValue() {
        assertThat(AE2WebConfig.matchesSharedToken("s3cret", "s3cret")).isTrue();
        assertThat(AE2WebConfig.matchesSharedToken("s3cret", "other")).isFalse();
        assertThat(AE2WebConfig.matchesSharedToken("s3cret", null)).isFalse();
    }
}

