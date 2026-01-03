package com.kokoda.ae2web.web.handlers.crafting;

import com.google.gson.JsonObject;

import java.util.Locale;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.GSON;

public final class CraftRequestParser {
    private static final long MAX_CRAFT_AMOUNT = 1_000_000;
    private static final long MILLI_BUCKETS_PER_BUCKET = 1_000;

    private CraftRequestParser() {}

    public enum CraftKeyType { ITEM, FLUID }

    public record CraftRequest(CraftKeyType keyType, String id, long amount, String cpuName, String terminalId) {}

    public static CraftRequest parse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Body required");
        }

        JsonObject json;
        try {
            json = GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }

        if (json == null) {
            throw new IllegalArgumentException("Invalid JSON");
        }

        if (!json.has("type") || json.get("type").isJsonNull()) {
            throw new IllegalArgumentException("type required");
        }
        String rawType = json.get("type").getAsString();
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("type required");
        }
        CraftKeyType keyType = switch (rawType.trim().toLowerCase(Locale.ROOT)) {
            case "item", "items" -> CraftKeyType.ITEM;
            case "fluid", "fluids" -> CraftKeyType.FLUID;
            default -> throw new IllegalArgumentException("Invalid type");
        };

        if (!json.has("id") || json.get("id").isJsonNull()) {
            throw new IllegalArgumentException("id required");
        }
        String id = json.get("id").getAsString();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id required");
        }

        long requestedAmount = 1;
        if (json.has("amount")) {
            try {
                requestedAmount = json.get("amount").getAsLong();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid amount", e);
            }
        }

        long normalizedAmount;
        try {
            normalizedAmount = keyType == CraftKeyType.FLUID
                    ? Math.multiplyExact(requestedAmount, MILLI_BUCKETS_PER_BUCKET)
                    : requestedAmount;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Invalid amount", e);
        }

        if (normalizedAmount <= 0 || normalizedAmount > MAX_CRAFT_AMOUNT) {
            throw new IllegalArgumentException("Invalid amount");
        }

        String cpuName = null;
        if (json.has("cpuName") && !json.get("cpuName").isJsonNull()) {
            cpuName = json.get("cpuName").getAsString();
            if (cpuName != null && cpuName.isBlank()) {
                cpuName = null;
            }
        }

        String terminalId = null;
        if (json.has("terminalId") && !json.get("terminalId").isJsonNull()) {
            terminalId = json.get("terminalId").getAsString();
            if (terminalId != null && terminalId.isBlank()) {
                terminalId = null;
            }
        }

        return new CraftRequest(keyType, id, normalizedAmount, cpuName, terminalId);
    }
}
