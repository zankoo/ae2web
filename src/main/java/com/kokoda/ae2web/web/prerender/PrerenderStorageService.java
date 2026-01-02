package com.kokoda.ae2web.web.prerender;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.util.PrerenderUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages storage and retrieval of pre-rendered item icons and translations.
 * Storage location: config/ae2web/prerender/{namespace}/{path}.png
 * Translations stored in: config/ae2web/prerender/translations.json
 * Tooltips stored in: config/ae2web/prerender/tooltips.json
 * Last-seen timestamps stored in: config/ae2web/prerender/seen.json
 */
public class PrerenderStorageService {
    private static final PrerenderStorageService INSTANCE = new PrerenderStorageService();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final long LOCK_TTL_MS = 60000; // 1 minute

    private final Path storageRoot;
    private final Path translationsFile;
    private final Path tooltipsFile;
    private final Path seenFile;
    private final Map<String, byte[]> iconCache;
    private final Object cacheLock = new Object();
    private final Object metaFileLock = new Object();
    private final Object initLock = new Object();
    private final Set<String> existenceCache;
    private final Map<String, LockInfo> renderingLocks;

    private record LockInfo(String clientId, long expireTime) {}

    // iconKey -> translated name (client-side language)
    private final Map<String, String> translations;
    // iconKey -> tooltip lines (legacy formatting may include literal '§')
    private final Map<String, List<String>> tooltips;
    // iconKey -> last time it appeared in ME inventory/craftables (epoch millis)
    private final Map<String, Long> lastSeen;
    private volatile boolean seenDirty = false;
    private volatile long lastSeenPersistAt = 0;
    private volatile ScheduledExecutorService maintenanceExecutor = null;
    private final Object maintenanceLock = new Object();
    private volatile boolean initialized = false;

    private PrerenderStorageService() {
        String rootOverride = System.getProperty("ae2web.prerender.storageRoot");
        this.storageRoot = (rootOverride != null && !rootOverride.isBlank())
                ? Paths.get(rootOverride)
                : Paths.get("config", "ae2web", "prerender");
        this.translationsFile = storageRoot.resolve("translations.json");
        this.tooltipsFile = storageRoot.resolve("tooltips.json");
        this.seenFile = storageRoot.resolve("seen.json");
        // Use LinkedHashMap with accessOrder=true for LRU eviction
        // All access must be synchronized via cacheLock
        this.iconCache = new LinkedHashMap<>(128, 0.75f, true);
        this.existenceCache = ConcurrentHashMap.newKeySet();
        this.renderingLocks = new ConcurrentHashMap<>();
        this.translations = new ConcurrentHashMap<>();
        this.tooltips = new ConcurrentHashMap<>();
        this.lastSeen = new ConcurrentHashMap<>();
    }

    public static PrerenderStorageService getInstance() {
        return INSTANCE;
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
                loadSeen();
                scanExistingIcons();
                loadTranslations();
                loadTooltips();
                initialized = true;
                AE2WebMod.LOGGER.info("PrerenderStorageService initialized at {}", storageRoot.toAbsolutePath());
            } catch (IOException e) {
                AE2WebMod.LOGGER.error("Failed to initialize prerender storage", e);
            }
        }
    }

    /**
     * Load translations from JSON file.
     */
    private void loadTranslations() {
        if (!Files.exists(translationsFile)) return;

        try (Reader reader = Files.newBufferedReader(translationsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                translations.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} translations", translations.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load translations", e);
        }
    }

    /**
     * Save translations to JSON file.
     */
    private void saveTranslations() {
        synchronized (metaFileLock) {
            try {
                writeJsonAtomic(translationsFile, translations);
            } catch (IOException e) {
                AE2WebMod.LOGGER.warn("Failed to save translations", e);
            }
        }
    }

    private void loadTooltips() {
        if (!Files.exists(tooltipsFile)) return;

        try (Reader reader = Files.newBufferedReader(tooltipsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, List<String>>>() {}.getType();
            Map<String, List<String>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                tooltips.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} tooltips", tooltips.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load tooltips", e);
        }
    }

    private void saveTooltips() {
        synchronized (metaFileLock) {
            try {
                writeJsonAtomic(tooltipsFile, tooltips);
            } catch (IOException e) {
                AE2WebMod.LOGGER.warn("Failed to save tooltips", e);
            }
        }
    }

    private void loadSeen() {
        if (!Files.exists(seenFile)) return;

        try (Reader reader = Files.newBufferedReader(seenFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                lastSeen.putAll(loaded);
                AE2WebMod.LOGGER.debug("Loaded {} last-seen entries", lastSeen.size());
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.warn("Failed to load last-seen timestamps", e);
        }
    }

    private void saveSeen(boolean force) {
        long now = System.currentTimeMillis();
        long minIntervalMs = 30000;
        if (!force && (!seenDirty || (now - lastSeenPersistAt) < minIntervalMs)) {
            return;
        }

        Map<String, Long> snapshot = new LinkedHashMap<>(lastSeen);
        synchronized (metaFileLock) {
            try {
                writeJsonAtomic(seenFile, snapshot);
                lastSeenPersistAt = now;
                seenDirty = false;
            } catch (IOException e) {
                AE2WebMod.LOGGER.warn("Failed to save last-seen timestamps", e);
            }
        }
    }

    private static void writeJsonAtomic(Path targetFile, Object value) throws IOException {
        Files.createDirectories(targetFile.getParent());
        Path tmp = targetFile.resolveSibling(targetFile.getFileName() + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(value, writer);
        }

        try {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Scan existing icons and populate the existence cache.
     */
    private void scanExistingIcons() {
        existenceCache.clear();

        if (!Files.exists(storageRoot)) return;

        try (Stream<Path> namespaces = Files.list(storageRoot)) {
            namespaces.filter(Files::isDirectory).forEach(nsDir -> {
                String namespace = nsDir.getFileName().toString();
                try (Stream<Path> files = Files.walk(nsDir)) {
                    files
                            .filter(Files::isRegularFile)
                            .filter(f -> f.toString().endsWith(".png"))
                            .forEach(f -> {
                                Path relative = nsDir.relativize(f);
                                String rel = relative.toString().replace('\\', '/');
                                if (!rel.endsWith(".png")) {
                                    return;
                                }
                                rel = rel.substring(0, rel.length() - 4); // Remove .png
                                String iconKey = namespace + ":" + rel;
                                existenceCache.add(iconKey);

                                if (!lastSeen.containsKey(iconKey)) {
                                    try {
                                        lastSeen.put(iconKey, Files.getLastModifiedTime(f).toMillis());
                                        seenDirty = true;
                                    } catch (IOException ignored) {
                                    }
                                }
                            });
                } catch (IOException e) {
                    AE2WebMod.LOGGER.warn("Failed to scan namespace directory: {}", nsDir, e);
                }
            });
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to scan prerender storage", e);
        }

        // Keep lastSeen small and consistent with existing icon files.
        lastSeen.keySet().retainAll(existenceCache);
        saveSeen(false);
        AE2WebMod.LOGGER.debug("Scanned {} existing prerendered icons", existenceCache.size());
    }

    /**
     * Check if a pre-rendered icon exists for the given item.
     */
    public boolean hasIcon(ResourceLocation itemId) {
        return existenceCache.contains(itemId.toString());
    }

    /**
     * Check if a pre-rendered icon exists for the given item ID string.
     */
    public boolean hasIcon(String itemId) {
        return existenceCache.contains(itemId);
    }

    /**
     * Get the pre-rendered icon for the given item.
     * Returns null if not found.
     */
    public @Nullable byte[] getIcon(ResourceLocation itemId) {
        return getIcon(itemId.toString());
    }

    /**
     * Get the pre-rendered icon for the given item ID string.
     * Returns null if not found.
     */
    public @Nullable byte[] getIcon(String itemId) {
        // Check memory cache first (synchronized for thread safety with LRU)
        synchronized (cacheLock) {
            byte[] cached = iconCache.get(itemId);
            if (cached != null) {
                return cached;
            }
        }

        // Check existence cache
        if (!existenceCache.contains(itemId)) {
            return null;
        }

        // Load from file
        Path filePath = getFilePath(itemId);
        if (filePath == null || !Files.exists(filePath)) {
            existenceCache.remove(itemId);
            return null;
        }

        try {
            byte[] data = Files.readAllBytes(filePath);

            // Cache the loaded data with LRU eviction
            synchronized (cacheLock) {
                iconCache.put(itemId, data);
                evictIfNeeded();
            }

            return data;
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to read prerendered icon: {}", itemId, e);
            return null;
        }
    }

    private void evictIfNeeded() {
        int maxSize = AE2WebConfig.getPrerenderCacheSize();
        while (iconCache.size() > maxSize) {
            Iterator<String> it = iconCache.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else {
                break;
            }
        }
    }

    /**
     * Save a pre-rendered icon with optional translated name + tooltip lines.
     */
    public boolean saveIcon(String iconKey, byte[] pngData, @Nullable String translatedName, @Nullable List<String> tooltipLines) {
        if (pngData == null || pngData.length == 0) {
            return false;
        }

        // Validate PNG magic bytes
        if (pngData.length < 8 ||
            pngData[0] != (byte) 0x89 ||
            pngData[1] != 0x50 ||
            pngData[2] != 0x4E ||
            pngData[3] != 0x47) {
            AE2WebMod.LOGGER.warn("Invalid PNG data for item: {}", iconKey);
            return false;
        }

        // Check size limit
        if (pngData.length > AE2WebConfig.getPrerenderMaxUploadSize()) {
            AE2WebMod.LOGGER.warn("PNG data too large for item: {} ({} bytes)", iconKey, pngData.length);
            return false;
        }

        Path filePath = getFilePath(iconKey);
        if (filePath == null) {
            return false;
        }

        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, pngData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Update caches
            existenceCache.add(iconKey);
            synchronized (cacheLock) {
                iconCache.put(iconKey, pngData);
                evictIfNeeded();
            }

            // Record last-seen on save, so newly rendered icons don't get GC'd immediately.
            lastSeen.put(iconKey, System.currentTimeMillis());
            seenDirty = true;

            // Store translation if provided
            if (translatedName != null && !translatedName.isBlank()) {
                translations.put(iconKey, translatedName);
                saveTranslations();
            }

            if (tooltipLines != null && !tooltipLines.isEmpty()) {
                tooltips.put(iconKey, tooltipLines);
                saveTooltips();
            }

            // Release any lock
            renderingLocks.remove(iconKey);

            AE2WebMod.LOGGER.debug("Saved prerendered icon: {}", iconKey);
            return true;
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to save prerendered icon: {}", iconKey, e);
            return false;
        }
    }

    /**
     * Get the translated name for an item.
     */
    public @Nullable String getTranslation(String itemId) {
        return translations.get(itemId);
    }

    public @Nullable List<String> getTooltip(String iconKey) {
        return tooltips.get(iconKey);
    }

    /**
     * Check if translation exists for an item.
     */
    public boolean hasTranslation(String itemId) {
        return translations.containsKey(itemId);
    }

    public boolean hasTooltip(String iconKey) {
        return tooltips.containsKey(iconKey);
    }

    /**
     * Get the file path for an item/fluid ID with optional NBT hash.
     * Format: {@code namespace:path@nbtHash}.
     *
     * @param iconKey Icon key in format "namespace:path@nbtHash"
     * @return Path to the PNG file, or null if invalid
     */
    private @Nullable Path getFilePath(String iconKey) {
        if (iconKey == null || iconKey.isEmpty()) {
            return null;
        }

        // Parse iconKey format: "namespace:path@nbtHash"
        int atIndex = iconKey.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= iconKey.length() - 1) {
            return null;
        }

        String itemId = iconKey.substring(0, atIndex);
        String nbtHash = iconKey.substring(atIndex + 1);

        // Parse itemId: "namespace:path"
        int colonIdx = itemId.indexOf(':');
        if (colonIdx <= 0 || colonIdx >= itemId.length() - 1) {
            return null;
        }

        String namespace = itemId.substring(0, colonIdx);
        String path = itemId.substring(colonIdx + 1);

        // Sanitize all components
        namespace = sanitizePath(namespace);
        path = sanitizePathWithSlashes(path);
        nbtHash = sanitizePath(nbtHash);

        if (namespace == null || path == null || nbtHash == null) {
            return null;
        }

        // Construct file path: {namespace}/{path}@{nbtHash}.png
        String filename = path + "@" + nbtHash + ".png";
        Path filePath = storageRoot.resolve(namespace).resolve(filename);

        // Security check: ensure resolved path is under storageRoot
        try {
            Path normalizedPath = filePath.toAbsolutePath().normalize();
            Path normalizedRoot = storageRoot.toAbsolutePath().normalize();
            if (!normalizedPath.startsWith(normalizedRoot)) {
                AE2WebMod.LOGGER.warn("Path traversal attempt detected: {}", iconKey);
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        return filePath;
    }

    /**
     * Sanitize a path component to prevent directory traversal.
     * Returns null if the component is invalid.
     */
    private @Nullable String sanitizePath(String component) {
        if (component == null || component.isEmpty()) {
            return null;
        }

        // Reject path traversal attempts
        if (component.contains("..") || component.contains("/") || component.contains("\\")) {
            return null;
        }

        // Only allow alphanumeric, underscore, hyphen, and dot (but not starting with dot)
        String sanitized = component.replaceAll("[^a-zA-Z0-9_.-]", "_");

        // Reject if starts with dot (hidden files)
        if (sanitized.startsWith(".")) {
            return null;
        }

        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * Sanitize a ResourceLocation path that may contain '/' (e.g. "foo/bar_baz").
     * Returns null if the path is invalid.
     */
    private @Nullable String sanitizePathWithSlashes(String component) {
        if (component == null || component.isEmpty()) {
            return null;
        }
        if (component.contains("..") || component.contains("\\")) {
            return null;
        }

        String[] parts = component.split("/");
        if (parts.length == 0) {
            return null;
        }

        StringBuilder out = new StringBuilder(component.length());
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isEmpty()) {
                return null;
            }

            // Sanitize each segment but keep directory structure.
            String sanitized = part.replaceAll("[^a-zA-Z0-9_.-]", "_");
            if (sanitized.isEmpty() || sanitized.startsWith(".")) {
                return null;
            }

            if (i > 0) {
                out.append('/');
            }
            out.append(sanitized);
        }

        return out.toString();
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
            if (!existenceCache.contains(iconKey)) {
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
            if (!existenceCache.contains(iconKey)) {
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
            if (existenceCache.contains(IconKeyUtils.iconKey(AEItemKey.of(item)))) {
                renderedBase++;
            }
        }
        for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
            var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }
            if (existenceCache.contains(IconKeyUtils.iconKey(AEFluidKey.of(fluid)))) {
                renderedBase++;
            }
        }

        int renderedTotal = existenceCache.size();
        int cacheSize;
        synchronized (cacheLock) {
            cacheSize = iconCache.size();
        }

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
        stats.put("activeLocks", renderingLocks.size());

        return stats;
    }

    /**
     * Claim items for rendering (concurrency control).
     * Returns the subset of items that this client should render.
     * Uses atomic compute() to prevent race conditions.
     */
    public ClaimResult claimItemsForRendering(List<String> requestedIds, String clientId) {
        long now = System.currentTimeMillis();
        List<String> claimed = new ArrayList<>();
        int alreadyRendered = 0;
        int lockedBySelf = 0;
        int lockedByOthers = 0;

        // Clean expired locks periodically (not on every call for performance)
        cleanExpiredLocksIfNeeded(now);

        for (String id : requestedIds) {
            // Skip if already rendered
            if (existenceCache.contains(id)) {
                alreadyRendered++;
                continue;
            }

            // Atomic claim using compute() to prevent race conditions
            final int[] result = {0}; // 0=claimed, 1=lockedBySelf, 2=lockedByOthers
            final long lockNow = now;

            renderingLocks.compute(id, (key, existing) -> {
                // Check if lock exists and is not expired
                if (existing != null && existing.expireTime() >= lockNow) {
                    if (existing.clientId().equals(clientId)) {
                        // Already locked by this client - extend lock
                        result[0] = 1; // lockedBySelf
                        return new LockInfo(clientId, lockNow + LOCK_TTL_MS);
                    } else {
                        // Locked by another client
                        result[0] = 2; // lockedByOthers
                        return existing; // keep existing lock
                    }
                } else {
                    // No lock or expired - acquire new lock
                    result[0] = 0; // claimed
                    return new LockInfo(clientId, lockNow + LOCK_TTL_MS);
                }
            });

            switch (result[0]) {
                case 0 -> claimed.add(id);
                case 1 -> { claimed.add(id); lockedBySelf++; }
                case 2 -> lockedByOthers++;
            }
        }

        if (!claimed.isEmpty()) {
            AE2WebMod.LOGGER.debug("Client {} claimed {} items for rendering", clientId, claimed.size());
        }

        return new ClaimResult(claimed, alreadyRendered, lockedBySelf, lockedByOthers);
    }

    private static final long LOCK_CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    private volatile long lastLockCleanup = 0;

    /**
     * Clean expired locks periodically to prevent unbounded growth.
     */
    private void cleanExpiredLocksIfNeeded(long now) {
        if (now - lastLockCleanup > LOCK_CLEANUP_INTERVAL_MS) {
            lastLockCleanup = now;
            renderingLocks.entrySet().removeIf(e -> e.getValue().expireTime() < now);
        }
    }

    public record ClaimResult(List<String> claimed, int alreadyRendered, int lockedBySelf, int lockedByOthers) {
        public boolean hasLockedByOthers() {
            return lockedByOthers > 0;
        }
    }

    /**
     * Clear all pre-rendered icons and translations.
     */
    public void clearAll() {
        synchronized (cacheLock) {
            iconCache.clear();
        }

        existenceCache.clear();
        renderingLocks.clear();
        translations.clear();
        tooltips.clear();
        lastSeen.clear();
        seenDirty = false;

        try {
            if (Files.exists(storageRoot)) {
                try (Stream<Path> walk = Files.walk(storageRoot)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                AE2WebMod.LOGGER.warn("Failed to delete: {}", path);
                            }
                        });
                }
            }
            Files.createDirectories(storageRoot);
            AE2WebMod.LOGGER.info("Cleared all prerendered icons and metadata");
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to clear prerender storage", e);
        }
    }

    /**
     * Check which items from a list are missing.
     */
    public List<String> filterMissing(List<String> itemIds) {
        return itemIds.stream()
                .filter(id -> !existenceCache.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * Get all stored item IDs.
     */
    public Set<String> getAllStoredIds() {
        return Collections.unmodifiableSet(existenceCache);
    }

    /**
     * Mark iconKeys as "seen" (appeared in current ME inventory/craftables).
     * This drives time-based GC for high-churn NBT variants.
     */
    public void markSeen(Collection<String> iconKeys) {
        if (iconKeys == null || iconKeys.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String iconKey : iconKeys) {
            if (iconKey == null || iconKey.isBlank()) {
                continue;
            }
            lastSeen.put(iconKey, now);
        }
        seenDirty = true;
    }

    /**
     * Start periodic maintenance (seen persistence + TTL-based GC).
     * Call on server start; must be paired with {@link #shutdownMaintenance()}.
     */
    public void startMaintenance() {
        if (maintenanceExecutor != null && !maintenanceExecutor.isShutdown()) {
            return;
        }

        synchronized (maintenanceLock) {
            if (maintenanceExecutor != null && !maintenanceExecutor.isShutdown()) {
                return;
            }

            int intervalSeconds = AE2WebConfig.getPrerenderGcIntervalSeconds();
            if (intervalSeconds <= 0) {
                return;
            }

            maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "AE2Web-PrerenderMaintenance");
                t.setDaemon(true);
                return t;
            });

            maintenanceExecutor.scheduleAtFixedRate(() -> {
                try {
                    runMaintenanceOnce();
                } catch (Exception e) {
                    AE2WebMod.LOGGER.warn("Prerender maintenance failed", e);
                }
            }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    public void shutdownMaintenance() {
        ScheduledExecutorService executor = maintenanceExecutor;
        maintenanceExecutor = null;
        if (executor != null) {
            executor.shutdown();
        }
        saveSeen(true);
    }

    private void runMaintenanceOnce() {
        initialize();

        int deleted = gcExpiredIcons();
        if (deleted > 0) {
            AE2WebMod.LOGGER.info("Prerender GC: deleted {} expired NBT-variant icons", deleted);
        }

        saveSeen(false);
    }

    /**
     * Delete icons that have not been seen for TTL seconds.
     * Only deletes non-base variants (NBT variants), to keep baseline icons stable for remote-only users.
     */
    private int gcExpiredIcons() {
        int ttlSeconds = AE2WebConfig.getPrerenderGcTtlSeconds();
        if (ttlSeconds <= 0) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - (ttlSeconds * 1000L);

        List<String> candidates = new ArrayList<>();
        for (var entry : lastSeen.entrySet()) {
            Long ts = entry.getValue();
            if (ts == null || ts <= 0) {
                continue;
            }
            String iconKey = entry.getKey();
            if (iconKey == null || iconKey.isBlank()) {
                continue;
            }
            boolean locked = renderingLocks.containsKey(iconKey);
            boolean baseVariant = isBaseVariant(iconKey);
            if (!PrerenderUtils.shouldGc(ts, cutoff, locked, baseVariant)) {
                continue;
            }

            candidates.add(iconKey);
        }

        if (candidates.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        boolean translationsChanged = false;
        boolean tooltipsChanged = false;

        for (String iconKey : candidates) {
            Long currentSeen = lastSeen.get(iconKey);
            if (currentSeen == null || currentSeen <= 0) {
                continue;
            }
            boolean locked = renderingLocks.containsKey(iconKey);
            boolean baseVariant = isBaseVariant(iconKey);
            if (!PrerenderUtils.shouldGc(currentSeen, cutoff, locked, baseVariant)) {
                continue;
            }

            if (deleteIcon(iconKey)) {
                deleted++;
            }
            if (translations.remove(iconKey) != null) {
                translationsChanged = true;
            }
            if (tooltips.remove(iconKey) != null) {
                tooltipsChanged = true;
            }
            lastSeen.remove(iconKey);
        }

        if (translationsChanged) {
            saveTranslations();
        }
        if (tooltipsChanged) {
            saveTooltips();
        }
        seenDirty = true;
        saveSeen(false);

        return deleted;
    }

    private boolean deleteIcon(String iconKey) {
        existenceCache.remove(iconKey);
        synchronized (cacheLock) {
            iconCache.remove(iconKey);
        }

        Path filePath = getFilePath(iconKey);
        if (filePath == null) {
            return false;
        }

        boolean removed = false;
        try {
            removed = Files.deleteIfExists(filePath);
        } catch (IOException e) {
            AE2WebMod.LOGGER.debug("Failed to delete icon file: {}", filePath, e);
        }

        cleanupEmptyParents(filePath);
        return removed;
    }

    private void cleanupEmptyParents(Path filePath) {
        if (filePath == null) {
            return;
        }

        Path dir = filePath.getParent();
        while (dir != null && !dir.equals(storageRoot)) {
            try (Stream<Path> entries = Files.list(dir)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                return;
            }

            try {
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
                return;
            }

            dir = dir.getParent();
        }
    }

    private boolean isBaseVariant(String iconKey) {
        int at = iconKey.lastIndexOf('@');
        if (at <= 0) {
            return true;
        }

        String idStr = iconKey.substring(0, at);
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            return true;
        }

        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null) {
            String base = IconKeyUtils.iconKey(AEItemKey.of(item));
            return iconKey.equals(base);
        }

        var fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid != null && fluid != Fluids.EMPTY) {
            String base = IconKeyUtils.iconKey(AEFluidKey.of(fluid));
            return iconKey.equals(base);
        }

        return true;
    }
}
