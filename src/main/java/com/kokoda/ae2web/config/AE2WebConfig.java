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
}
