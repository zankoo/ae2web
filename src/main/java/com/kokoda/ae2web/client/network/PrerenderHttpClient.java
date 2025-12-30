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

/**
 * HTTP client for prerender API communication.
 * Runs on background thread to avoid blocking.
 */
@OnlyIn(Dist.CLIENT)
public class PrerenderHttpClient {
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 30000;

    private static PrerenderHttpClient instance;

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
        if (instance == null) {
            instance = new PrerenderHttpClient();
        }
        return instance;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Fetch missing icons from server.
     */
    public CompletableFuture<List<String>> fetchMissingIcons(String serverUrl, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = serverUrl + "/api/prerender/missing?limit=" + limit;
                HttpURLConnection conn = createConnection(url, "GET");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    AE2WebMod.LOGGER.warn("Failed to fetch missing icons: HTTP {}", responseCode);
                    return Collections.emptyList();
                }

                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = GSON.fromJson(response, JsonObject.class);

                List<String> missing = new ArrayList<>();
                JsonArray missingArray = json.getAsJsonArray("missing");
                if (missingArray != null) {
                    missingArray.forEach(e -> missing.add(e.getAsString()));
                }

                return missing;
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to fetch missing icons", e);
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Claim items for rendering (concurrency control).
     */
    public CompletableFuture<List<String>> claimItems(String serverUrl, List<String> itemIds) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = serverUrl + "/api/prerender/claim";
                HttpURLConnection conn = createConnection(url, "POST");
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
                    return Collections.emptyList();
                }

                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = GSON.fromJson(response, JsonObject.class);

                List<String> claimed = new ArrayList<>();
                JsonArray claimedArray = json.getAsJsonArray("claimed");
                if (claimedArray != null) {
                    claimedArray.forEach(e -> claimed.add(e.getAsString()));
                }

                return claimed;
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to claim items", e);
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Upload a pre-rendered icon to server.
     */
    public CompletableFuture<Boolean> uploadIcon(String serverUrl, String itemId, byte[] pngData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = serverUrl + "/api/prerender/upload";
                HttpURLConnection conn = createConnection(url, "POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                JsonObject request = new JsonObject();
                request.addProperty("itemId", itemId);
                request.addProperty("png", Base64.getEncoder().encodeToString(pngData));

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
            }
        }, executor);
    }

    /**
     * Get prerender stats from server.
     */
    public CompletableFuture<Map<String, Integer>> getStats(String serverUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = serverUrl + "/api/prerender/stats";
                HttpURLConnection conn = createConnection(url, "GET");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    return Collections.emptyMap();
                }

                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
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
            }
        }, executor);
    }

    /**
     * Check if server is reachable.
     */
    public CompletableFuture<Boolean> isServerReachable(String serverUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = serverUrl + "/api/status";
                HttpURLConnection conn = createConnection(url, "GET");
                conn.setConnectTimeout(5000);
                conn.connect();
                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
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

    public void shutdown() {
        executor.shutdown();
    }
}
