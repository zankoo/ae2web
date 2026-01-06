package com.kokoda.ae2web.web.prerender;

import com.google.gson.reflect.TypeToken;
import com.kokoda.ae2web.AE2WebMod;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class TranslationStorageService {
    private final Path translationsFile;
    private final Object fileLock;
    private final Map<String, String> translations;

    TranslationStorageService(Path translationsFile, Object fileLock) {
        this.translationsFile = translationsFile;
        this.fileLock = fileLock;
        this.translations = new ConcurrentHashMap<>();
    }

    void load() {
        if (!Files.exists(translationsFile)) return;

        try (Reader reader = Files.newBufferedReader(translationsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> loaded = PrerenderJsonIO.GSON.fromJson(reader, type);
            if (loaded != null) {
                translations.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} translations", translations.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load translations", e);
        }
    }

    void save() {
        synchronized (fileLock) {
            try {
                PrerenderJsonIO.writeJsonAtomic(translationsFile, translations);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to save translations", e);
            }
        }
    }

    @Nullable String get(String iconKey) {
        return translations.get(iconKey);
    }

    boolean has(String iconKey) {
        return translations.containsKey(iconKey);
    }

    void put(String iconKey, String translatedName) {
        translations.put(iconKey, translatedName);
        save();
    }

    boolean remove(String iconKey) {
        return translations.remove(iconKey) != null;
    }

    int size() {
        return translations.size();
    }

    void clear() {
        translations.clear();
    }
}

