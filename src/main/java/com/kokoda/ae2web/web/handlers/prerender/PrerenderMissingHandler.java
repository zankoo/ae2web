package com.kokoda.ae2web.web.handlers.prerender;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.networking.crafting.ICraftingService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.*;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

public final class PrerenderMissingHandler {
    private PrerenderMissingHandler() {}

    public static void handleMissing(ChannelHandlerContext ctx,
                                     Map<String, List<String>> params, HttpHeaders headers) {
        String namespace = getFirstParam(params, "namespace");
        int limit;
        try {
            limit = parseIntParam(params, "limit", 100, 1, 100000);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid limit parameter"), headers);
            return;
        }

        runOnServerThread(ctx, headers, () -> {
            PrerenderStorageService storage = PrerenderStorageService.getInstance();
            storage.initialize();

            List<ItemMissingInfo> registryItems = new ArrayList<>();

            for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {
                if (namespace != null && !itemId.getNamespace().equals(namespace)) {
                    continue;
                }
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    continue;
                }
                if (item == Items.AIR || "minecraft:air".equals(itemId.toString())) {
                    continue;
                }
                String iconKey = IconKeyUtils.iconKey(AEItemKey.of(item));
                if (!storage.hasIcon(iconKey)) {
                    registryItems.add(new ItemMissingInfo(iconKey, null));
                }
            }

            for (ResourceLocation fluidId : ForgeRegistries.FLUIDS.getKeys()) {
                if (namespace != null && !fluidId.getNamespace().equals(namespace)) {
                    continue;
                }
                var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                if (fluid != null && fluid != Fluids.EMPTY) {
                    String iconKey = IconKeyUtils.iconKey(AEFluidKey.of(fluid));
                    if (!storage.hasIcon(iconKey)) {
                        registryItems.add(new ItemMissingInfo(iconKey, null));
                    }
                }
            }

            Map<String, ItemMissingInfo> meNetworkItems = new LinkedHashMap<>();
            Map<String, WebTerminalBlockEntity> terminals = WebServer.getInstance().getTerminals();
            for (WebTerminalBlockEntity terminal : terminals.values()) {
                if (!terminal.isConnectedToGrid()) {
                    continue;
                }

                Map<AEKey, Long> inventory = terminal.getNetworkInventory(WebTerminalBlockEntity.KeyFilter.ALL);
                for (AEKey key : inventory.keySet()) {
                    if (namespace != null && !key.getId().getNamespace().equals(namespace)) {
                        continue;
                    }
                    if ("minecraft:air".equals(key.getId().toString())) {
                        continue;
                    }

                    String iconKey = IconKeyUtils.iconKey(key);
                    if (storage.hasIcon(iconKey) || meNetworkItems.containsKey(iconKey)) {
                        continue;
                    }

                    String stackNbtBase64 = null;
                    if (key instanceof AEItemKey itemKey) {
                        stackNbtBase64 = serializeItemStackToBase64(itemKey.toStack());
                    }

                    meNetworkItems.put(iconKey, new ItemMissingInfo(iconKey, stackNbtBase64));
                }
            }

            // Also include craftable-only keys (zero stock) so the UI can show correct icons/names for craftables.
            // Registry scanning cannot discover NBT variants, so we must ask AE2 crafting service.
            for (WebTerminalBlockEntity terminal : terminals.values()) {
                if (!terminal.isConnectedToGrid()) {
                    continue;
                }

                ICraftingService craftingService = terminal.getCraftingService();
                if (craftingService == null) {
                    continue;
                }

                Set<AEKey> craftables = craftingService.getCraftables(key -> key instanceof AEItemKey || key instanceof AEFluidKey);
                for (AEKey key : craftables) {
                    if (namespace != null && !key.getId().getNamespace().equals(namespace)) {
                        continue;
                    }
                    if ("minecraft:air".equals(key.getId().toString())) {
                        continue;
                    }

                    String iconKey = IconKeyUtils.iconKey(key);
                    if (storage.hasIcon(iconKey) || meNetworkItems.containsKey(iconKey)) {
                        continue;
                    }

                    String stackNbtBase64 = null;
                    if (key instanceof AEItemKey itemKey) {
                        stackNbtBase64 = serializeItemStackToBase64(itemKey.toStack());
                    }

                    meNetworkItems.put(iconKey, new ItemMissingInfo(iconKey, stackNbtBase64));
                }
            }

            List<ItemMissingInfo> allMissing = new ArrayList<>();
            allMissing.addAll(meNetworkItems.values());
            allMissing.addAll(registryItems);

            if (allMissing.size() > limit) {
                allMissing = allMissing.subList(0, limit);
            }

            JsonArray missingArray = new JsonArray();
            for (ItemMissingInfo info : allMissing) {
                JsonObject item = new JsonObject();
                item.addProperty("iconKey", info.iconKey);
                if (info.stackNbt != null) {
                    item.addProperty("stackNbt", info.stackNbt);
                }
                missingArray.add(item);
            }

            Map<String, Integer> stats = storage.getStats();
            JsonObject response = new JsonObject();
            response.add("missing", missingArray);
            response.addProperty("total", stats.get("totalItems"));
            response.addProperty("renderedCount", stats.get("renderedCount"));
            response.addProperty("hasMore", allMissing.size() >= limit);

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private record ItemMissingInfo(String iconKey, String stackNbt) {}

    private static String serializeItemStackToBase64(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            CompoundTag data = new CompoundTag();
            stack.save(data);
            NbtIo.writeCompressed(data, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }
}
