package com.kokoda.ae2web.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerInventoryConfigServiceTest {
    @Test
    void returnsDefaultConfigWhenMissing(@TempDir Path tempDir) {
        PlayerInventoryConfigService service = new PlayerInventoryConfigService(tempDir);
        UUID uuid = UUID.randomUUID();

        PlayerInventoryConfigService.InventoryConfig cfg = service.getConfig(uuid, "t1");

        assertThat(cfg.whitelist()).isEmpty();
        assertThat(cfg.blacklist()).isEmpty();
        assertThat(cfg.categories()).isEmpty();
        assertThat(cfg.collapsed()).isEmpty();
    }

    @Test
    void savesAndLoadsPerTerminalConfig(@TempDir Path tempDir) {
        UUID uuid = UUID.randomUUID();

        PlayerInventoryConfigService service = new PlayerInventoryConfigService(tempDir);
        PlayerInventoryConfigService.InventoryConfig input = new PlayerInventoryConfigService.InventoryConfig(
                List.of("^forge:ores/.*$"),
                List.of("^forge:ores/uranium.*$"),
                List.of(new PlayerInventoryConfigService.CategoryRule("Ores", "^forge:ores/.*$")),
                Map.of("Ores|^forge:ores/.*$", true, "Other", false)
        );

        service.saveConfig(uuid, "terminal-a", input);

        PlayerInventoryConfigService reloaded = new PlayerInventoryConfigService(tempDir);
        PlayerInventoryConfigService.InventoryConfig output = reloaded.getConfig(uuid, "terminal-a");

        assertThat(output).isEqualTo(input);
    }
}

