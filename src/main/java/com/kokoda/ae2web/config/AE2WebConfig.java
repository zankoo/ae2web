package com.kokoda.ae2web.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class AE2WebConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
    private static final boolean DEFAULT_ENABLE_AUTH = false;
    private static final String DEFAULT_AUTH_TOKEN = "change-me-to-a-secure-token";
    private static final List<String> DEFAULT_ALLOWED_ORIGINS = List.of();

    private static final boolean DEFAULT_PRERENDER_ENABLED = true;
    private static final int DEFAULT_PRERENDER_MAX_UPLOAD_SIZE = 65536;
    private static final int DEFAULT_PRERENDER_CACHE_SIZE = 1024;
    private static final int DEFAULT_PRERENDER_AUTO_MAX_ITEMS = 0;
    private static final int DEFAULT_PRERENDER_GC_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final int DEFAULT_PRERENDER_GC_INTERVAL_SECONDS = 300;

    public static final ForgeConfigSpec.IntValue HTTP_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS;
    public static final ForgeConfigSpec.ConfigValue<String> AUTH_TOKEN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTH;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_ORIGINS;

    // Prerender settings
    public static final ForgeConfigSpec.BooleanValue PRERENDER_ENABLED;
    public static final ForgeConfigSpec.IntValue PRERENDER_MAX_UPLOAD_SIZE;
    public static final ForgeConfigSpec.IntValue PRERENDER_CACHE_SIZE;
    public static final ForgeConfigSpec.IntValue PRERENDER_AUTO_MAX_ITEMS;
    public static final ForgeConfigSpec.IntValue PRERENDER_GC_TTL_SECONDS;
    public static final ForgeConfigSpec.IntValue PRERENDER_GC_INTERVAL_SECONDS;

    static {
        BUILDER.push("server");

        HTTP_PORT = BUILDER
                .comment("HTTP server port for web terminal access")
                .defineInRange("httpPort", DEFAULT_HTTP_PORT, 1024, 65535);

        BIND_ADDRESS = BUILDER
                .comment("Bind address for HTTP server (0.0.0.0 for all interfaces, 127.0.0.1 for localhost only)")
                .define("bindAddress", DEFAULT_BIND_ADDRESS);
        
        ENABLE_AUTH = BUILDER
                .comment("Enable token authentication for API access")
                .define("enableAuth", DEFAULT_ENABLE_AUTH);
        
        AUTH_TOKEN = BUILDER
                .comment("Shared authentication token for API access. Ignored while left at the default value;",
                        "players can always use their personal token from /ae2web token.")
                .define("authToken", DEFAULT_AUTH_TOKEN);

        ALLOWED_ORIGINS = BUILDER
                .comment("Extra browser origins allowed to call the API cross-origin (e.g. \"https://example.com\").",
                        "Same-origin requests are always allowed. Use \"*\" to allow any origin (not recommended).")
                .defineListAllowEmpty("allowedOrigins", DEFAULT_ALLOWED_ORIGINS, o -> o instanceof String);

        BUILDER.pop();

        BUILDER.push("prerender");

        PRERENDER_ENABLED = BUILDER
                .comment("Enable client-side pre-rendering of item icons")
                .define("enabled", DEFAULT_PRERENDER_ENABLED);

        PRERENDER_MAX_UPLOAD_SIZE = BUILDER
                .comment("Maximum size in bytes for uploaded PNG icons (default 64KB)")
                .defineInRange("maxUploadSize", DEFAULT_PRERENDER_MAX_UPLOAD_SIZE, 1024, 262144);

        PRERENDER_CACHE_SIZE = BUILDER
                .comment("Number of pre-rendered icons to cache in memory")
                .defineInRange("cacheSize", DEFAULT_PRERENDER_CACHE_SIZE, 100, 10000);

        PRERENDER_AUTO_MAX_ITEMS = BUILDER
                .comment("Maximum number of items to auto-render when player joins (0 = unlimited)")
                .defineInRange("autoMaxItems", DEFAULT_PRERENDER_AUTO_MAX_ITEMS, 0, 100000);

        PRERENDER_GC_TTL_SECONDS = BUILDER
                .comment("Delete NBT-variant prerenders that have not been seen for this many seconds (0 = disabled)")
                .defineInRange("gcTtlSeconds", DEFAULT_PRERENDER_GC_TTL_SECONDS, 0, 365 * 24 * 60 * 60);

        PRERENDER_GC_INTERVAL_SECONDS = BUILDER
                .comment("How often to run prerender maintenance (seen persistence + GC) in seconds (0 = disabled)")
                .defineInRange("gcIntervalSeconds", DEFAULT_PRERENDER_GC_INTERVAL_SECONDS, 0, 3600);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    /**
     * Registered as COMMON so the file stays at config/ae2web-server.toml. Clients never read it for
     * connection settings: the server sends those in {@link com.kokoda.ae2web.network.ServerInfoPacket}.
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "ae2web-server.toml");
    }

    public static int getHttpPort() {
        return getOrDefault(HTTP_PORT, DEFAULT_HTTP_PORT);
    }

    public static String getBindAddress() {
        return getOrDefault(BIND_ADDRESS, DEFAULT_BIND_ADDRESS);
    }

    public static boolean isAuthEnabled() {
        return getOrDefault(ENABLE_AUTH, DEFAULT_ENABLE_AUTH);
    }

    public static String getAuthToken() {
        return getOrDefault(AUTH_TOKEN, DEFAULT_AUTH_TOKEN);
    }

    public static List<? extends String> getAllowedOrigins() {
        return getOrDefault(ALLOWED_ORIGINS, DEFAULT_ALLOWED_ORIGINS);
    }

    public static boolean isDefaultAuthToken(String token) {
        return token == null || token.isBlank() || token.equals(DEFAULT_AUTH_TOKEN);
    }

    /**
     * Checks a presented token against the configured shared token. The shipped default and blank
     * values never match, so enabling auth without changing authToken does not leave a public token.
     */
    public static boolean matchesSharedToken(String configured, String presented) {
        if (isDefaultAuthToken(configured) || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isPrerenderEnabled() {
        return getOrDefault(PRERENDER_ENABLED, DEFAULT_PRERENDER_ENABLED);
    }

    public static int getPrerenderMaxUploadSize() {
        return getOrDefault(PRERENDER_MAX_UPLOAD_SIZE, DEFAULT_PRERENDER_MAX_UPLOAD_SIZE);
    }

    public static int getPrerenderCacheSize() {
        return getOrDefault(PRERENDER_CACHE_SIZE, DEFAULT_PRERENDER_CACHE_SIZE);
    }

    public static int getPrerenderAutoMaxItems() {
        return getOrDefault(PRERENDER_AUTO_MAX_ITEMS, DEFAULT_PRERENDER_AUTO_MAX_ITEMS);
    }

    public static int getPrerenderGcTtlSeconds() {
        return getOrDefault(PRERENDER_GC_TTL_SECONDS, DEFAULT_PRERENDER_GC_TTL_SECONDS);
    }

    public static int getPrerenderGcIntervalSeconds() {
        return getOrDefault(PRERENDER_GC_INTERVAL_SECONDS, DEFAULT_PRERENDER_GC_INTERVAL_SECONDS);
    }

    private static <T> T getOrDefault(ForgeConfigSpec.ConfigValue<T> configValue, T defaultValue) {
        try {
            return configValue.get();
        } catch (IllegalStateException e) {
            return defaultValue;
        }
    }
}
