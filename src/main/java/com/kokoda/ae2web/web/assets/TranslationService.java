package com.kokoda.ae2web.web.assets;

import appeng.api.stacks.AEItemKey;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IllegalFormatException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TranslationService {
    private static final Gson GSON = new Gson();
    // Known mod namespaces that provide translations - searched in order as fallback
    private static final String[] KNOWN_NAMESPACES = {"gtceu", "minecraft", "ae2", "ae2web"};
    // GTCEu-specific translation key prefixes that don't follow type.namespace.path pattern
    private static final Set<String> GTCEU_PREFIXES = Set.of(
            "tagprefix", "material", "ore", "config", "behavior", "cover",
            "recipe", "metaitem", "multiblock", "terminal", "compass"
    );

    private final ResourceLocator resources;
    private final ConcurrentHashMap<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private volatile @Nullable String defaultLang;

    public TranslationService(ResourceLocator resources) {
        this.resources = resources;
    }

    public String translateItemDisplayName(AEItemKey itemKey, @Nullable String requestedLang) {
        String lang = normalizeLang(requestedLang).orElseGet(this::getDefaultLang);

        var stack = itemKey.toStack();
        if (stack.hasCustomHoverName()) {
            return stack.getHoverName().getString();
        }

        Component displayName = itemKey.getDisplayName();
        String translated = translateComponent(displayName, lang);
        if (translated != null && !translated.isBlank()) {
            return translated;
        }

        String fallbackLang = "en_us";
        if (!fallbackLang.equals(lang)) {
            translated = translateComponent(displayName, fallbackLang);
            if (translated != null && !translated.isBlank()) {
                return translated;
            }
        }

        return displayName.getString();
    }

    public @Nullable String translateKey(String namespace, String lang, String key) {
        Map<String, String> map = loadLang(namespace, lang);
        return map.get(key);
    }

    public String getDefaultLang() {
        String lang = defaultLang;
        if (lang != null) {
            return lang;
        }

        synchronized (this) {
            if (defaultLang != null) {
                return defaultLang;
            }
            defaultLang = readOptionsLang().orElse("en_us");
            return defaultLang;
        }
    }

    public static Optional<String> normalizeLang(@Nullable String lang) {
        if (lang == null) {
            return Optional.empty();
        }
        lang = lang.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (lang.isEmpty() || lang.length() > 32) {
            return Optional.empty();
        }
        if (!lang.matches("^[a-z0-9_]+$")) {
            return Optional.empty();
        }
        return Optional.of(lang);
    }

    private Map<String, String> loadLang(String namespace, String lang) {
        String cacheKey = namespace + ":" + lang;
        return cache.computeIfAbsent(cacheKey, ignored -> {
            String langPath = namespace + "/lang/" + lang + ".json";
            AE2WebMod.LOGGER.debug("[TranslationService] Attempting to load language file: {}", langPath);
            try (InputStream is = openLangStream(namespace, lang).orElse(null)) {
                if (is == null) {
                    AE2WebMod.LOGGER.debug("[TranslationService] Language file not found: {}", langPath);
                    return Collections.emptyMap();
                }
                try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject root = GSON.fromJson(reader, JsonObject.class);
                    if (root == null) {
                        AE2WebMod.LOGGER.debug("[TranslationService] Language file empty or invalid JSON: {}", langPath);
                        return Collections.emptyMap();
                    }
                    Map<String, String> map = new HashMap<>(root.size());
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            map.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                    AE2WebMod.LOGGER.debug("[TranslationService] Successfully loaded {} translation keys from: {}", map.size(), langPath);
                    return map;
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.debug("[TranslationService] Failed to load language file: {} - {}", langPath, e.getMessage());
                AE2WebMod.LOGGER.debug("Failed to load lang {}/lang/{}.json", namespace, lang, e);
                return Collections.emptyMap();
            }
        });
    }

    private Optional<InputStream> openLangStream(String namespace, String lang) {
        return resources.openAsset(namespace, "lang/" + lang + ".json");
    }

    private @Nullable String translateComponent(Component component, String lang) {
        ComponentContents contents = component.getContents();
        if (contents instanceof LiteralContents literal) {
            StringBuilder out = new StringBuilder(literal.text());
            for (Component sibling : component.getSiblings()) {
                String sib = translateComponent(sibling, lang);
                if (sib != null) {
                    out.append(sib);
                }
            }
            return out.toString();
        }

        if (contents instanceof TranslatableContents translatable) {
            StringBuilder out = new StringBuilder(translateTranslatable(translatable, lang));
            for (Component sibling : component.getSiblings()) {
                String sib = translateComponent(sibling, lang);
                if (sib != null) {
                    out.append(sib);
                }
            }
            return out.toString();
        }

        return component.getString();
    }

    private String translateTranslatable(TranslatableContents translatable, String lang) {
        String key = translatable.getKey();
        String namespace = inferNamespaceFromTranslationKey(key);

        String template = translateKey(namespace, lang, key);
        // Fallback: search all known namespaces if not found in inferred namespace
        if (template == null) {
            template = searchAllNamespaces(key, lang, namespace);
        }
        if (template == null) {
            template = translatable.getFallback();
        }
        if (template == null) {
            template = key;
        }

        Object[] args = translatable.getArgs();
        Object[] formattedArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            formattedArgs[i] = formatArg(args[i], lang);
        }

        return safeFormat(template, formattedArgs);
    }

    private @Nullable String searchAllNamespaces(String key, String lang, String skipNamespace) {
        for (String ns : KNOWN_NAMESPACES) {
            if (ns.equals(skipNamespace)) {
                continue;
            }
            String result = translateKey(ns, lang, key);
            if (result != null) {
                AE2WebMod.LOGGER.debug("Found translation for key '{}' in fallback namespace '{}'", key, ns);
                return result;
            }
        }
        return null;
    }

    private String formatArg(Object arg, String lang) {
        if (arg instanceof Component c) {
            return Optional.ofNullable(translateComponent(c, lang)).orElse(c.getString());
        }
        return String.valueOf(arg);
    }

    private static String inferNamespaceFromTranslationKey(String key) {
        if (key == null) {
            return "minecraft";
        }
        String[] parts = key.split("\\.");
        if (parts.length == 0) {
            return "minecraft";
        }
        // Handle GTCEu-specific prefixes (e.g., "tagprefix.dust", "material.gtceu.xxx")
        String firstPart = parts[0];
        if (GTCEU_PREFIXES.contains(firstPart)) {
            return "gtceu";
        }
        // Standard Minecraft format: type.namespace.path (e.g., "item.gtceu.dust")
        if (parts.length >= 3) {
            return switch (firstPart) {
                case "item", "block", "effect", "entity", "fluid", "enchantment" -> parts[1];
                default -> "minecraft";
            };
        }
        return "minecraft";
    }

    private static String safeFormat(String template, Object[] args) {
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (IllegalFormatException e) {
            return template;
        }
    }

    private static Optional<String> readOptionsLang() {
        Path options = FMLPaths.GAMEDIR.get().resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            return Optional.empty();
        }
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("lang:")) {
                    return normalizeLang(line.substring("lang:".length()).trim());
                }
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("Failed to read options.txt for lang", e);
        }
        return Optional.empty();
    }
}
