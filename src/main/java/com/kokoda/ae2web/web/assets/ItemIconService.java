package com.kokoda.ae2web.web.assets;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ItemIconService {
    private static final Gson GSON = new Gson();
    private static final int MAX_CACHE_ENTRIES = 2048;

    private final ResourceLocator resources;
    private final Map<String, Optional<byte[]>> cache =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<byte[]>> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    public ItemIconService(ResourceLocator resources) {
        this.resources = resources;
    }

    public Optional<byte[]> getItemIconPng(ResourceLocation itemId) {
        // Check prerendered icons first (highest priority)
        PrerenderStorageService prerenderStorage = PrerenderStorageService.getInstance();
        prerenderStorage.initialize();
        byte[] prerendered = prerenderStorage.getIcon(itemId);
        if (prerendered != null) {
            return Optional.of(prerendered);
        }

        // Fall back to JAR-extracted icons
        String key = itemId.toString();
        synchronized (cache) {
            Optional<byte[]> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        Optional<byte[]> resolved = resolveItemIcon(itemId);
        synchronized (cache) {
            cache.put(key, resolved);
        }
        return resolved;
    }

    private Optional<byte[]> resolveItemIcon(ResourceLocation itemId) {
        String namespace = itemId.getNamespace();
        String path = itemId.getPath();
        AE2WebMod.LOGGER.debug("[ItemIconService] Resolving icon for item: {}", itemId);

        ResourceLocation modelId = new ResourceLocation(namespace, "item/" + path);
        Optional<ResourceLocation> textureId = resolveTextureFromModel(modelId);
        if (textureId.isPresent()) {
            AE2WebMod.LOGGER.debug("[ItemIconService] Found texture from model {}: {}", modelId, textureId.get());
        } else {
            AE2WebMod.LOGGER.debug("[ItemIconService] No model found for {}, trying texture guess", modelId);
            textureId = resolveTextureByGuess(namespace, path);
            if (textureId.isPresent()) {
                AE2WebMod.LOGGER.debug("[ItemIconService] Found texture by guess: {}", textureId.get());
            } else {
                AE2WebMod.LOGGER.debug("[ItemIconService] No texture found for item: {}", itemId);
            }
        }

        Optional<byte[]> result = textureId.flatMap(this::readTexturePng);
        if (result.isPresent()) {
            AE2WebMod.LOGGER.debug("[ItemIconService] Successfully loaded texture PNG for item: {} ({} bytes)", itemId, result.get().length);
        } else if (textureId.isPresent()) {
            AE2WebMod.LOGGER.debug("[ItemIconService] Texture ID found but PNG file not readable: {}", textureId.get());
        }
        return result;
    }

    private Optional<byte[]> readTexturePng(ResourceLocation textureId) {
        String relativePath = "textures/" + textureId.getPath() + ".png";
        return resources.readAssetBytes(textureId.getNamespace(), relativePath);
    }

    private Optional<ResourceLocation> resolveTextureByGuess(String namespace, String path) {
        return tryTexture(namespace, "item/" + path)
                .or(() -> tryTexture(namespace, "block/" + path));
    }

    private Optional<ResourceLocation> tryTexture(String namespace, String texturePath) {
        String relativePath = "textures/" + texturePath + ".png";
        if (!resources.assetExists(namespace, relativePath)) {
            return Optional.empty();
        }
        return Optional.of(new ResourceLocation(namespace, texturePath));
    }

    private Optional<ResourceLocation> resolveTextureFromModel(ResourceLocation modelId) {
        Map<String, String> textures = resolveModelTextures(modelId, 0, Set.of());
        if (textures.isEmpty()) {
            return Optional.empty();
        }

        String textureRef = firstNonNull(
                textures.get("layer0"),
                textures.get("all"),
                textures.get("particle"),
                textures.values().stream().findFirst().orElse(null)
        );
        if (textureRef == null) {
            return Optional.empty();
        }

        textureRef = resolveTextureReference(textureRef, textures);
        ResourceLocation textureId = parseResourceLocation(textureRef, modelId.getNamespace());
        return Optional.ofNullable(textureId);
    }

    private Map<String, String> resolveModelTextures(ResourceLocation modelId, int depth, Set<String> visiting) {
        if (depth > 10) {
            return Map.of();
        }
        String visitKey = modelId.toString();
        if (visiting.contains(visitKey)) {
            return Map.of();
        }

        JsonObject model = readJson(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
        if (model == null) {
            return Map.of();
        }

        Map<String, String> textures = new LinkedHashMap<>();
        String parentRaw = getString(model, "parent");
        if (parentRaw != null) {
            ResourceLocation parentId = parseResourceLocation(parentRaw, modelId.getNamespace());
            if (parentId != null) {
                var nextVisiting = new java.util.HashSet<String>(visiting);
                nextVisiting.add(visitKey);
                textures.putAll(resolveModelTextures(parentId, depth + 1, nextVisiting));
            }
        }

        JsonObject texturesObj = model.getAsJsonObject("textures");
        if (texturesObj != null) {
            for (Map.Entry<String, JsonElement> entry : texturesObj.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    textures.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        return textures;
    }

    private static String resolveTextureReference(String ref, Map<String, String> textures) {
        String current = ref;
        for (int i = 0; i < 10; i++) {
            if (!current.startsWith("#")) {
                return current;
            }
            String key = current.substring(1);
            String next = textures.get(key);
            if (next == null) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private @Nullable JsonObject readJson(String namespace, String relativePath) {
        try (var is = resources.openAsset(namespace, relativePath).orElse(null)) {
            if (is == null) {
                return null;
            }
            try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("Failed to read json {}/{}", namespace, relativePath, e);
            return null;
        }
    }

    private static @Nullable String getString(JsonObject obj, String key) {
        if (obj == null) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return null;
        }
        return el.getAsString();
    }

    private static @Nullable ResourceLocation parseResourceLocation(String raw, String defaultNamespace) {
        raw = raw.trim();
        if (raw.isEmpty()) {
            return null;
        }
        raw = raw.toLowerCase(Locale.ROOT);
        if (!raw.contains(":")) {
            raw = defaultNamespace + ":" + raw;
        }
        return ResourceLocation.tryParse(raw);
    }

    private static @Nullable String firstNonNull(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
