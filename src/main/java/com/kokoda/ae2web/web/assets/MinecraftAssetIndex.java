package com.kokoda.ae2web.web.assets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class MinecraftAssetIndex {
    private static final Gson GSON = new Gson();

    private final Path assetsDir;
    private final Path objectsDir;
    private final Path indexFile;
    private volatile Map<String, String> objectHashes;

    public MinecraftAssetIndex() {
        this.assetsDir = FMLPaths.GAMEDIR.get().resolve("assets");
        this.objectsDir = assetsDir.resolve("objects");
        this.indexFile = pickIndexFile(assetsDir.resolve("indexes")).orElse(null);
        this.objectHashes = null;
    }

    public Optional<InputStream> open(String assetKey) {
        if (indexFile == null) {
            return Optional.empty();
        }

        var hashes = ensureLoaded();
        String hash = hashes.get(assetKey);
        if (hash == null || hash.length() < 2) {
            return Optional.empty();
        }

        Path objectPath = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
        if (!Files.isRegularFile(objectPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.newInputStream(objectPath));
        } catch (IOException e) {
            AE2WebMod.LOGGER.debug("Failed to read asset object {} at {}", assetKey, objectPath, e);
            return Optional.empty();
        }
    }

    public boolean exists(String assetKey) {
        if (indexFile == null) {
            return false;
        }

        var hashes = ensureLoaded();
        String hash = hashes.get(assetKey);
        if (hash == null || hash.length() < 2) {
            return false;
        }

        Path objectPath = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
        return Files.isRegularFile(objectPath);
    }

    private Map<String, String> ensureLoaded() {
        var existing = objectHashes;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (objectHashes != null) {
                return objectHashes;
            }

            Map<String, String> hashes = new HashMap<>();
            try (InputStream is = Files.newInputStream(indexFile);
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                JsonObject objects = root != null ? root.getAsJsonObject("objects") : null;
                if (objects != null) {
                    for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
                        JsonObject obj = entry.getValue().getAsJsonObject();
                        if (obj == null || !obj.has("hash")) {
                            continue;
                        }
                        hashes.put(entry.getKey(), obj.get("hash").getAsString());
                    }
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to load Minecraft assets index {}", indexFile, e);
            }

            objectHashes = hashes;
            return hashes;
        }
    }

    private static Optional<Path> pickIndexFile(Path indexesDir) {
        if (!Files.isDirectory(indexesDir)) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.list(indexesDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparingLong(MinecraftAssetIndex::safeLastModified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
