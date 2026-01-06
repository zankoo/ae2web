package com.kokoda.ae2web.web;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.handlers.ApiHandler;
import com.kokoda.ae2web.web.dto.InventoryItemDTO;
import com.kokoda.ae2web.web.inventory.InventoryNameUtils;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import com.kokoda.ae2web.web.history.ProductionHistoryService;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;

public final class WebServer {
    private static final WebServer instance = new WebServer();
    private static final Gson SSE_GSON = new Gson();
    private static final long INVENTORY_DELTA_FLUSH_DELAY_MS = 200L;

    private final Object lifecycleLock = new Object();
    private final Object ioExecutorLock = new Object();
    private final Map<String, WebTerminalBlockEntity> terminals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> terminalInventoryVersions = new ConcurrentHashMap<>();
    private final Map<String, Set<Channel>> terminalInventorySubscribers = new ConcurrentHashMap<>();
    private final Map<String, TerminalInventoryState> terminalInventoryStates = new ConcurrentHashMap<>();
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;
    private volatile Thread serverThread;
    private volatile boolean running;
    private volatile ScheduledExecutorService ioExecutor;

    private WebServer() {
    }

    public static WebServer getInstance() {
        return instance;
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (running || (serverThread != null && serverThread.isAlive())) {
                return;
            }

            ScheduledExecutorService executor = getIoExecutor();
            ProductionHistoryService.getInstance().start(executor);

            int port = AE2WebConfig.getHttpPort();
            String bindAddress = AE2WebConfig.getBindAddress();
            serverThread = new Thread(() -> runServer(bindAddress, port), "AE2Web-HTTP");
            serverThread.setDaemon(true);
            serverThread.start();
        }
    }

    private void runServer(String bindAddress, int port) {
        EventLoopGroup localBossGroup = new NioEventLoopGroup(1);
        EventLoopGroup localWorkerGroup = new NioEventLoopGroup();

        synchronized (lifecycleLock) {
            bossGroup = localBossGroup;
            workerGroup = localWorkerGroup;
        }

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(localBossGroup, localWorkerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ApiChannelInitializer())
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            Channel channel = bootstrap.bind(bindAddress, port).sync().channel();
            synchronized (lifecycleLock) {
                serverChannel = channel;
                running = true;
            }

            AE2WebMod.LOGGER.info("AE2 Web Terminal HTTP server started on {}:{}", bindAddress, port);
            channel.closeFuture().sync();
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to start HTTP server", e);
        } finally {
            stop();
        }
    }

    public void stop() {
        ProductionHistoryService.getInstance().stop();

        Channel localServerChannel;
        EventLoopGroup localWorkerGroup;
        EventLoopGroup localBossGroup;
        ScheduledExecutorService localIoExecutor;

        synchronized (lifecycleLock) {
            running = false;
            terminals.clear();
            terminalInventoryVersions.clear();
            terminalInventorySubscribers.clear();
            terminalInventoryStates.clear();

            if (serverChannel == null && bossGroup == null && workerGroup == null) {
                // Still shut down any auxiliary executor(s).
                localIoExecutor = ioExecutor;
                ioExecutor = null;
                localServerChannel = null;
                localWorkerGroup = null;
                localBossGroup = null;
            } else {
                localServerChannel = serverChannel;
                localWorkerGroup = workerGroup;
                localBossGroup = bossGroup;
                localIoExecutor = ioExecutor;

                serverChannel = null;
                workerGroup = null;
                bossGroup = null;
                ioExecutor = null;
            }
        }

        if (localServerChannel != null) {
            closeAndShutdownNetty(localServerChannel, localWorkerGroup, localBossGroup);
            localWorkerGroup = null;
            localBossGroup = null;
        }

        if (localWorkerGroup != null) {
            shutdownEventLoopGroup(localWorkerGroup);
        }

        if (localBossGroup != null) {
            shutdownEventLoopGroup(localBossGroup);
        }

        shutdownExecutor(localIoExecutor);

        AE2WebMod.LOGGER.info("AE2 Web Terminal HTTP server stopped");
    }

    private static void closeAndShutdownNetty(Channel serverChannel,
                                              EventLoopGroup workerGroup,
                                              EventLoopGroup bossGroup) {
        if (serverChannel == null) {
            shutdownEventLoopGroup(workerGroup);
            shutdownEventLoopGroup(bossGroup);
            return;
        }

        if (serverChannel.eventLoop() != null && serverChannel.eventLoop().inEventLoop()) {
            // Avoid deadlocking the Netty event loop by blocking waits inside it.
            Thread shutdownThread = new Thread(() -> closeAndShutdownNettyBlocking(serverChannel, workerGroup, bossGroup),
                    "AE2Web-HTTP-Shutdown");
            shutdownThread.setDaemon(true);
            shutdownThread.start();
            return;
        }

        closeAndShutdownNettyBlocking(serverChannel, workerGroup, bossGroup);
    }

    private static void closeAndShutdownNettyBlocking(Channel serverChannel,
                                                      EventLoopGroup workerGroup,
                                                      EventLoopGroup bossGroup) {
        try {
            serverChannel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        shutdownEventLoopGroup(workerGroup);
        shutdownEventLoopGroup(bossGroup);
    }

    private static void shutdownEventLoopGroup(EventLoopGroup group) {
        if (group == null) {
            return;
        }

        try {
            group.shutdownGracefully(0, 2, TimeUnit.SECONDS).awaitUninterruptibly(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public ScheduledExecutorService getIoExecutor() {
        ScheduledExecutorService current = ioExecutor;
        if (current != null && !current.isShutdown()) {
            return current;
        }

        synchronized (ioExecutorLock) {
            current = ioExecutor;
            if (current == null || current.isShutdown()) {
                ioExecutor = Executors.newScheduledThreadPool(2, r -> {
                    Thread t = new Thread(r, "AE2Web-IO");
                    t.setDaemon(true);
                    return t;
                });
            }
            return ioExecutor;
        }
    }

    private static void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }

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

    public void registerTerminal(WebTerminalBlockEntity terminal) {
        terminals.put(terminal.getTerminalId(), terminal);
        terminalInventoryVersions.computeIfAbsent(terminal.getTerminalId(), id -> new AtomicLong(0L));
        terminalInventoryStates.computeIfAbsent(terminal.getTerminalId(), id -> new TerminalInventoryState());
        AE2WebMod.LOGGER.debug("Registered terminal: {}", terminal.getTerminalId());
    }

    public void unregisterTerminal(WebTerminalBlockEntity terminal) {
        if (terminal == null) {
            return;
        }
        unregisterTerminalById(terminal.getTerminalId());
    }

    public void unregisterTerminalById(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }

        terminals.remove(terminalId);
        Set<Channel> subs = terminalInventorySubscribers.remove(terminalId);
        if (subs != null) {
            for (Channel ch : subs) {
                ch.eventLoop().execute(() -> {
                    if (ch.isActive()) {
                        ch.close();
                    }
                });
            }
        }
        terminalInventoryVersions.remove(terminalId);
        terminalInventoryStates.remove(terminalId);
        AE2WebMod.LOGGER.debug("Unregistered terminal: {}", terminalId);
    }

    public Map<String, WebTerminalBlockEntity> getTerminals() {
        return Collections.unmodifiableMap(terminals);
    }

    public WebTerminalBlockEntity getTerminal(String id) {
        return terminals.get(id);
    }

    public WebTerminalBlockEntity getFirstTerminal() {
        return terminals.values().stream().findFirst().orElse(null);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean hasInventorySubscribers(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return false;
        }
        Set<Channel> subs = terminalInventorySubscribers.get(terminalId);
        return subs != null && !subs.isEmpty();
    }

    public long getInventoryVersion(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return 0L;
        }
        AtomicLong v = terminalInventoryVersions.get(terminalId);
        return v == null ? 0L : v.get();
    }

    public void subscribeInventory(String terminalId, Channel channel) {
        if (terminalId == null || terminalId.isBlank() || channel == null) {
            return;
        }
        terminalInventoryVersions.computeIfAbsent(terminalId, id -> new AtomicLong(0L));
        terminalInventoryStates.computeIfAbsent(terminalId, id -> new TerminalInventoryState());
        terminalInventorySubscribers
                .computeIfAbsent(terminalId, id -> ConcurrentHashMap.newKeySet())
                .add(channel);
    }

    public void unsubscribeInventory(String terminalId, Channel channel) {
        if (terminalId == null || terminalId.isBlank() || channel == null) {
            return;
        }
        Set<Channel> subs = terminalInventorySubscribers.get(terminalId);
        if (subs == null) {
            return;
        }
        subs.remove(channel);
        if (subs.isEmpty()) {
            terminalInventorySubscribers.remove(terminalId, subs);
            TerminalInventoryState state = terminalInventoryStates.get(terminalId);
            if (state != null) {
                synchronized (state.lock) {
                    state.items.clear();
                    state.craftableUniqueIds.clear();
                    state.pending.clear();
                    state.flushFuture = null;
                    state.initialized = false;
                }
            }
        }
    }

    public void notifyInventoryChanged(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }

        AtomicLong version = terminalInventoryVersions.computeIfAbsent(terminalId, id -> new AtomicLong(0L));
        long newVersion = version.incrementAndGet();

        Set<Channel> subs = terminalInventorySubscribers.get(terminalId);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (Channel channel : subs) {
            if (!channel.isActive()) {
                continue;
            }
            // Writing must occur on the channel's event loop.
            channel.eventLoop().execute(() -> {
                if (!channel.isActive()) {
                    return;
                }
                if (channel.attr(com.kokoda.ae2web.web.handlers.SseHandler.SUBSCRIBED_TERMINAL).get() == null) {
                    // Not an SSE channel we manage anymore.
                    return;
                }
                com.kokoda.ae2web.web.handlers.SseHandler.writeInventoryEvent(channel, terminalId, newVersion);
            });
        }
    }

    public void queueInventoryDelta(String terminalId, AEKey key, Long amount, Boolean craftable) {
        if (terminalId == null || terminalId.isBlank() || key == null) {
            return;
        }
        TerminalInventoryState state = terminalInventoryStates.computeIfAbsent(terminalId, id -> new TerminalInventoryState());
        String uniqueId = buildUniqueId(key);
        if (uniqueId == null) {
            return;
        }

        synchronized (state.lock) {
            PendingInventoryDelta pending = state.pending.computeIfAbsent(uniqueId, id -> new PendingInventoryDelta(key));
            pending.merge(key, amount, craftable, null);

            if (!state.initialized) {
                // Buffer changes until a snapshot initializes the baseline.
                return;
            }

            scheduleDeltaFlushLocked(terminalId, state);
        }
    }

    /**
     * Update inventory item names after a prerender upload provides a translated display name.
     * This avoids requiring a full page refresh to pick up newly rendered translations.
     */
    public void queueInventoryNameRefresh(String iconKey, String translatedName) {
        if (iconKey == null || iconKey.isBlank()) {
            return;
        }
        if (translatedName == null || translatedName.isBlank()) {
            return;
        }

        String sanitizedName = InventoryNameUtils.stripMinecraftFormatting(translatedName);
        if (sanitizedName.isBlank()) {
            return;
        }

        String itemUniqueId = "item:" + iconKey;
        String fluidUniqueId = "fluid:" + iconKey;

        for (var entry : terminalInventoryStates.entrySet()) {
            String terminalId = entry.getKey();
            TerminalInventoryState state = entry.getValue();

            synchronized (state.lock) {
                if (!state.initialized || state.items.isEmpty()) {
                    continue;
                }

                boolean queued = false;

                InventoryItemDTO item = state.items.get(itemUniqueId);
                if (item != null && !Objects.equals(item.getName(), sanitizedName)) {
                    PendingInventoryDelta pending = state.pending.computeIfAbsent(itemUniqueId, id -> new PendingInventoryDelta(null));
                    pending.merge(null, null, null, sanitizedName);
                    queued = true;
                }

                InventoryItemDTO fluid = state.items.get(fluidUniqueId);
                if (fluid != null && !Objects.equals(fluid.getName(), sanitizedName)) {
                    PendingInventoryDelta pending = state.pending.computeIfAbsent(fluidUniqueId, id -> new PendingInventoryDelta(null));
                    pending.merge(null, null, null, sanitizedName);
                    queued = true;
                }

                if (queued) {
                    scheduleDeltaFlushLocked(terminalId, state);
                }
            }
        }
    }

    public void sendInventorySnapshot(Channel channel, String terminalId) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        if (terminalId == null || terminalId.isBlank()) {
            return;
        }

        WebTerminalBlockEntity terminal = terminals.get(terminalId);
        if (terminal == null || !terminal.isConnectedToGrid()) {
            return;
        }

        TerminalInventoryState state = terminalInventoryStates.computeIfAbsent(terminalId, id -> new TerminalInventoryState());
        ensureInventoryStateInitialized(terminalId, terminal, state);

        long version = getInventoryVersion(terminalId);
        List<InventoryItemDTO> items;
        synchronized (state.lock) {
            items = new ArrayList<>(state.items.values());
        }

        com.kokoda.ae2web.web.handlers.SseHandler.writeHelloEvent(channel, terminalId, version);
        com.kokoda.ae2web.web.handlers.SseHandler.writeInventorySnapshotEvent(channel, terminalId, version, items);
    }

    private void scheduleDeltaFlushLocked(String terminalId, TerminalInventoryState state) {
        if (state.flushFuture != null && !state.flushFuture.isDone()) {
            return;
        }

        ScheduledExecutorService executor = getIoExecutor();
        state.flushFuture = executor.schedule(() -> scheduleOnServerThread(() -> flushInventoryDeltas(terminalId)),
                INVENTORY_DELTA_FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void scheduleOnServerThread(Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(task);
        }
    }

    private void ensureInventoryStateInitialized(String terminalId, WebTerminalBlockEntity terminal, TerminalInventoryState state) {
        synchronized (state.lock) {
            if (state.initialized) {
                return;
            }
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        Map<String, AEKey> craftableKeyByUniqueId = new HashMap<>();
        ICraftingService craftingService = terminal.getCraftingService();
        if (craftingService != null) {
            Set<AEKey> craftables = craftingService.getCraftables(key -> key instanceof AEItemKey || key instanceof AEFluidKey);
            for (AEKey craftable : craftables) {
                String craftableUniqueId = buildUniqueId(craftable);
                if (craftableUniqueId != null) {
                    craftableKeyByUniqueId.put(craftableUniqueId, craftable);
                }
            }
        }

        Map<AEKey, Long> inventory = terminal.getNetworkInventory(WebTerminalBlockEntity.KeyFilter.ALL);
        Map<String, InventoryItemDTO> next = new HashMap<>();

        for (var entry : inventory.entrySet()) {
            AEKey key = entry.getKey();
            Long amount = entry.getValue();
            if (key == null || amount == null || amount <= 0) {
                continue;
            }

            String uniqueId = buildUniqueId(key);
            if (uniqueId == null) {
                continue;
            }

            boolean craftable = craftableKeyByUniqueId.containsKey(uniqueId);
            InventoryItemDTO dto = buildInventoryItem(key, amount, craftable, storage);
            if (dto != null) {
                next.put(uniqueId, dto);
            }
        }

        for (var entry : craftableKeyByUniqueId.entrySet()) {
            String craftableUniqueId = entry.getKey();
            if (next.containsKey(craftableUniqueId)) {
                continue;
            }

            InventoryItemDTO dto = buildInventoryItem(entry.getValue(), 0L, true, storage);
            if (dto != null) {
                next.put(craftableUniqueId, dto);
            }
        }

        Map<String, PendingInventoryDelta> buffered;
        synchronized (state.lock) {
            buffered = new HashMap<>(state.pending);
            state.pending.clear();
        }

        if (!buffered.isEmpty()) {
            applyBufferedDeltasToSnapshot(next, craftableKeyByUniqueId, buffered, storage);
        }

        synchronized (state.lock) {
            state.items.clear();
            state.items.putAll(next);
            state.craftableUniqueIds.clear();
            state.craftableUniqueIds.addAll(craftableKeyByUniqueId.keySet());
            state.initialized = true;
            state.flushFuture = null;
            if (!state.pending.isEmpty()) {
                scheduleDeltaFlushLocked(terminalId, state);
            }
        }

        // Mark snapshot icon keys as "seen" to prevent server-side TTL GC from deleting active icons.
        storage.markSeen(next.values().stream().map(InventoryItemDTO::getIconKey).toList());
    }

    private static void applyBufferedDeltasToSnapshot(Map<String, InventoryItemDTO> snapshot,
                                                     Map<String, AEKey> craftableKeyByUniqueId,
                                                     Map<String, PendingInventoryDelta> buffered,
                                                     PrerenderStorageService storage) {
        for (var entry : buffered.entrySet()) {
            String uniqueId = entry.getKey();
            PendingInventoryDelta delta = entry.getValue();
            if (uniqueId == null || delta == null || delta.key == null) {
                continue;
            }

            if (delta.craftable != null) {
                if (delta.craftable) {
                    craftableKeyByUniqueId.put(uniqueId, delta.key);
                } else {
                    craftableKeyByUniqueId.remove(uniqueId);
                }
            }

            boolean craftable = craftableKeyByUniqueId.containsKey(uniqueId);
            InventoryItemDTO existing = snapshot.get(uniqueId);
            long baseAmount = existing != null ? existing.getAmount() : 0L;
            long nextAmount = delta.amount != null ? delta.amount : baseAmount;

            if (nextAmount <= 0 && !craftable) {
                snapshot.remove(uniqueId);
                continue;
            }

            if (existing == null) {
                InventoryItemDTO created = buildInventoryItem(delta.key, nextAmount, craftable, storage);
                if (created != null) {
                    if (delta.name != null && !Objects.equals(created.getName(), delta.name)) {
                        created = created.withName(delta.name);
                    }
                    snapshot.put(uniqueId, created);
                }
            } else {
                InventoryItemDTO updated = existing.withAmountAndCraftable(nextAmount, craftable);
                if (delta.name != null && !Objects.equals(existing.getName(), delta.name)) {
                    updated = updated.withName(delta.name);
                }
                snapshot.put(uniqueId, updated);
            }
        }
    }

    private void flushInventoryDeltas(String terminalId) {
        TerminalInventoryState state = terminalInventoryStates.get(terminalId);
        if (state == null) {
            return;
        }

        Map<String, PendingInventoryDelta> pending;
        synchronized (state.lock) {
            if (!state.initialized) {
                return;
            }
            if (state.pending.isEmpty()) {
                state.flushFuture = null;
                return;
            }
            pending = new HashMap<>(state.pending);
            state.pending.clear();
            state.flushFuture = null;
        }

        PrerenderStorageService storage = PrerenderStorageService.getInstance();
        storage.initialize();

        List<InventoryItemDTO> added = new ArrayList<>();
        List<InventoryPatch> updated = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        synchronized (state.lock) {
            for (var entry : pending.entrySet()) {
                String uniqueId = entry.getKey();
                PendingInventoryDelta delta = entry.getValue();
                if (delta == null || uniqueId == null) {
                    continue;
                }

                if (delta.craftable != null) {
                    if (delta.craftable) {
                        state.craftableUniqueIds.add(uniqueId);
                    } else {
                        state.craftableUniqueIds.remove(uniqueId);
                    }
                }

                InventoryItemDTO before = state.items.get(uniqueId);
                long beforeAmount = before != null ? before.getAmount() : 0L;
                boolean beforeCraftable = before != null ? before.isCraftable() : state.craftableUniqueIds.contains(uniqueId);
                String beforeName = before != null ? before.getName() : null;

                long nextAmount = delta.amount != null ? delta.amount : beforeAmount;
                boolean nextCraftable = delta.craftable != null ? delta.craftable : beforeCraftable;
                String nextName = delta.name != null ? delta.name : beforeName;

                if (nextAmount <= 0 && !nextCraftable) {
                    if (before != null) {
                        state.items.remove(uniqueId);
                        removed.add(uniqueId);
                    }
                    continue;
                }

                if (before == null) {
                    if (delta.key == null) {
                        continue;
                    }
                    InventoryItemDTO created = buildInventoryItem(delta.key, nextAmount, nextCraftable, storage);
                    if (created != null) {
                        if (nextName != null && !Objects.equals(created.getName(), nextName)) {
                            created = created.withName(nextName);
                        }
                        state.items.put(uniqueId, created);
                        added.add(created);
                    }
                    continue;
                }

                boolean amountChanged = beforeAmount != nextAmount;
                boolean craftableChanged = before.isCraftable() != nextCraftable;
                boolean nameChanged = !Objects.equals(beforeName, nextName);
                if (!amountChanged && !craftableChanged && !nameChanged) {
                    continue;
                }

                InventoryItemDTO after = before;
                if (amountChanged || craftableChanged) {
                    after = after.withAmountAndCraftable(nextAmount, nextCraftable);
                }
                if (nameChanged && nextName != null) {
                    after = after.withName(nextName);
                }
                state.items.put(uniqueId, after);
                updated.add(new InventoryPatch(uniqueId, nextAmount, nextCraftable, nameChanged ? nextName : null));
            }
        }

        if (added.isEmpty() && updated.isEmpty() && removed.isEmpty()) {
            return;
        }

        // Record history data points
        long now = System.currentTimeMillis();
        ProductionHistoryService historyService = ProductionHistoryService.getInstance();
        for (InventoryItemDTO dto : added) {
            historyService.recordDataPoint(terminalId, dto.getUniqueId(), dto.getAmount(), now);
        }
        for (InventoryPatch patch : updated) {
            historyService.recordDataPoint(terminalId, patch.getUniqueId(), patch.getAmount(), now);
        }
        for (String uniqueId : removed) {
            historyService.recordDataPoint(terminalId, uniqueId, 0L, now);
        }

        AtomicLong version = terminalInventoryVersions.computeIfAbsent(terminalId, id -> new AtomicLong(0L));
        long newVersion = version.incrementAndGet();

        if (!added.isEmpty()) {
            storage.markSeen(added.stream().map(InventoryItemDTO::getIconKey).toList());
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("terminalId", terminalId);
        payload.addProperty("version", newVersion);
        if (!added.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (InventoryItemDTO dto : added) {
                arr.add(SSE_GSON.toJsonTree(dto));
            }
            payload.add("added", arr);
        }
        if (!updated.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (InventoryPatch patch : updated) {
                arr.add(SSE_GSON.toJsonTree(patch));
            }
            payload.add("updated", arr);
        }
        if (!removed.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String id : removed) {
                arr.add(id);
            }
            payload.add("removed", arr);
        }

        broadcastInventoryDelta(terminalId, payload.toString());
    }

    private void broadcastInventoryDelta(String terminalId, String data) {
        Set<Channel> subs = terminalInventorySubscribers.get(terminalId);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (Channel channel : subs) {
            if (!channel.isActive()) {
                continue;
            }
            channel.eventLoop().execute(() -> {
                if (!channel.isActive()) {
                    return;
                }
                if (channel.attr(com.kokoda.ae2web.web.handlers.SseHandler.SUBSCRIBED_TERMINAL).get() == null) {
                    return;
                }
                com.kokoda.ae2web.web.handlers.SseHandler.writeInventoryDeltaEvent(channel, terminalId, data);
            });
        }
    }

    private static String buildUniqueId(AEKey key) {
        if (key instanceof AEItemKey || key instanceof AEFluidKey) {
            String iconKey = IconKeyUtils.iconKey(key);
            String type = (key instanceof AEFluidKey) ? "fluid" : "item";
            return type + ":" + iconKey;
        }
        return null;
    }

    private static InventoryItemDTO buildInventoryItem(AEKey key, long amount, boolean craftable, PrerenderStorageService storage) {
        if (key == null) {
            return null;
        }
        if (!(key instanceof AEItemKey) && !(key instanceof AEFluidKey)) {
            return null;
        }

        String iconKey = IconKeyUtils.iconKey(key);
        String type = (key instanceof AEFluidKey) ? "fluid" : "item";
        String uniqueId = type + ":" + iconKey;
        String id = key.getId().toString();
        String namespace = key.getId().getNamespace();

        String translationKey;
        String name;
        List<String> tags = null;
        if (key instanceof AEItemKey itemKey) {
            translationKey = itemKey.getItem().getDescriptionId();
            name = com.kokoda.ae2web.web.inventory.InventoryNameUtils.getTranslatedName(itemKey, storage);
            tags = itemKey.toStack().getTags()
                    .map(tagKey -> tagKey.location().toString())
                    .sorted()
                    .toList();
        } else {
            AEFluidKey fluidKey = (AEFluidKey) key;
            translationKey = fluidKey.getFluid().getFluidType().getDescriptionId();
            name = com.kokoda.ae2web.web.inventory.InventoryNameUtils.getFluidName(fluidKey, storage);
        }

        return new InventoryItemDTO(type, id, uniqueId, iconKey, tags, name, translationKey, amount, namespace, craftable);
    }

    private static final class TerminalInventoryState {
        private final Object lock = new Object();
        private final Map<String, InventoryItemDTO> items = new HashMap<>();
        private final Set<String> craftableUniqueIds = new HashSet<>();
        private final Map<String, PendingInventoryDelta> pending = new HashMap<>();
        private ScheduledFuture<?> flushFuture;
        private boolean initialized = false;
    }

    private static final class PendingInventoryDelta {
        private AEKey key;
        private Long amount;
        private Boolean craftable;
        private String name;

        private PendingInventoryDelta(AEKey key) {
            this.key = key;
        }

        private void merge(AEKey nextKey, Long nextAmount, Boolean nextCraftable, String nextName) {
            if (nextKey != null) {
                this.key = nextKey;
            }
            if (nextAmount != null) {
                this.amount = nextAmount;
            }
            if (nextCraftable != null) {
                this.craftable = nextCraftable;
            }
            if (nextName != null) {
                this.name = nextName;
            }
        }
    }

    private static final class InventoryPatch {
        private final String uniqueId;
        private final long amount;
        private final boolean craftable;
        private final String name;

        private InventoryPatch(String uniqueId, long amount, boolean craftable, String name) {
            this.uniqueId = uniqueId;
            this.amount = amount;
            this.craftable = craftable;
            this.name = name;
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public long getAmount() {
            return amount;
        }

        public boolean isCraftable() {
            return craftable;
        }

        public String getName() {
            return name;
        }
    }

    private static final class ApiChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            // Allow larger JSON payloads for prerender claim batches (64 KiB is too small and can trigger HTTP 413).
            pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
            pipeline.addLast(new ApiHandler());
        }
    }
}
