package com.kokoda.ae2web.web.assets;

import com.kokoda.ae2web.AE2WebMod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public final class ResourceLocator {
    private final MinecraftAssetIndex minecraftAssets;
    private final ClassLoader classLoader;

    public ResourceLocator() {
        this.minecraftAssets = new MinecraftAssetIndex();
        this.classLoader = ResourceLocator.class.getClassLoader();
    }

    public Optional<InputStream> openAsset(String namespace, String relativePath) {
        if (namespace == null || relativePath == null) {
            return Optional.empty();
        }
        relativePath = sanitizeRelativePath(relativePath);
        if (relativePath == null) {
            return Optional.empty();
        }

        if ("minecraft".equals(namespace)) {
            return minecraftAssets.open("minecraft/" + relativePath);
        }

        String resourcePath = "assets/" + namespace + "/" + relativePath;
        InputStream is = classLoader.getResourceAsStream(resourcePath);
        return Optional.ofNullable(is);
    }

    public boolean assetExists(String namespace, String relativePath) {
        if (namespace == null || relativePath == null) {
            return false;
        }
        relativePath = sanitizeRelativePath(relativePath);
        if (relativePath == null) {
            return false;
        }

        if ("minecraft".equals(namespace)) {
            return minecraftAssets.exists("minecraft/" + relativePath);
        }

        String resourcePath = "assets/" + namespace + "/" + relativePath;
        return classLoader.getResource(resourcePath) != null;
    }

    public Optional<byte[]> readAssetBytes(String namespace, String relativePath) {
        try (InputStream is = openAsset(namespace, relativePath).orElse(null)) {
            if (is == null) {
                return Optional.empty();
            }
            return Optional.of(readAllBytes(is));
        } catch (IOException e) {
            AE2WebMod.LOGGER.debug("Failed to read asset bytes {}/{}", namespace, relativePath, e);
            return Optional.empty();
        }
    }

    private static String sanitizeRelativePath(String path) {
        path = path.replace('\\', '/');
        if (path.startsWith("/") || path.contains("..")) {
            return null;
        }
        return path;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
        return out.toByteArray();
    }
}
