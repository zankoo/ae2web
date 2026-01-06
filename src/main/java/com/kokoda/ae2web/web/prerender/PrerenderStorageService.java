package com.kokoda.ae2web.web.prerender;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.util.IconKeyUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages storage and retrieval of pre-rendered item icons and translations.
 * Storage location: config/ae2web/prerender/{namespace}/{path}.png
 * Translations stored in: config/ae2web/prerender/translations.json
 * Tooltips stored in: config/ae2web/prerender/tooltips.json
 * Last-seen timestamps stored in: config/ae2web/prerender/seen.json
 */
public class PrerenderStorageService {
    private static final PrerenderStorageService INSTANCE = new PrerenderStorageService();

    private final Object initLock = new Object();
    private volatile boolean initialized = false;

    private final Path storageRoot;

    private final IconStorageService icons;
    private final TranslationStorageService translations;
    private final TooltipStorageService tooltips;
    private final SeenStorageService seen;
    private final PrerenderLockManager locks;
    private final PrerenderGarbageCollector gc;

    public static PrerenderStorageService getInstance() {
        return INSTANCE;
    }

    private PrerenderStorageService() {
        String rootOverride = System.getProperty("ae2web.prerender.storageRoot");
        this.storageRoot = (rootOverride != null && !rootOverride.isBlank())
                ? Paths.get(rootOverride)
                : Paths.get("config", "ae2web", "prerender");

        Path translationsFile = storageRoot.resolve("translations.json");
        Path tooltipsFile = storageRoot.resolve("tooltips.json");
        Path seenFile = storageRoot.resolve("seen.json");

        Object metaFileLock = new Object();
        this.icons = new IconStorageService(storageRoot);
        this.translations = new TranslationStorageService(translationsFile, metaFileLock);
        this.tooltips = new TooltipStorageService(tooltipsFile, metaFileLock);
        this.seen = new SeenStorageService(seenFile, metaFileLock);
        this.locks = new PrerenderLockManager();
        this.gc = new PrerenderGarbageCollector(seen, icons, translations, tooltips, locks, this::initialize);
    }

    /**
     * Initialize the storage directory.
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        synchronized (initLock) {
            if (initialized) {
                return;
            }

            try {
                Files.createDirectories(storageRoot);
                seen.load();
                icons.scanExistingIcons(seen);
                translations.load();
                tooltips.load();

                initialized = true;
                AE2WebMod.LOGGER.info("PrerenderStorageService initialized at {}", storageRoot.toAbsolutePath());
            } catch (IOException e) {
                AE2WebMod.LOGGER.error("Failed to initialize prerender storage", e);
            }
        }
    }

    public boolean hasIcon(ResourceLocation itemId) {
        return icons.hasIcon(itemId.toString());
    }

    public boolean hasIcon(String itemId) {
        return icons.hasIcon(itemId);
    }

    public @Nullable byte[] getIcon(ResourceLocation itemId) {
        return getIcon(itemId.toString());
    }

    public @Nullable byte[] getIcon(String itemId) {
        return icons.getIcon(itemId);
    }

    /**
     * Save a pre-rendered icon with optional translated name + tooltip lines.
     */
    public boolean saveIcon(String iconKey, byte[] pngData, @Nullable String translatedName, @Nullable List<String> tooltipLines) {
        boolean saved = icons.saveIcon(iconKey, pngData);
        if (!saved) {
            return false;
        }

        // Record last-seen on save, so newly rendered icons don't get GC'd immediately.
        seen.markSeen(List.of(iconKey));

        if (translatedName != null && !translatedName.isBlank()) {
            translations.put(iconKey, translatedName);
        }

        if (tooltipLines != null && !tooltipLines.isEmpty()) {
            tooltips.put(iconKey, tooltipLines);
        }

        locks.releaseLock(iconKey);
        return true;
    }

    public @Nullable String getTranslation(String itemId) {
        return translations.get(itemId);
    }

    public @Nullable List<String> getTooltip(String iconKey) {
        return tooltips.get(iconKey);
    }

    public boolean hasTranslation(String itemId) {
        return translations.has(itemId);
    }

    public boolean hasTooltip(String iconKey) {
        return tooltips.has(iconKey);
    }

    /**
     * Get a list of item/fluid IDs that don't have pre-rendered icons.
     */
    public List<String> getMissingIcons(@Nullable String namespace, int limit) {
        List<String> missing = new ArrayList<>();

        // Check items
        for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
            if (namespace != null && !itemId.getNamespace().equals(namespace)) {
                continue;
            }

            var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                continue;
            }

            String iconKey = IconKeyUtils.iconKey(AEItemKey.of(item));
            if (!icons.hasIcon(iconKey)) {
                missing.add(iconKey);
                if (missing.size() >= limit) {
                    return missing;
                }
            }
        }

        // Check fluids
        for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
            if (namespace != null && !fluidId.getNamespace().equals(namespace)) {
                continue;
            }

            // Skip empty fluid
            var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }

            String iconKey = IconKeyUtils.iconKey(AEFluidKey.of(fluid));
            if (!icons.hasIcon(iconKey)) {
                missing.add(iconKey);
                if (missing.size() >= limit) {
                    return missing;
                }
            }
        }

        return missing;
    }

    /**
     * Get total counts for statistics.
     */
    public Map<String, Integer> getStats() {
        int totalItems = 0;
        for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
            if ("minecraft:air".equals(itemId.toString())) {
                continue;
            }
            totalItems++;
        }

        // Count fluids (excluding EMPTY)
        int totalFluids = 0;
        for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                totalFluids++;
            }
        }

        int totalAll = totalItems + totalFluids;

        // Base icons are keyed by AEItemKey.of(item) / AEFluidKey.of(fluid) without NBT.
        // existenceCache also contains NBT variants; track both for clarity.
        int renderedBase = 0;
        for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
            if ("minecraft:air".equals(itemId.toString())) {
                continue;
            }
            var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                continue;
            }
            if (icons.hasIcon(IconKeyUtils.iconKey(AEItemKey.of(item)))) {
                renderedBase++;
            }
        }
        for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }
            if (icons.hasIcon(IconKeyUtils.iconKey(AEFluidKey.of(fluid)))) {
                renderedBase++;
            }
        }

        int renderedTotal = icons.getExistenceCount();
        int cacheSize = icons.getCacheSize();

        int missingBase = Math.max(0, totalAll - renderedBase);
        int nbtVariantCount = Math.max(0, renderedTotal - renderedBase);

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("totalItems", totalAll);
        stats.put("itemCount", totalItems);
        stats.put("fluidCount", totalFluids);
        stats.put("renderedCount", renderedBase);
        stats.put("missingCount", missingBase);
        stats.put("renderedTotalCount", renderedTotal);
        stats.put("nbtVariantCount", nbtVariantCount);
        stats.put("translationCount", translations.size());
        stats.put("cacheSize", cacheSize);
        stats.put("activeLocks", locks.activeLocks());

        return stats;
    }

    /**
     * Claim items for rendering (concurrency control).
     * Returns the subset of items that this client should render.
     */
    public ClaimResult claimItemsForRendering(List<String> requestedIds, String clientId) {
        return locks.claimItemsForRendering(requestedIds, clientId, icons);
    }

    public record ClaimResult(List<String> claimed, int alreadyRendered, int lockedBySelf, int lockedByOthers) {
        public boolean hasLockedByOthers() {
            return lockedByOthers > 0;
        }
    }

    /**
     * Clear all pre-rendered icons and metadata.
     */
    public void clearAll() {
        icons.clearMemoryCaches();
        locks.clear();
        translations.clear();
        tooltips.clear();
        seen.clear();

        icons.clearAllOnDisk();
        AE2WebMod.LOGGER.info("Cleared all prerendered icons and metadata");
    }

    public List<String> filterMissing(List<String> itemIds) {
        return itemIds.stream()
                .filter(id -> !icons.hasIcon(id))
                .toList();
    }

    public Set<String> getAllStoredIds() {
        return icons.getAllStoredIds();
    }

    /**
     * Mark iconKeys as "seen" (appeared in current ME inventory/craftables).
     * This drives time-based GC for high-churn NBT variants.
     */
    public void markSeen(Collection<String> iconKeys) {
        seen.markSeen(iconKeys);
    }

    /**
     * Start periodic maintenance (seen persistence + TTL-based GC).
     * Call on server start; must be paired with {@link #shutdownMaintenance()}.
     */
    public void startMaintenance() {
        gc.startMaintenance();
    }

    public void shutdownMaintenance() {
        gc.shutdownMaintenance();
    }
}

