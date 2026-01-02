package com.kokoda.ae2web.util;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PrerenderUtilsTest {
    @Test
    void hasMore_isFalseForEqualLimit() {
        assertThat(PrerenderUtils.hasMore(0, 1)).isFalse();
        assertThat(PrerenderUtils.hasMore(5, 5)).isFalse();
    }

    @Test
    void hasMore_isTrueOnlyWhenMoreThanLimit() {
        assertThat(PrerenderUtils.hasMore(6, 5)).isTrue();
    }

    @Test
    void shouldGc_onlyWhenOldAndNotLockedAndNotBase() {
        assertThat(PrerenderUtils.shouldGc(0L, 100L, false, false)).isFalse();
        assertThat(PrerenderUtils.shouldGc(100L, 100L, false, false)).isFalse();
        assertThat(PrerenderUtils.shouldGc(101L, 100L, false, false)).isFalse();
        assertThat(PrerenderUtils.shouldGc(99L, 100L, true, false)).isFalse();
        assertThat(PrerenderUtils.shouldGc(99L, 100L, false, true)).isFalse();
        assertThat(PrerenderUtils.shouldGc(99L, 100L, false, false)).isTrue();
    }

    @Test
    void applyBearerAuth_setsAuthorizationHeader() {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        PrerenderUtils.applyBearerAuth(conn, "abc");
        verify(conn).setRequestProperty("Authorization", "Bearer abc");
    }

    @Test
    void applyBearerAuth_doesNothingForBlankToken() {
        HttpURLConnection conn = mock(HttpURLConnection.class);
        PrerenderUtils.applyBearerAuth(conn, " ");
        verifyNoInteractions(conn);
    }

    @Test
    void applyBearerAuth_doesNotThrowOnNullConnection() {
        assertThatCode(() -> PrerenderUtils.applyBearerAuth(null, "abc"))
                .doesNotThrowAnyException();
    }
}
