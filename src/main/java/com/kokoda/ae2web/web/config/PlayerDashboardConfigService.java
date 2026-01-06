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

public final class PlayerDashboardConfigService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PlayerDashboardConfigService INSTANCE = new PlayerDashboardConfigService(resolveDefaultRoot());

    private final Path rootDir;
    private final Object ioLock = new Object();
    private final Map<UUID, Object> perPlayerLocks = new ConcurrentHashMap<>();

    public record WidgetConfig(
            String id,
            String type,
            String title,
            int w,
            int h,
            int x,
            int y,
            Map<String, Object> options
    ) {}

    public record DashboardConfig(List<WidgetConfig> widgets) {}

    private record PlayerFile(String uuid, Map<String, DashboardConfig> terminals) {}

    public PlayerDashboardConfigService(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    }

    public static PlayerDashboardConfigService getInstance() {
        return INSTANCE;
    }

    public DashboardConfig getConfig(UUID playerUuid, String terminalId) {
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

        DashboardConfig cfg = file.terminals().get(terminalId);
        return cfg != null ? normalizeConfig(cfg) : defaultConfig();
    }

    public void saveConfig(UUID playerUuid, String terminalId, DashboardConfig config) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid required");
        }
        if (terminalId == null || terminalId.isBlank()) {
            throw new IllegalArgumentException("terminalId required");
        }

        DashboardConfig normalized = normalizeConfig(config);
        Object lock = perPlayerLocks.computeIfAbsent(playerUuid, k -> new Object());

        synchronized (lock) {
            Map<String, DashboardConfig> terminals = new HashMap<>();
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
                AE2WebMod.LOGGER.debug("Failed to read dashboard config file: {}", path, e);
                return null;
            }
        }
    }

    private void writeFile(UUID playerUuid, PlayerFile file) {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to create dashboard config dir {}", rootDir.toAbsolutePath(), e);
            return;
        }

        Path path = rootDir.resolve(playerUuid.toString() + ".json");
        Path tmp = rootDir.resolve(playerUuid.toString() + ".json.tmp");

        synchronized (ioLock) {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to write dashboard config tmp file {}", tmp, e);
                return;
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    AE2WebMod.LOGGER.warn("Failed to persist dashboard config file {}", path, ex);
                }
            }
        }
    }

    private static DashboardConfig defaultConfig() {
        return new DashboardConfig(Collections.emptyList());
    }

    private static DashboardConfig normalizeConfig(DashboardConfig raw) {
        if (raw == null) {
            return defaultConfig();
        }

        List<WidgetConfig> widgets = new ArrayList<>();
        if (raw.widgets() != null) {
            for (WidgetConfig widget : raw.widgets()) {
                if (widget == null) continue;
                if (widget.id() == null || widget.id().isBlank()) continue;
                if (widget.type() == null || widget.type().isBlank()) continue;

                widgets.add(new WidgetConfig(
                        widget.id().trim(),
                        widget.type().trim(),
                        widget.title() != null ? widget.title().trim() : null,
                        Math.max(1, Math.min(4, widget.w())),
                        Math.max(1, Math.min(4, widget.h())),
                        Math.max(0, widget.x()),
                        Math.max(0, widget.y()),
                        widget.options() != null ? widget.options() : Collections.emptyMap()
                ));
            }
        }

        return new DashboardConfig(widgets.isEmpty() ? Collections.emptyList() : List.copyOf(widgets));
    }

    private static Path resolveDefaultRoot() {
        String override = System.getProperty("ae2web.dashboardConfig.storageRoot");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("config", "ae2web", "dashboard-config");
    }
}
