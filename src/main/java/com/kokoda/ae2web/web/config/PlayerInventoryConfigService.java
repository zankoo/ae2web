package com.kokoda.ae2web.web.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kokoda.ae2web.AE2WebMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerInventoryConfigService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PlayerInventoryConfigService INSTANCE = new PlayerInventoryConfigService(resolveDefaultRoot());

    private final Path rootDir;
    private final Object ioLock = new Object();
    private final Map<UUID, Object> perPlayerLocks = new ConcurrentHashMap<>();

    public record CategoryRule(String name, String tagRegex) {}

    public record InventoryConfig(
            List<String> whitelist,
            List<String> blacklist,
            List<CategoryRule> categories,
            Map<String, Boolean> collapsed
    ) {}

    private record PlayerFile(String uuid, Map<String, InventoryConfig> terminals) {}

    public PlayerInventoryConfigService(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    }

    public static PlayerInventoryConfigService getInstance() {
        return INSTANCE;
    }

    public InventoryConfig getConfig(UUID playerUuid, String terminalId) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid required");
        }
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId required");
        }

        PlayerFile file = readFile(playerUuid);
        if (file == null || file.terminals() == null) {
            return defaultConfig();
        }

        InventoryConfig cfg = file.terminals().get(terminalId);
        return cfg != null ? normalizeConfig(cfg) : defaultConfig();
    }

    public void saveConfig(UUID playerUuid, String terminalId, InventoryConfig config) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid required");
        }
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId required");
        }

        InventoryConfig normalized = normalizeConfig(config);
        Object lock = perPlayerLocks.computeIfAbsent(playerUuid, k -> new Object());

        synchronized (lock) {
            Map<String, InventoryConfig> terminals = new HashMap<>();
            PlayerFile existing = readFile(playerUuid);
            if (existing != null && existing.terminals() != null) {
                terminals.putAll(existing.terminals());
            }
            terminals.put(terminalId, normalized);

            PlayerFile file = new PlayerFile(playerUuid.toString(), terminals);
            writeFile(playerUuid, file);
        }
    }

    private @Nullable PlayerFile readFile(UUID playerUuid) {
        Path path = rootDir.resolve(playerUuid.toString() + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }

        synchronized (ioLock) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, PlayerFile.class);
            } catch (Exception e) {
                AE2WebMod.LOGGER.debug("Failed to read inventory config file: {}", path, e);
                return null;
            }
        }
    }

    private void writeFile(UUID playerUuid, PlayerFile file) {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to create inventory config dir {}", rootDir.toAbsolutePath(), e);
            return;
        }

        Path path = rootDir.resolve(playerUuid.toString() + ".json");
        Path tmp = rootDir.resolve(playerUuid.toString() + ".json.tmp");

        synchronized (ioLock) {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to write inventory config tmp file {}", tmp, e);
                return;
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    AE2WebMod.LOGGER.warn("Failed to persist inventory config file {}", path, ex);
                }
            }
        }
    }

    private static InventoryConfig defaultConfig() {
        return new InventoryConfig(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    private static InventoryConfig normalizeConfig(InventoryConfig raw) {
        if (raw == null) {
            return defaultConfig();
        }

        List<String> whitelist = normalizeStringList(raw.whitelist());
        List<String> blacklist = normalizeStringList(raw.blacklist());

        List<CategoryRule> categories = new ArrayList<>();
        if (raw.categories() != null) {
            for (CategoryRule rule : raw.categories()) {
                if (rule == null) {
                    continue;
                }
                String name = rule.name() != null ? rule.name().trim() : "";
                String tagRegex = rule.tagRegex() != null ? rule.tagRegex().trim() : "";
                if (name.isBlank() || tagRegex.isBlank()) {
                    continue;
                }
                categories.add(new CategoryRule(name, tagRegex));
            }
        }

        Map<String, Boolean> collapsed = new HashMap<>();
        if (raw.collapsed() != null) {
            collapsed.putAll(raw.collapsed());
        }

        return new InventoryConfig(whitelist, blacklist, categories, collapsed);
    }

    private static List<String> normalizeStringList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String trimmed = s.trim();
            if (!trimmed.isBlank()) {
                out.add(trimmed);
            }
        }
        return out.isEmpty() ? Collections.emptyList() : List.copyOf(out);
    }

    private static Path resolveDefaultRoot() {
        String override = System.getProperty("ae2web.inventoryConfig.storageRoot");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("config", "ae2web", "inventory-config");
    }
}

