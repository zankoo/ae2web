package com.kokoda.ae2web.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for prerender API communication.
 * Runs on background thread to avoid blocking.
 */
@OnlyIn(Dist.CLIENT)
public class PrerenderHttpClient {
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 30000;
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 1000;

    private static volatile PrerenderHttpClient instance;

    private final ExecutorService executor;
    private final String clientId;

    private PrerenderHttpClient() {
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AE2Web-PrerenderClient");
            t.setDaemon(true);
            return t;
        });
        this.clientId = UUID.randomUUID().toString();
    }

    public static PrerenderHttpClient getInstance() {
        PrerenderHttpClient current = instance;
        if (current == null) {
            synchronized (PrerenderHttpClient.class) {
                if (instance == null) {
                    instance = new PrerenderHttpClient();
                }
                current = instance;
            }
        }
        return current;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Missing icon item with optional ItemStack NBT payload (compressed).
     */
    public record MissingIconItem(String iconKey, byte[] stackNbt) {}

    /**
     * Fetch missing icons from server.
     * Retries on 5xx errors and network failures.
     */
    public CompletableFuture<List<MissingIconItem>> fetchMissingIcons(String serverUrl, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String url = serverUrl + "/api/prerender/missing?limit=" + limit;

            for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
                HttpURLConnection conn = null;
                try {
                    conn = createConnection(url, "GET");
                    conn.connect();

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        String response;
                        try (var is = conn.getInputStream()) {
                            response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        JsonObject json = GSON.fromJson(response, JsonObject.class);

                        List<MissingIconItem> missing = new ArrayList<>();
                        JsonArray missingArray = json.getAsJsonArray("missing");
                        if (missingArray != null) {
                            for (var element : missingArray) {
                                if (element.isJsonObject()) {
                                    JsonObject itemObj = element.getAsJsonObject();
                                    String iconKey = itemObj.get("iconKey").getAsString();
                                    if (iconKey != null) {
                                        int atIndex = iconKey.lastIndexOf('@');
                                        String itemIdStr = atIndex > 0 ? iconKey.substring(0, atIndex) : iconKey;
                                        if ("minecraft:air".equals(itemIdStr)) {
                                            continue;
                                        }
                                    }

                                    // Decode ItemStack NBT if present
                                    byte[] stackNbt = null;
                                    if (itemObj.has("stackNbt") && !itemObj.get("stackNbt").isJsonNull()) {
                                        String nbtBase64 = itemObj.get("stackNbt").getAsString();
                                        try {
                                            stackNbt = Base64.getDecoder().decode(nbtBase64);
                                        } catch (IllegalArgumentException e) {
                                            AE2WebMod.LOGGER.warn("Failed to decode stackNbt for {}", iconKey, e);
                                        }
                                    }

                                    missing.add(new MissingIconItem(iconKey, stackNbt));
                                }
                            }
                        }
                        return missing;
                    } else if (responseCode >= 500 && attempt < MAX_RETRIES) {
                        // Server error - retry with backoff
                        AE2WebMod.LOGGER.debug("Server error {}, retrying in {}ms (attempt {})", responseCode, RETRY_DELAY_MS, attempt + 1);
                        try {
                            Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Collections.emptyList();
                        }
                    } else {
                        // 4xx or final 5xx - don't retry
                        AE2WebMod.LOGGER.warn("Failed to fetch missing icons: HTTP {}", responseCode);
                        return Collections.emptyList();
                    }
                } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                    // Network error - retry
                    if (attempt < MAX_RETRIES) {
                        AE2WebMod.LOGGER.debug("Network error, retrying in {}ms (attempt {}): {}", RETRY_DELAY_MS, attempt + 1, e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return Collections.emptyList();
                        }
                    } else {
                        AE2WebMod.LOGGER.warn("Failed to fetch missing icons after {} retries: {}", MAX_RETRIES, e.getMessage());
                        return Collections.emptyList();
                    }
                } catch (Exception e) {
                    AE2WebMod.LOGGER.warn("Failed to fetch missing icons", e);
                    return Collections.emptyList();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            return Collections.emptyList();
        }, executor);
    }

    /**
     * Result of claiming items for rendering.
     */
    public record ClaimResult(List<String> claimed, int alreadyRendered, int lockedBySelf, int lockedByOthers) {
        public boolean hasLockedByOthers() {
            return lockedByOthers > 0;
        }
    }

    /**
     * Claim items for rendering (concurrency control).
     */
    public CompletableFuture<ClaimResult> claimItems(String serverUrl, List<String> itemIds) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl + "/api/prerender/claim";
                conn = createConnection(url, "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                JsonObject request = new JsonObject();
                request.addProperty("clientId", clientId);
                JsonArray ids = new JsonArray();
                itemIds.forEach(ids::add);
                request.add("itemIds", ids);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(request).getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    AE2WebMod.LOGGER.warn("Failed to claim items: HTTP {}", responseCode);
                    return new ClaimResult(Collections.emptyList(), 0, 0, 0);
                }

                String response;
                try (var is = conn.getInputStream()) {
                    response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                JsonObject json = GSON.fromJson(response, JsonObject.class);

                List<String> claimed = new ArrayList<>();
                JsonArray claimedArray = json.getAsJsonArray("claimed");
                if (claimedArray != null) {
                    claimedArray.forEach(e -> claimed.add(e.getAsString()));
                }

                int alreadyRendered = json.has("alreadyRendered") ? json.get("alreadyRendered").getAsInt() : 0;
                int lockedBySelf = json.has("lockedBySelf") ? json.get("lockedBySelf").getAsInt() : 0;
                int lockedByOthers = json.has("lockedByOthers") ? json.get("lockedByOthers").getAsInt() : 0;

                return new ClaimResult(claimed, alreadyRendered, lockedBySelf, lockedByOthers);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to claim items", e);
                return new ClaimResult(Collections.emptyList(), 0, 0, 0);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, executor);
    }

    /**
     * Upload a pre-rendered icon to server.
     */
    public CompletableFuture<Boolean> uploadIcon(String serverUrl, String itemId, byte[] pngData, String name, List<String> tooltip) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl + "/api/prerender/upload";
                conn = createConnection(url, "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                JsonObject request = new JsonObject();
                request.addProperty("itemId", itemId);
                request.addProperty("png", Base64.getEncoder().encodeToString(pngData));
                if (name != null && !name.isBlank()) {
                    request.addProperty("name", name);
                }
                if (tooltip != null && !tooltip.isEmpty()) {
                    JsonArray lines = new JsonArray();
                    tooltip.forEach(lines::add);
                    request.add("tooltip", lines);
                }

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(GSON.toJson(request).getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    AE2WebMod.LOGGER.debug("Uploaded icon: {}", itemId);
                    return true;
                } else {
                    AE2WebMod.LOGGER.warn("Failed to upload icon {}: HTTP {}", itemId, responseCode);
                    return false;
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to upload icon: {}", itemId, e);
                return false;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, executor);
    }

    /**
     * Fetch ALL iconKeys from server (for full re-render).
     */
    public CompletableFuture<List<String>> fetchAllItems(String serverUrl, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl + "/api/prerender/all?limit=" + limit;
                conn = createConnection(url, "GET");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    AE2WebMod.LOGGER.warn("Failed to fetch all items: HTTP {}", responseCode);
                    return Collections.emptyList();
                }

                String response;
                try (var is = conn.getInputStream()) {
                    response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                JsonObject json = GSON.fromJson(response, JsonObject.class);

                List<String> items = new ArrayList<>();
                JsonArray itemsArray = json.getAsJsonArray("items");
                if (itemsArray != null) {
                    itemsArray.forEach(e -> items.add(e.getAsString()));
                }

                return items;
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to fetch all items", e);
                return Collections.emptyList();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, executor);
    }

    /**
     * Get prerender stats from server.
     */
    public CompletableFuture<Map<String, Integer>> getStats(String serverUrl) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl + "/api/prerender/stats";
                conn = createConnection(url, "GET");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    return Collections.emptyMap();
                }

                String response;
                try (var is = conn.getInputStream()) {
                    response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
                JsonObject json = GSON.fromJson(response, JsonObject.class);

                Map<String, Integer> stats = new HashMap<>();
                json.entrySet().forEach(e -> {
                    if (e.getValue().isJsonPrimitive()) {
                        stats.put(e.getKey(), e.getValue().getAsInt());
                    }
                });

                return stats;
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to get stats", e);
                return Collections.emptyMap();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, executor);
    }

    /**
     * Check if server is reachable.
     */
    public CompletableFuture<Boolean> isServerReachable(String serverUrl) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String url = serverUrl + "/api/status";
                conn = createConnection(url, "GET");
                conn.setConnectTimeout(5000);
                conn.connect();
                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }, executor);
    }

    private HttpURLConnection createConnection(String urlString, String method) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "AE2WebTerminal-Client");
        return conn;
    }

    public static void shutdownInstance() {
        PrerenderHttpClient current;
        synchronized (PrerenderHttpClient.class) {
            current = instance;
            instance = null;
        }
        if (current != null) {
            current.shutdown();
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
