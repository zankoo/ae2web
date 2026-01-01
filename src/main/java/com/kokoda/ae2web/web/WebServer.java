package com.kokoda.ae2web.web;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.handlers.ApiHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class WebServer {
    private static final WebServer instance = new WebServer();

    private final Object lifecycleLock = new Object();
    private final Object ioExecutorLock = new Object();
    private final Map<String, WebTerminalBlockEntity> terminals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> terminalInventoryVersions = new ConcurrentHashMap<>();
    private final Map<String, Set<Channel>> terminalInventorySubscribers = new ConcurrentHashMap<>();
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;
    private volatile Thread serverThread;
    private volatile boolean running;
    private volatile ExecutorService ioExecutor;

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

            getIoExecutor();

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
        Channel localServerChannel;
        EventLoopGroup localWorkerGroup;
        EventLoopGroup localBossGroup;
        ExecutorService localIoExecutor;

        synchronized (lifecycleLock) {
            running = false;
            terminals.clear();
            terminalInventoryVersions.clear();
            terminalInventorySubscribers.clear();

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

    public ExecutorService getIoExecutor() {
        ExecutorService current = ioExecutor;
        if (current != null && !current.isShutdown()) {
            return current;
        }

        synchronized (ioExecutorLock) {
            current = ioExecutor;
            if (current == null || current.isShutdown()) {
                ioExecutor = Executors.newFixedThreadPool(2, r -> {
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
        AE2WebMod.LOGGER.debug("Registered terminal: {}", terminal.getTerminalId());
    }

    public void unregisterTerminal(WebTerminalBlockEntity terminal) {
        terminals.remove(terminal.getTerminalId());
        Set<Channel> subs = terminalInventorySubscribers.remove(terminal.getTerminalId());
        if (subs != null) {
            for (Channel ch : subs) {
                if (ch != null) {
                    ch.eventLoop().execute(() -> {
                        if (ch.isActive()) {
                            ch.close();
                        }
                    });
                }
            }
        }
        terminalInventoryVersions.remove(terminal.getTerminalId());
        AE2WebMod.LOGGER.debug("Unregistered terminal: {}", terminal.getTerminalId());
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
            if (channel == null || !channel.isActive()) {
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
