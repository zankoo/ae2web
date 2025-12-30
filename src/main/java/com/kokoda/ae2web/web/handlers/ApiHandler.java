package com.kokoda.ae2web.web.handlers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.assets.ItemIconService;
import com.kokoda.ae2web.web.assets.ResourceLocator;
import com.kokoda.ae2web.web.assets.TranslationService;
import com.kokoda.ae2web.web.dto.ItemStackDTO;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ResourceLocator RESOURCE_LOCATOR = new ResourceLocator();
    private static final TranslationService TRANSLATIONS = new TranslationService(RESOURCE_LOCATOR);
    private static final ItemIconService ITEM_ICONS = new ItemIconService(RESOURCE_LOCATOR);
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;
    private static final long MAX_CRAFT_AMOUNT = 1_000_000;
    private static final int CRAFT_CALCULATION_TIMEOUT_SECONDS = 10;
    private static final ExecutorService CRAFTING_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "AE2Web-Crafting");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        HttpMethod method = request.method();

        // Add CORS headers
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, Authorization, X-AE2Web-Lang");

        // Handle CORS preflight
        if (method == HttpMethod.OPTIONS) {
            sendResponse(ctx, HttpResponseStatus.OK, "", headers);
            return;
        }

        // Check authentication if enabled
        if (AE2WebConfig.isAuthEnabled()) {
            String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
            String expectedToken = "Bearer " + AE2WebConfig.getAuthToken();
            if (authHeader == null || !authHeader.equals(expectedToken)) {
                sendJsonResponse(ctx, HttpResponseStatus.UNAUTHORIZED, 
                        errorJson("Unauthorized - Invalid or missing token"), headers);
                return;
            }
        }

        try {
            // Route requests
            if (uri.equals("/") || uri.equals("/index.html")) {
                serveStaticFile(ctx, "index.html", "text/html", headers);
            } else if (uri.equals("/style.css")) {
                serveStaticFile(ctx, "style.css", "text/css", headers);
            } else if (uri.equals("/app.js")) {
                serveStaticFile(ctx, "app.js", "application/javascript", headers);
            } else if (uri.startsWith("/api/")) {
                handleApiRequest(ctx, request, uri, method, headers);
            } else {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, 
                        errorJson("Not Found"), headers);
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error handling request: {}", uri, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    errorJson("Internal Server Error"), headers);
        }
    }

    private void handleApiRequest(ChannelHandlerContext ctx, FullHttpRequest request, 
                                   String uri, HttpMethod method, HttpHeaders headers) {
        
        // Parse query parameters
        QueryStringDecoder decoder = new QueryStringDecoder(uri);
        String path = decoder.path();
        Map<String, List<String>> params = decoder.parameters();
        String lang = resolveRequestLang(request, params);

        if (path.equals("/api/status")) {
            handleStatus(ctx, headers);
        } else if (path.equals("/api/terminals")) {
            handleTerminals(ctx, headers);
        } else if (path.equals("/api/items")) {
            handleItems(ctx, params, headers, lang);
        } else if (path.equals("/api/item-icon")) {
            handleItemIcon(ctx, params, headers);
        } else if (path.equals("/api/craft") && method == HttpMethod.POST) {
            String body = request.content().toString(CharsetUtil.UTF_8);
            handleCraft(ctx, body, headers, lang);
        } else if (path.equals("/api/cpus")) {
            handleCpus(ctx, headers, lang);
        } else {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, 
                    errorJson("API endpoint not found: " + path), headers);
        }
    }

    private void handleStatus(ChannelHandlerContext ctx, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            JsonObject status = new JsonObject();
            status.addProperty("running", true);
            status.addProperty("terminals", WebServer.getInstance().getTerminals().size());
            status.addProperty("version", getModVersion());
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(status), headers);
        });
    }

    private static String getModVersion() {
        try {
            return ModList.get()
                    .getModContainerById(AE2WebMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void handleTerminals(ChannelHandlerContext ctx, HttpHeaders headers) {
        runOnServerThread(ctx, headers, () -> {
            List<Map<String, Object>> terminalList = new ArrayList<>();

            for (WebTerminalBlockEntity terminal : WebServer.getInstance().getTerminals().values()) {
                Map<String, Object> info = new HashMap<>();
                info.put("id", terminal.getTerminalId());
                info.put("connected", terminal.isConnectedToGrid());
                info.put("position", Map.of(
                        "x", terminal.getPosition().getX(),
                        "y", terminal.getPosition().getY(),
                        "z", terminal.getPosition().getZ()
                ));
                if (terminal.getTerminalLevel() != null) {
                    info.put("dimension", terminal.getTerminalLevel().dimension().location().toString());
                }
                terminalList.add(info);
            }

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(terminalList), headers);
        });
    }

    private void handleItems(ChannelHandlerContext ctx, Map<String, List<String>> params, HttpHeaders headers, String lang) {
        String search = params.containsKey("search") ? params.get("search").get(0).toLowerCase() : "";
        int page;
        int pageSize;
        try {
            page = parseIntParam(params, "page", 0, 0, Integer.MAX_VALUE);
            pageSize = parseIntParam(params, "pageSize", DEFAULT_PAGE_SIZE, 1, MAX_PAGE_SIZE);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, 
                    errorJson("Invalid pagination parameters"), headers);
            return;
        }

        runOnServerThread(ctx, headers, () -> {
            WebTerminalBlockEntity terminal = getConnectedTerminal();
            if (terminal == null) {
                sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, 
                        errorJson("No connected terminal available"), headers);
                return;
            }

            List<FilteredItem> filteredItems = filterItems(terminal.getNetworkInventory(), search, lang);
            List<FilteredItem> sortedItems = sortByAmount(filteredItems);
            List<FilteredItem> pageEntries = paginate(sortedItems, page, pageSize);
            List<ItemStackDTO> pageItems = pageEntries.stream()
                    .map(entry -> new ItemStackDTO(
                            entry.key(),
                            entry.amount(),
                            entry.name(),
                            entry.translationKey(),
                            tryResolveGtceuMaterialColor(entry.key())
                    ))
                    .toList();

            int total = sortedItems.size();
            int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
            Map<String, Object> response = Map.of(
                    "items", pageItems,
                    "total", total,
                    "page", page,
                    "pageSize", pageSize,
                    "totalPages", totalPages
            );

            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private WebTerminalBlockEntity getConnectedTerminal() {
        WebTerminalBlockEntity terminal = WebServer.getInstance().getFirstTerminal();
        if (terminal == null) {
            return null;
        }
        return terminal.isConnectedToGrid() ? terminal : null;
    }

    private record FilteredItem(AEItemKey key, long amount, String name, String translationKey) {}

    private List<FilteredItem> filterItems(Map<AEKey, Long> inventory, String searchLower, String lang) {
        return inventory.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry -> entry.getKey() instanceof AEItemKey)
                .map(entry -> Map.entry((AEItemKey) entry.getKey(), entry.getValue()))
                .filter(entry -> matchesSearch(entry.getKey(), searchLower, lang))
                .map(entry -> {
                    AEItemKey key = entry.getKey();
                    String translationKey = key.getItem().getDescriptionId();
                    String name = TRANSLATIONS.translateItemDisplayName(key, lang);
                    return new FilteredItem(key, entry.getValue(), name, translationKey);
                })
                .toList();
    }

    private static @Nullable String tryResolveGtceuMaterialColor(AEItemKey itemKey) {
        ResourceLocation id = itemKey.getId();
        if (!"gtceu".equals(id.getNamespace())) {
            return null;
        }

        try {
            ItemStack stack = itemKey.toStack();
            return GtceuMaterialColor.tryExtract(stack);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class GtceuMaterialColor {
        private static final String CHEMICAL_HELPER_CLASS = "com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper";

        private static volatile boolean resolved;
        private static volatile @Nullable Method getMaterialMethod;
        private static volatile @Nullable Method getMaterialRgbMethod;

        static @Nullable String tryExtract(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }

            Method method = resolveGetMaterialMethod();
            if (method == null) {
                return null;
            }

            try {
                Object material = method.invoke(null, stack);
                if (material == null) {
                    return null;
                }

                Method rgbMethod = resolveGetMaterialRgbMethod(material);
                if (rgbMethod == null) {
                    return null;
                }
                Object rgbValue = rgbMethod.invoke(material);
                if (!(rgbValue instanceof Integer rgb)) {
                    return null;
                }

                return String.format("%06X", rgb & 0xFFFFFF);
            } catch (Throwable t) {
                return null;
            }
        }

        private static @Nullable Method resolveGetMaterialRgbMethod(Object material) {
            Method cached = getMaterialRgbMethod;
            if (cached != null && cached.getDeclaringClass().isInstance(material)) {
                return cached;
            }

            synchronized (GtceuMaterialColor.class) {
                cached = getMaterialRgbMethod;
                if (cached != null && cached.getDeclaringClass().isInstance(material)) {
                    return cached;
                }

                try {
                    getMaterialRgbMethod = material.getClass().getMethod("getMaterialRGB");
                } catch (Throwable t) {
                    getMaterialRgbMethod = null;
                }

                return getMaterialRgbMethod;
            }
        }

        private static @Nullable Method resolveGetMaterialMethod() {
            if (resolved) {
                return getMaterialMethod;
            }

            synchronized (GtceuMaterialColor.class) {
                if (resolved) {
                    return getMaterialMethod;
                }
                try {
                    Class<?> helperClass = Class.forName(CHEMICAL_HELPER_CLASS);
                    getMaterialMethod = helperClass.getMethod("getMaterial", ItemStack.class);
                    try {
                        getMaterialRgbMethod = getMaterialMethod.getReturnType().getMethod("getMaterialRGB");
                    } catch (Throwable t) {
                        getMaterialRgbMethod = null;
                    }
                } catch (ClassNotFoundException e) {
                    getMaterialMethod = null;
                } catch (Throwable t) {
                    getMaterialMethod = null;
                } finally {
                    resolved = true;
                }
                return getMaterialMethod;
            }
        }
    }

    private boolean matchesSearch(AEItemKey itemKey, String searchLower, String lang) {
        if (searchLower.isEmpty()) {
            return true;
        }
        String name = TRANSLATIONS.translateItemDisplayName(itemKey, lang).toLowerCase();
        String id = itemKey.getId().toString().toLowerCase();
        return name.contains(searchLower) || id.contains(searchLower);
    }

    private List<FilteredItem> sortByAmount(List<FilteredItem> items) {
        return items.stream()
                .sorted(Comparator.comparingLong(FilteredItem::amount).reversed())
                .toList();
    }

    private <T> List<T> paginate(List<T> items, int page, int pageSize) {
        long startLong = (long) page * pageSize;
        if (startLong >= items.size() || startLong > Integer.MAX_VALUE) {
            return List.of();
        }

        int start = (int) startLong;
        int end = Math.min(start + pageSize, items.size());
        return items.subList(start, end);
    }

    private void handleCraft(ChannelHandlerContext ctx, String body, HttpHeaders headers, String lang) {
        CraftRequest request;
        try {
            request = parseCraftRequest(body);
        } catch (IllegalArgumentException e) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid craft request"), headers);
            return;
        }

        runOnServerThread(ctx, headers, () -> executeCraftRequest(ctx, headers, request, lang));
    }

    private void handleCpus(ChannelHandlerContext ctx, HttpHeaders headers, String lang) {
        runOnServerThread(ctx, headers, () -> {
            WebTerminalBlockEntity terminal = getConnectedTerminal();
            if (terminal == null) {
                sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                        errorJson("No connected terminal available"), headers);
                return;
            }

            ICraftingService craftingService = terminal.getCraftingService();
            if (craftingService == null) {
                sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                        errorJson("Crafting service not available"), headers);
                return;
            }

            List<Map<String, Object>> cpus = new ArrayList<>();
            for (ICraftingCPU cpu : craftingService.getCpus()) {
                cpus.add(cpuToJson(cpu, lang));
            }

            Map<String, Object> response = Map.of(
                    "cpus", cpus,
                    "total", cpus.size()
            );
            sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(response), headers);
        });
    }

    private record CraftRequest(String itemId, long amount) {
    }

    private CraftRequest parseCraftRequest(String body) {
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

        String itemId = json.has("itemId") ? json.get("itemId").getAsString() : "";
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId required");
        }

        long amount = 1;
        if (json.has("amount")) {
            try {
                amount = json.get("amount").getAsLong();
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid amount", e);
            }
        }

        if (amount <= 0 || amount > MAX_CRAFT_AMOUNT) {
            throw new IllegalArgumentException("Invalid amount");
        }

        return new CraftRequest(itemId, amount);
    }

    private void executeCraftRequest(ChannelHandlerContext ctx, HttpHeaders headers, CraftRequest request, String lang) {
        WebTerminalBlockEntity terminal = getConnectedTerminal();
        if (terminal == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("No connected terminal available"), headers);
            return;
        }

        ICraftingService craftingService = terminal.getCraftingService();
        if (craftingService == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Crafting service not available"), headers);
            return;
        }

        var actionSource = terminal.getActionSource();
        if (actionSource == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Action source not available"), headers);
            return;
        }

        ResourceLocation itemKeyId = ResourceLocation.tryParse(request.itemId());
        if (itemKeyId == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Invalid itemId"), headers);
            return;
        }

        var item = BuiltInRegistries.ITEM.getOptional(itemKeyId).orElse(null);
        if (item == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Unknown itemId"), headers);
            return;
        }

        AEItemKey itemKey = AEItemKey.of(item);
        if (!craftingService.isCraftable(itemKey)) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST,
                    errorJson("Item is not craftable"), headers);
            return;
        }

        var level = terminal.getTerminalLevel();
        if (level == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    errorJson("Terminal level not available"), headers);
            return;
        }

        Future<ICraftingPlan> planFuture = craftingService.beginCraftingCalculation(
                level,
                () -> actionSource,
                itemKey,
                request.amount(),
                CalculationStrategy.REPORT_MISSING_ITEMS
        );

        CRAFTING_EXECUTOR.execute(() -> {
            ICraftingPlan plan;
            try {
                plan = planFuture.get(CRAFT_CALCULATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                scheduleOnServerThread(() -> {
                    if (ctx.channel().isActive()) {
                        sendJsonResponse(ctx, HttpResponseStatus.GATEWAY_TIMEOUT,
                                errorJson("Crafting calculation timed out"), headers);
                    }
                });
                return;
            }

            scheduleOnServerThread(() -> {
                if (!ctx.channel().isActive()) {
                    return;
                }

                if (plan == null) {
                    sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            errorJson("Internal Server Error"), headers);
                    return;
                }

                if (plan.simulation()) {
                    Map<String, Object> response = Map.of(
                            "error", "Missing items for crafting request",
                            "missing", keyCounterToStackList(plan.missingItems(), lang)
                    );
                    sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, GSON.toJson(response), headers);
                    return;
                }

                var submitResult = craftingService.submitJob(plan, null, null, false, actionSource);
                if (!submitResult.successful()) {
                    sendJsonResponse(ctx, HttpResponseStatus.CONFLICT,
                            errorJson(mapCraftingSubmitError(submitResult.errorCode())), headers);
                    return;
                }

                sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(Map.of("status", "submitted")), headers);
            });
        });
    }

    private void scheduleOnServerThread(Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.execute(task);
    }

    private List<Map<String, Object>> keyCounterToStackList(appeng.api.stacks.KeyCounter keyCounter, String lang) {
        List<Map<String, Object>> missing = new ArrayList<>();
        if (keyCounter == null) {
            return missing;
        }

        for (Object2LongMap.Entry<AEKey> entry : keyCounter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (key == null || amount <= 0) {
                continue;
            }
            missing.add(genericStackToJson(new GenericStack(key, amount), lang));
        }
        return missing;
    }

    private Map<String, Object> genericStackToJson(GenericStack stack, String lang) {
        AEKey key = stack.what();
        String name;
        String translationKey = null;
        if (key instanceof AEItemKey itemKey) {
            translationKey = itemKey.getItem().getDescriptionId();
            name = TRANSLATIONS.translateItemDisplayName(itemKey, lang);
        } else {
            name = key.getDisplayName().getString();
        }
        return Map.of(
                "id", key.getId().toString(),
                "name", name,
                "translationKey", translationKey,
                "amount", stack.amount()
        );
    }

    private Map<String, Object> cpuToJson(ICraftingCPU cpu, String lang) {
        Map<String, Object> info = new HashMap<>();
        info.put("busy", cpu.isBusy());
        info.put("availableStorage", cpu.getAvailableStorage());
        info.put("coProcessors", cpu.getCoProcessors());
        info.put("selectionMode", cpu.getSelectionMode().toString());
        info.put("name", cpu.getName() != null ? cpu.getName().getString() : null);

        CraftingJobStatus status = cpu.getJobStatus();
        if (status != null) {
            Map<String, Object> jobStatus = new HashMap<>();
            if (status.crafting() != null) {
                jobStatus.put("crafting", genericStackToJson(status.crafting(), lang));
            }
            jobStatus.put("totalItems", status.totalItems());
            jobStatus.put("progress", status.progress());
            jobStatus.put("elapsedTimeNanos", status.elapsedTimeNanos());
            info.put("jobStatus", jobStatus);
        }

        return info;
    }

    private String mapCraftingSubmitError(CraftingSubmitErrorCode errorCode) {
        if (errorCode == null) {
            return "Unable to submit crafting job";
        }

        return switch (errorCode) {
            case INCOMPLETE_PLAN -> "Crafting plan incomplete";
            case NO_CPU_FOUND, NO_SUITABLE_CPU_FOUND -> "No crafting CPU available";
            case CPU_BUSY -> "Crafting CPU is busy";
            case CPU_OFFLINE -> "Crafting CPU is offline";
            case CPU_TOO_SMALL -> "Crafting CPU is too small";
            case MISSING_INGREDIENT -> "Missing ingredient";
        };
    }

    private void handleItemIcon(ChannelHandlerContext ctx, Map<String, List<String>> params, HttpHeaders headers) {
        String id = getFirstParam(params, "id");
        if (id == null || id.isBlank()) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Missing id"), headers);
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null) {
            sendJsonResponse(ctx, HttpResponseStatus.BAD_REQUEST, errorJson("Invalid id"), headers);
            return;
        }

        Optional<byte[]> png = ITEM_ICONS.getItemIconPng(itemId);
        if (png.isEmpty()) {
            sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, errorJson("Icon not found"), headers);
            return;
        }

        headers.set(HttpHeaderNames.CONTENT_TYPE, "image/png");
        headers.set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=86400");
        sendBinaryResponse(ctx, HttpResponseStatus.OK, png.get(), headers);
    }

    private static String resolveRequestLang(FullHttpRequest request, Map<String, List<String>> params) {
        String fromQuery = getFirstParam(params, "lang");
        if (fromQuery != null) {
            return TranslationService.normalizeLang(fromQuery).orElseGet(TRANSLATIONS::getDefaultLang);
        }

        String fromHeader = request.headers().get("X-AE2Web-Lang");
        if (fromHeader != null) {
            return TranslationService.normalizeLang(fromHeader).orElseGet(TRANSLATIONS::getDefaultLang);
        }

        String acceptLanguage = request.headers().get(HttpHeaderNames.ACCEPT_LANGUAGE);
        String inferred = inferLangFromAcceptLanguage(acceptLanguage);
        return inferred != null ? inferred : TRANSLATIONS.getDefaultLang();
    }

    private static @Nullable String getFirstParam(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private static @Nullable String inferLangFromAcceptLanguage(@Nullable String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String first = header.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (first.startsWith("zh")) {
            return "zh_cn";
        }
        if (first.startsWith("en")) {
            return "en_us";
        }
        return null;
    }

    private void serveStaticFile(ChannelHandlerContext ctx, String filename, String contentType, HttpHeaders headers) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("web/" + filename);
            if (is == null) {
                sendJsonResponse(ctx, HttpResponseStatus.NOT_FOUND, 
                        errorJson("File not found: " + filename), headers);
                return;
            }

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            headers.set(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=UTF-8");
            sendResponse(ctx, HttpResponseStatus.OK, content, headers);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error serving static file: {}", filename, e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    errorJson("Error serving file"), headers);
        }
    }

    private String errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return GSON.toJson(error);
    }

    private void sendJsonResponse(ChannelHandlerContext ctx, HttpResponseStatus status, 
                                   String content, HttpHeaders headers) {
        headers.set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        sendResponse(ctx, status, content, headers);
    }

    private void sendResponse(ChannelHandlerContext ctx, HttpResponseStatus status, 
                               String content, HttpHeaders headers) {
        ByteBuf buf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendBinaryResponse(ChannelHandlerContext ctx, HttpResponseStatus status,
                                    byte[] content, HttpHeaders headers) {
        ByteBuf buf = Unpooled.wrappedBuffer(content);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf);
        response.headers().add(headers);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private int parseIntParam(Map<String, List<String>> params, String name, int defaultValue, int min, int max) {
        if (!params.containsKey(name)) {
            return defaultValue;
        }

        String raw = params.get(name).get(0);
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number");
        }

        if (value < min || value > max) {
            throw new IllegalArgumentException("Out of range");
        }

        return value;
    }

    private void runOnServerThread(ChannelHandlerContext ctx, HttpHeaders headers, Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sendJsonResponse(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, 
                    errorJson("Server not available"), headers);
            return;
        }

        server.execute(() -> executeSafely(ctx, headers, task));
    }

    private void executeSafely(ChannelHandlerContext ctx, HttpHeaders headers, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error handling API request", e);
            sendJsonResponse(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    errorJson("Internal Server Error"), headers);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        AE2WebMod.LOGGER.error("HTTP handler error", cause);
        ctx.close();
    }
}
