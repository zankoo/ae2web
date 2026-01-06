package com.kokoda.ae2web.web.prerender;

import com.google.gson.reflect.TypeToken;
import com.kokoda.ae2web.AE2WebMod;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TooltipStorageService {
    private final Path tooltipsFile;
    private final Object fileLock;
    private final Map<String, List<String>> tooltips;

    TooltipStorageService(Path tooltipsFile, Object fileLock) {
        this.tooltipsFile = tooltipsFile;
        this.fileLock = fileLock;
        this.tooltips = new ConcurrentHashMap<>();
    }

    void load() {
        if (!Files.exists(tooltipsFile)) return;

        try (Reader reader = Files.newBufferedReader(tooltipsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, List<String>>>() {
            }.getType();
            Map<String, List<String>> loaded = PrerenderJsonIO.GSON.fromJson(reader, type);
            if (loaded != null) {
                tooltips.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} tooltips", tooltips.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load tooltips", e);
        }
    }

    void save() {
        synchronized (fileLock) {
            try {
                PrerenderJsonIO.writeJsonAtomic(tooltipsFile, tooltips);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to save tooltips", e);
            }
        }
    }

    @Nullable List<String> get(String iconKey) {
        return tooltips.get(iconKey);
    }

    boolean has(String iconKey) {
        return tooltips.containsKey(iconKey);
    }

    void put(String iconKey, List<String> tooltipLines) {
        tooltips.put(iconKey, tooltipLines);
        save();
    }

    boolean remove(String iconKey) {
        return tooltips.remove(iconKey) != null;
    }

    void clear() {
        tooltips.clear();
    }
}

