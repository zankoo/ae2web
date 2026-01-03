package com.kokoda.ae2web.web.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerAuthServiceTest {

    @Test
    void getOrCreateTokenReturnsStableTokenForSameUuid(@TempDir Path tempDir) {
        PlayerAuthService service = new PlayerAuthService(tempDir);
        service.initialize();

        UUID uuid = UUID.randomUUID();

        String token1 = service.getOrCreateToken(uuid);
        String token2 = service.getOrCreateToken(uuid);

        assertThat(token1).isNotBlank();
        assertThat(token2).isEqualTo(token1);
    }

    @Test
    void authenticateReturnsUuidAfterReload(@TempDir Path tempDir) {
        UUID uuid = UUID.randomUUID();

        PlayerAuthService service = new PlayerAuthService(tempDir);
        service.initialize();
        String token = service.getOrCreateToken(uuid);

        PlayerAuthService reloaded = new PlayerAuthService(tempDir);
        reloaded.initialize();

        assertThat(reloaded.authenticate(token)).isEqualTo(uuid);
        assertThat(reloaded.authenticate("invalid-token")).isNull();
    }
}

