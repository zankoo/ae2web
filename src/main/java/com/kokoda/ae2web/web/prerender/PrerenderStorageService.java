package com.kokoda.ae2web.web.prerender;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.config.AE2WebConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages storage and retrieval of pre-rendered item icons.
 * Storage location: config/ae2web/prerender/{namespace}/{path}.png
 */
public class PrerenderStorageService {
    private static final PrerenderStorageService INSTANCE = new PrerenderStorageService();

    private static final int DEFAULT_CACHE_SIZE = 1024;
    private static final long LOCK_TTL_MS = 60000; // 1 minute

    private final Path storageRoot;
    private final Map<String, byte[]> iconCache;
    private final Set<String> existenceCache;
    private final Map<String, Long> renderingLocks;
    private final ReentrantReadWriteLock cacheLock;
    private volatile boolean initialized = false;

    private PrerenderStorageService() {
        this.storageRoot = Paths.get("config", "ae2web", "prerender");
        this.iconCache = new LinkedHashMap<>(DEFAULT_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > AE2WebConfig.getPrerenderCacheSize();
            }
        };
        this.existenceCache = ConcurrentHashMap.newKeySet();
        this.renderingLocks = new ConcurrentHashMap<>();
        this.cacheLock = new ReentrantReadWriteLock();
    }

    public static PrerenderStorageService getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the storage directory.
     */
    public void initialize() {
        if (initialized) return;

        try {
            Files.createDirectories(storageRoot);
            scanExistingIcons();
            initialized = true;
            AE2WebMod.LOGGER.info("PrerenderStorageService initialized at {}", storageRoot.toAbsolutePath());
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to initialize prerender storage", e);
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
                try (Stream<Path> files = Files.list(nsDir)) {
                    files.filter(f -> f.toString().endsWith(".png"))
                         .forEach(f -> {
                             String path = f.getFileName().toString();
                             path = path.substring(0, path.length() - 4); // Remove .png
                             existenceCache.add(namespace + ":" + path);
                         });
                } catch (IOException e) {
                    AE2WebMod.LOGGER.warn("Failed to scan namespace directory: {}", nsDir, e);
                }
            });
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to scan prerender storage", e);
        }

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
        // Check memory cache first
        cacheLock.readLock().lock();
        try {
            byte[] cached = iconCache.get(itemId);
            if (cached != null) {
                return cached;
            }
        } finally {
            cacheLock.readLock().unlock();
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

            // Cache the loaded data
            cacheLock.writeLock().lock();
            try {
                iconCache.put(itemId, data);
            } finally {
                cacheLock.writeLock().unlock();
            }

            return data;
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to read prerendered icon: {}", itemId, e);
            return null;
        }
    }

    /**
     * Save a pre-rendered icon.
     */
    public boolean saveIcon(String itemId, byte[] pngData) {
        if (pngData == null || pngData.length == 0) {
            return false;
        }

        // Validate PNG magic bytes
        if (pngData.length < 8 ||
            pngData[0] != (byte) 0x89 ||
            pngData[1] != 0x50 ||
            pngData[2] != 0x4E ||
            pngData[3] != 0x47) {
            AE2WebMod.LOGGER.warn("Invalid PNG data for item: {}", itemId);
            return false;
        }

        // Check size limit
        if (pngData.length > AE2WebConfig.getPrerenderMaxUploadSize()) {
            AE2WebMod.LOGGER.warn("PNG data too large for item: {} ({} bytes)", itemId, pngData.length);
            return false;
        }

        Path filePath = getFilePath(itemId);
        if (filePath == null) {
            return false;
        }

        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, pngData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Update caches
            existenceCache.add(itemId);
            cacheLock.writeLock().lock();
            try {
                iconCache.put(itemId, pngData);
            } finally {
                cacheLock.writeLock().unlock();
            }

            // Release any lock
            renderingLocks.remove(itemId);

            AE2WebMod.LOGGER.debug("Saved prerendered icon: {}", itemId);
            return true;
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to save prerendered icon: {}", itemId, e);
            return false;
        }
    }

    /**
     * Get the file path for an item ID.
     */
    private @Nullable Path getFilePath(String itemId) {
        int colonIdx = itemId.indexOf(':');
        if (colonIdx <= 0 || colonIdx >= itemId.length() - 1) {
            return null;
        }

        String namespace = itemId.substring(0, colonIdx);
        String path = itemId.substring(colonIdx + 1);

        // Sanitize path components
        namespace = sanitizePath(namespace);
        path = sanitizePath(path);

        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }

        return storageRoot.resolve(namespace).resolve(path + ".png");
    }

    /**
     * Sanitize a path component to prevent directory traversal.
     */
    private String sanitizePath(String component) {
        return component.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    /**
     * Get a list of item IDs that don't have pre-rendered icons.
     */
    public List<String> getMissingIcons(@Nullable String namespace, int limit) {
        List<String> missing = new ArrayList<>();

        for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
            if (namespace != null && !itemId.getNamespace().equals(namespace)) {
                continue;
            }

            String id = itemId.toString();
            if (!existenceCache.contains(id)) {
                missing.add(id);
                if (missing.size() >= limit) {
                    break;
                }
            }
        }

        return missing;
    }

    /**
     * Get total counts for statistics.
     */
    public Map<String, Integer> getStats() {
        int totalItems = ForgeRegistries.ITEMS.getKeys().size();
        int renderedCount = existenceCache.size();

        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("totalItems", totalItems);
        stats.put("renderedCount", renderedCount);
        stats.put("missingCount", totalItems - renderedCount);
        stats.put("cacheSize", iconCache.size());
        stats.put("activeLocks", renderingLocks.size());

        return stats;
    }

    /**
     * Claim items for rendering (concurrency control).
     * Returns the subset of items that this client should render.
     */
    public List<String> claimItemsForRendering(List<String> requestedIds, String clientId) {
        long now = System.currentTimeMillis();
        List<String> claimed = new ArrayList<>();

        // Clean expired locks
        renderingLocks.entrySet().removeIf(e -> e.getValue() < now);

        for (String id : requestedIds) {
            // Skip if already rendered
            if (existenceCache.contains(id)) {
                continue;
            }

            // Try to acquire lock
            Long existing = renderingLocks.putIfAbsent(id, now + LOCK_TTL_MS);
            if (existing == null) {
                claimed.add(id);
            }
        }

        if (!claimed.isEmpty()) {
            AE2WebMod.LOGGER.debug("Client {} claimed {} items for rendering", clientId, claimed.size());
        }

        return claimed;
    }

    /**
     * Clear all pre-rendered icons.
     */
    public void clearAll() {
        cacheLock.writeLock().lock();
        try {
            iconCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }

        existenceCache.clear();
        renderingLocks.clear();

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
            AE2WebMod.LOGGER.info("Cleared all prerendered icons");
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
}
