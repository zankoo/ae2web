package com.kokoda.ae2web.web.prerender;

import com.google.gson.reflect.TypeToken;
import com.kokoda.ae2web.AE2WebMod;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SeenStorageService {
    private static final long MIN_PERSIST_INTERVAL_MS = 30000;

    private final Path seenFile;
    private final Object fileLock;
    private final Map<String, Long> lastSeen;

    private volatile boolean dirty = false;
    private volatile long lastPersistAt = 0;

    SeenStorageService(Path seenFile, Object fileLock) {
        this.seenFile = seenFile;
        this.fileLock = fileLock;
        this.lastSeen = new ConcurrentHashMap<>();
    }

    void load() {
        if (!Files.exists(seenFile)) return;

        try (Reader reader = Files.newBufferedReader(seenFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Long>>() {
            }.getType();
            Map<String, Long> loaded = PrerenderJsonIO.GSON.fromJson(reader, type);
            if (loaded != null) {
                lastSeen.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} last-seen entries", lastSeen.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load last-seen timestamps", e);
        }
    }

    void save(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (!dirty || (now - lastPersistAt) < MIN_PERSIST_INTERVAL_MS)) {
            return;
        }

        Map<String, Long> snapshot = new LinkedHashMap<>(lastSeen);
        synchronized (fileLock) {
            try {
                PrerenderJsonIO.writeJsonAtomic(seenFile, snapshot);
                lastPersistAt = now;
                dirty = false;
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to save last-seen timestamps", e);
            }
        }
    }

    Map<String, Long> getLastSeenMap() {
        return lastSeen;
    }

    void markSeen(Collection<String> iconKeys) {
        if (iconKeys == null || iconKeys.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (String iconKey : iconKeys) {
            if (iconKey == null || iconKey.isBlank()) continue;
            lastSeen.put(iconKey, now);
        }
        dirty = true;
    }

    void ensureSeenIfAbsent(String iconKey, long timestamp) {
        if (iconKey == null || iconKey.isBlank()) return;
        if (lastSeen.putIfAbsent(iconKey, timestamp) == null) {
            dirty = true;
        }
    }

    void retainAll(Set<String> keys) {
        if (keys == null) return;
        if (lastSeen.keySet().retainAll(keys)) {
            dirty = true;
        }
    }

    void remove(String iconKey) {
        if (lastSeen.remove(iconKey) != null) {
            dirty = true;
        }
    }

    void clear() {
        lastSeen.clear();
        dirty = false;
        lastPersistAt = 0;
    }
}

