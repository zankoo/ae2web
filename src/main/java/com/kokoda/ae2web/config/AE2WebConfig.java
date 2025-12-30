package com.kokoda.ae2web.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class AE2WebConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue HTTP_PORT;
    public static final ForgeConfigSpec.ConfigValue<String> BIND_ADDRESS;
    public static final ForgeConfigSpec.ConfigValue<String> AUTH_TOKEN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTH;

    // Prerender settings
    public static final ForgeConfigSpec.BooleanValue PRERENDER_ENABLED;
    public static final ForgeConfigSpec.IntValue PRERENDER_MAX_UPLOAD_SIZE;
    public static final ForgeConfigSpec.IntValue PRERENDER_CACHE_SIZE;
    public static final ForgeConfigSpec.IntValue PRERENDER_AUTO_MAX_ITEMS;

    static {
        BUILDER.push("server");

        HTTP_PORT = BUILDER
                .comment("HTTP server port for web terminal access")
                .defineInRange("httpPort", 8080, 1024, 65535);

        BIND_ADDRESS = BUILDER
                .comment("Bind address for HTTP server (0.0.0.0 for all interfaces, 127.0.0.1 for localhost only)")
                .define("bindAddress", "0.0.0.0");
        
        ENABLE_AUTH = BUILDER
                .comment("Enable token authentication for API access")
                .define("enableAuth", false);
        
        AUTH_TOKEN = BUILDER
                .comment("Authentication token for API access (set your own secure token)")
                .define("authToken", "change-me-to-a-secure-token");

        BUILDER.pop();

        BUILDER.push("prerender");

        PRERENDER_ENABLED = BUILDER
                .comment("Enable client-side pre-rendering of item icons")
                .define("enabled", true);

        PRERENDER_MAX_UPLOAD_SIZE = BUILDER
                .comment("Maximum size in bytes for uploaded PNG icons (default 64KB)")
                .defineInRange("maxUploadSize", 65536, 1024, 262144);

        PRERENDER_CACHE_SIZE = BUILDER
                .comment("Number of pre-rendered icons to cache in memory")
                .defineInRange("cacheSize", 1024, 100, 10000);

        PRERENDER_AUTO_MAX_ITEMS = BUILDER
                .comment("Maximum number of items to auto-render when player joins")
                .defineInRange("autoMaxItems", 500, 10, 5000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "ae2web-server.toml");
    }

    public static int getHttpPort() {
        return HTTP_PORT.get();
    }

    public static String getBindAddress() {
        return BIND_ADDRESS.get();
    }

    public static boolean isAuthEnabled() {
        return ENABLE_AUTH.get();
    }

    public static String getAuthToken() {
        return AUTH_TOKEN.get();
    }

    public static boolean isPrerenderEnabled() {
        return PRERENDER_ENABLED.get();
    }

    public static int getPrerenderMaxUploadSize() {
        return PRERENDER_MAX_UPLOAD_SIZE.get();
    }

    public static int getPrerenderCacheSize() {
        return PRERENDER_CACHE_SIZE.get();
    }

    public static int getPrerenderAutoMaxItems() {
        return PRERENDER_AUTO_MAX_ITEMS.get();
    }
}
