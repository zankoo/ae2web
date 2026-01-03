package com.kokoda.ae2web.web.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kokoda.ae2web.AE2WebMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerAuthService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final PlayerAuthService INSTANCE = new PlayerAuthService(resolveDefaultRoot());

    private final Path rootDir;
    private final Object initLock = new Object();
    private final Object createLock = new Object();
    private final ConcurrentMap<UUID, String> tokenByUuid = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> uuidByToken = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    public PlayerAuthService(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    }

    public static PlayerAuthService getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (initialized) {
            return;
        }

        synchronized (initLock) {
            if (initialized) {
                return;
            }

            try {
                Files.createDirectories(rootDir);
                loadExistingProfiles();
            } catch (IOException e) {
                AE2WebMod.LOGGER.warn("Failed to initialize PlayerAuthService at {}", rootDir.toAbsolutePath(), e);
            }
            initialized = true;
        }
    }

    public String getOrCreateToken(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid required");
        }
        initialize();

        String existing = tokenByUuid.get(playerUuid);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }

        synchronized (createLock) {
            String again = tokenByUuid.get(playerUuid);
            if (again != null && !again.isBlank()) {
                return again;
            }

            String token = generateUniqueToken();
            persistProfile(playerUuid, token);
            tokenByUuid.put(playerUuid, token);
            uuidByToken.put(token, playerUuid);
            return token;
        }
    }

    public @Nullable UUID authenticate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        initialize();
        return uuidByToken.get(token);
    }

    private void loadExistingProfiles() {
        if (!Files.isDirectory(rootDir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir, "*.json")) {
            for (Path path : stream) {
                if (path == null || !Files.isRegularFile(path)) {
                    continue;
                }

                ProfileFile file;
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    file = GSON.fromJson(reader, ProfileFile.class);
                } catch (Exception e) {
                    AE2WebMod.LOGGER.debug("Failed to read player auth file: {}", path, e);
                    continue;
                }

                if (file == null || file.uuid == null || file.uuid.isBlank() || file.token == null || file.token.isBlank()) {
                    continue;
                }

                UUID uuid;
                try {
                    uuid = UUID.fromString(file.uuid.trim());
                } catch (IllegalArgumentException e) {
                    continue;
                }

                String token = file.token.trim();
                tokenByUuid.putIfAbsent(uuid, token);
                uuidByToken.putIfAbsent(token, uuid);
            }
        } catch (IOException e) {
            AE2WebMod.LOGGER.debug("Failed to scan player auth dir: {}", rootDir, e);
        }
    }

    private void persistProfile(UUID uuid, String token) {
        try {
            Files.createDirectories(rootDir);
            Path path = rootDir.resolve(uuid.toString() + ".json");
            Path tmp = rootDir.resolve(uuid.toString() + ".json.tmp");

            ProfileFile file = new ProfileFile(uuid.toString(), token);
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(file, writer);
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to persist player token for {}", uuid, e);
        }
    }

    private String generateUniqueToken() {
        for (int i = 0; i < 10; i++) {
            String token = generateToken();
            if (!uuidByToken.containsKey(token)) {
                return token;
            }
        }
        return generateToken();
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static Path resolveDefaultRoot() {
        String override = System.getProperty("ae2web.players.storageRoot");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("config", "ae2web", "players");
    }

    private record ProfileFile(String uuid, String token) {}
}

