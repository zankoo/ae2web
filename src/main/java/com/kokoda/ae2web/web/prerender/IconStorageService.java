package com.kokoda.ae2web.web.prerender;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.config.AE2WebConfig;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

final class IconStorageService {
    private final Path storageRoot;
    private final Object cacheLock = new Object();

    // Use LinkedHashMap with accessOrder=true for LRU eviction
    // All access must be synchronized via cacheLock
    private final Map<String, byte[]> iconCache = new LinkedHashMap<>(128, 0.75f, true);
    private final Set<String> existenceCache = ConcurrentHashMap.newKeySet();

    IconStorageService(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    Path getStorageRoot() {
        return storageRoot;
    }

    int getCacheSize() {
        synchronized (cacheLock) {
            return iconCache.size();
        }
    }

    int getExistenceCount() {
        return existenceCache.size();
    }

    Set<String> getAllStoredIds() {
        return Collections.unmodifiableSet(existenceCache);
    }

    void clearMemoryCaches() {
        synchronized (cacheLock) {
            iconCache.clear();
        }
        existenceCache.clear();
    }

    void scanExistingIcons(SeenStorageService seenStorage) {
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
                                if (!rel.endsWith(".png")) return;

                                rel = rel.substring(0, rel.length() - 4); // Remove .png
                                String iconKey = namespace + ":" + rel;
                                existenceCache.add(iconKey);

                                try {
                                    seenStorage.ensureSeenIfAbsent(iconKey, Files.getLastModifiedTime(f).toMillis());
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException e) {
                    AE2WebMod.LOGGER.warn("Failed to scan namespace directory: {}", nsDir, e);
                }
            });
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to scan prerender storage", e);
        }

        seenStorage.retainAll(existenceCache);
        seenStorage.save(false);
        AE2WebMod.LOGGER.debug("Scanned {} existing prerendered icons", existenceCache.size());
    }

    boolean hasIcon(String iconKey) {
        return existenceCache.contains(iconKey);
    }

    @Nullable byte[] getIcon(String iconKey) {
        // Check memory cache first (synchronized for thread safety with LRU)
        synchronized (cacheLock) {
            byte[] cached = iconCache.get(iconKey);
            if (cached != null) {
                return cached;
            }
        }

        if (!existenceCache.contains(iconKey)) {
            return null;
        }

        Path filePath = getFilePath(iconKey);
        if (filePath == null || !Files.exists(filePath)) {
            existenceCache.remove(iconKey);
            return null;
        }

        try {
            byte[] data = Files.readAllBytes(filePath);

            synchronized (cacheLock) {
                iconCache.put(iconKey, data);
                evictIfNeeded();
            }

            return data;
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to read prerendered icon: {}", iconKey, e);
            return null;
        }
    }

    boolean saveIcon(String iconKey, byte[] pngData) {
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

            existenceCache.add(iconKey);
            synchronized (cacheLock) {
                iconCache.put(iconKey, pngData);
                evictIfNeeded();
            }

            AE2WebMod.LOGGER.debug("Saved prerendered icon: {}", iconKey);
            return true;
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to save prerendered icon: {}", iconKey, e);
            return false;
        }
    }

    boolean deleteIcon(String iconKey) {
        existenceCache.remove(iconKey);
        synchronized (cacheLock) {
            iconCache.remove(iconKey);
        }

        Path filePath = getFilePath(iconKey);
        if (filePath == null) {
            return false;
        }

        boolean removed;
        try {
            removed = Files.deleteIfExists(filePath);
        } catch (IOException e) {
            AE2WebMod.LOGGER.debug("Failed to delete icon file: {}", filePath, e);
            removed = false;
        }

        cleanupEmptyParents(filePath);
        return removed;
    }

    void clearAllOnDisk() {
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
        } catch (IOException e) {
            AE2WebMod.LOGGER.error("Failed to clear prerender storage", e);
        }
    }

    private void cleanupEmptyParents(Path filePath) {
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
}
