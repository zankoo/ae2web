package com.kokoda.ae2web.web.handlers;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity.KeyFilter;
import com.kokoda.ae2web.web.dto.FluidStackDTO;
import com.kokoda.ae2web.web.dto.ItemStackDTO;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.*;
import java.util.Locale;

import static com.kokoda.ae2web.web.handlers.HandlerUtils.*;

/**
 * Handles /api/items endpoint for inventory listing.
 */
public final class InventoryHandler {

    private InventoryHandler() {}

    public static void handleItems(ChannelHandlerContext ctx, FullHttpRequest request,
                                   Map<String, List<String>> params, HttpHeaders headers) {
        String searchRaw = getFirstParam(params, "search");
        String search = searchRaw == null ? "" : searchRaw.toLowerCase(Locale.ROOT);

        String typeRaw = getFirstParam(params, "type");
        String typeParam = (typeRaw == null || typeRaw.isBlank()) ? "all" : typeRaw.toLowerCase(Locale.ROOT);

        String terminalId = getFirstParam(params, "terminalId");

        KeyFilter keyFilter = switch (typeParam) {
            case "items" -> KeyFilter.ITEMS;
            case "fluids" -> KeyFilter.FLUIDS;
            default -> KeyFilter.ALL;
        };

        runOnServerThread(ctx, headers, () -> {
            WebTerminalBlockEntity terminal = getConnectedTerminal(terminalId);
            if (terminal == null) {
                sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                        errorJson("No connected terminal available"), headers);
                return;
            }

            PrerenderStorageService storage = PrerenderStorageService.getInstance();
            storage.initialize();

            // Get craftable items from crafting service
            Set<AEKey> craftableKeys = new HashSet<>();
            ICraftingService craftingService = terminal.getCraftingService();
            if (craftingService != null) {
                Set<AEKey> allCraftables = craftingService.getCraftables(key -> {
                    return switch (keyFilter) {
                        case ITEMS -> key instanceof AEItemKey;
                        case FLUIDS -> key instanceof AEFluidKey;
                        case ALL -> key instanceof AEItemKey || key instanceof AEFluidKey;
                    };
                });
                craftableKeys.addAll(allCraftables);
            }

            Map<AEKey, Long> inventory = terminal.getNetworkInventory(keyFilter);
            List<FilteredEntry> filteredItems = filterStacks(inventory, search, storage, craftableKeys);

            // Add zero-stock craftable items
            addZeroStockCraftables(filteredItems, inventory, craftableKeys, search, storage, keyFilter);

            List<FilteredEntry> sortedItems = sortForUi(filteredItems);

            List<Object> pageItems = new ArrayList<>();
            for (FilteredEntry entry : sortedItems) {
                if (entry.key() instanceof AEItemKey itemKey) {
                    pageItems.add(new ItemStackDTO(itemKey, entry.amount(), entry.name(), entry.translationKey(), entry.craftable()));
                } else if (entry.key() instanceof AEFluidKey fluidKey) {
                    pageItems.add(new FluidStackDTO(fluidKey, entry.amount(), entry.name(), entry.translationKey(), entry.craftable()));
                }
            }

            int total = sortedItems.size();
            Map<String, Object> response = Map.of(
                    "items", pageItems,
                    "total", total
            );

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private record FilteredEntry(AEKey key, long amount, String name, String translationKey, boolean craftable) {}

    private static List<FilteredEntry> filterStacks(Map<AEKey, Long> inventory, String searchLower,
                                                    PrerenderStorageService storage, Set<AEKey> craftableKeys) {
        List<FilteredEntry> result = new ArrayList<>();
        for (var entry : inventory.entrySet()) {
            AEKey key = entry.getKey();
            Long amount = entry.getValue();
            if (amount == null || amount <= 0) continue;

            String name;
            String translationKey;

            if (key instanceof AEItemKey itemKey) {
                translationKey = itemKey.getItem().getDescriptionId();
                name = getTranslatedName(itemKey, storage);
            } else if (key instanceof AEFluidKey fluidKey) {
                translationKey = fluidKey.getFluid().getFluidType().getDescriptionId();
                name = getFluidName(fluidKey, storage);
            } else {
                continue;
            }

            if (matchesSearch(name, key.getId().toString(), searchLower)) {
                boolean craftable = craftableKeys.contains(key);
                result.add(new FilteredEntry(key, amount, name, translationKey, craftable));
            }
        }
        return result;
    }

    private static void addZeroStockCraftables(List<FilteredEntry> result, Map<AEKey, Long> inventory,
                                               Set<AEKey> craftableKeys, String searchLower,
                                               PrerenderStorageService storage, KeyFilter keyFilter) {
        for (AEKey key : craftableKeys) {
            // Skip if already in inventory
            if (inventory.containsKey(key)) continue;

            String name;
            String translationKey;

            if (key instanceof AEItemKey itemKey) {
                if (keyFilter == KeyFilter.FLUIDS) continue;
                translationKey = itemKey.getItem().getDescriptionId();
                name = getTranslatedName(itemKey, storage);
            } else if (key instanceof AEFluidKey fluidKey) {
                if (keyFilter == KeyFilter.ITEMS) continue;
                translationKey = fluidKey.getFluid().getFluidType().getDescriptionId();
                name = getFluidName(fluidKey, storage);
            } else {
                continue;
            }

            if (matchesSearch(name, key.getId().toString(), searchLower)) {
                result.add(new FilteredEntry(key, 0, name, translationKey, true));
            }
        }
    }

    private static String getTranslatedName(AEItemKey itemKey, PrerenderStorageService storage) {
        // Try to use cached translation from prerender (client-side language)
        String iconKey = IconKeyUtils.iconKey(itemKey);
        String cached = storage.getTranslation(iconKey);
        if (cached != null && !cached.isBlank()) {
            return stripMinecraftFormatting(cached);
        }
        // Fallback to server-side name (may be in server's language)
        return stripMinecraftFormatting(itemKey.getDisplayName().getString());
    }

    private static String getFluidName(AEFluidKey fluidKey, PrerenderStorageService storage) {
        // Try to use cached translation from prerender (client-side language)
        String iconKey = IconKeyUtils.iconKey(fluidKey);
        String cached = storage.getTranslation(iconKey);
        if (cached != null && !cached.isBlank()) {
            return stripMinecraftFormatting(cached);
        }
        // Fallback to server-side name (may be in server's language)
        return stripMinecraftFormatting(fluidKey.getDisplayName().getString());
    }

    /**
     * Remove Minecraft formatting codes (§ followed by color/format character).
     * Examples: "256安§4UHV§r激光源仓" -> "256安UHV激光源仓"
     */
    private static String stripMinecraftFormatting(String text) {
        if (text == null) return null;
        // §0-§9, §a-§f: colors, §k: obfuscated, §l: bold, §m: strikethrough,
        // §n: underline, §o: italic, §r: reset (case insensitive)
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private static boolean matchesSearch(String name, String id, String searchLower) {
        if (searchLower.isEmpty()) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains(searchLower) || id.toLowerCase(Locale.ROOT).contains(searchLower);
    }

    private static List<FilteredEntry> sortForUi(List<FilteredEntry> items) {
        Comparator<FilteredEntry> comparator = Comparator.<FilteredEntry>comparingInt(e -> (e.key() instanceof AEFluidKey) ? 1 : 0)
                // Keep item stacks before fluid stacks when showing both.
                // Match AE2 terminal behavior better: stable sort by name/id, not by fluctuating amounts.
                .thenComparing(e -> e.name(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(e -> e.key().getId().toString())
                .thenComparing(e -> IconKeyUtils.iconKey(e.key()));

        return items.stream().sorted(comparator).toList();
    }
}
