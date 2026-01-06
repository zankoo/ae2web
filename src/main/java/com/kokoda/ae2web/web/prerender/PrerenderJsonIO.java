package com.kokoda.ae2web.web.prerender;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class PrerenderJsonIO {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PrerenderJsonIO() {
    }

    static void writeJsonAtomic(Path targetFile, Object value) throws IOException {
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
}

