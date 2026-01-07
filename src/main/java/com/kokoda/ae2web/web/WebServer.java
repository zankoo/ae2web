package com.kokoda.ae2web.web;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.handlers.ApiHandler;
import com.kokoda.ae2web.web.history.ProductionHistoryService;
import com.kokoda.ae2web.web.sse.InventorySseService;
import com.kokoda.ae2web.web.terminal.TerminalRegistry;
import appeng.api.stacks.AEKey;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Singleton Netty HTTP server for the AE2 Web Terminal.
 * Delegates terminal registration to TerminalRegistry and SSE handling to InventorySseService.
 */
public final class WebServer {
    private static final WebServer instance = new WebServer();

    private final Object lifecycleLock = new Object();
    private final Object ioExecutorLock = new Object();
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
            InventorySseService.getInstance().setExecutor(executor);

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
            TerminalRegistry.getInstance().clear();
            InventorySseService.getInstance().clear();

            if (serverChannel == null && bossGroup == null && workerGroup == null) {
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
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("Exception while closing server channel", e);
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
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("Exception while shutting down event loop group", e);
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

    public boolean isRunning() {
        return running;
    }

    // Terminal delegation methods (backward compatibility)

    public void registerTerminal(WebTerminalBlockEntity terminal) {
        TerminalRegistry.getInstance().register(terminal);
    }

    public void unregisterTerminal(WebTerminalBlockEntity terminal) {
        TerminalRegistry.getInstance().unregister(terminal);
    }

    public void unregisterTerminalById(String terminalId) {
        TerminalRegistry.getInstance().unregisterById(terminalId);
        InventorySseService.getInstance().unregisterTerminal(terminalId);
    }

    public Map<String, WebTerminalBlockEntity> getTerminals() {
        return TerminalRegistry.getInstance().getAll();
    }

    public WebTerminalBlockEntity getTerminal(String id) {
        return TerminalRegistry.getInstance().get(id);
    }

    public WebTerminalBlockEntity getFirstTerminal() {
        return TerminalRegistry.getInstance().getFirst();
    }

    // SSE delegation methods (backward compatibility)

    public boolean hasInventorySubscribers(String terminalId) {
        return InventorySseService.getInstance().hasSubscribers(terminalId);
    }

    public long getInventoryVersion(String terminalId) {
        return InventorySseService.getInstance().getVersion(terminalId);
    }

    public void subscribeInventory(String terminalId, Channel channel) {
        InventorySseService.getInstance().subscribe(terminalId, channel);
    }

    public void unsubscribeInventory(String terminalId, Channel channel) {
        InventorySseService.getInstance().unsubscribe(terminalId, channel);
    }

    public void notifyInventoryChanged(String terminalId) {
        InventorySseService.getInstance().notifyChanged(terminalId);
    }

    public void queueInventoryDelta(String terminalId, AEKey key, Long amount, Boolean craftable) {
        InventorySseService.getInstance().queueDelta(terminalId, key, amount, craftable);
    }

    public void queueInventoryNameRefresh(String iconKey, String translatedName) {
        InventorySseService.getInstance().queueNameRefresh(iconKey, translatedName);
    }

    public void sendInventorySnapshot(Channel channel, String terminalId) {
        InventorySseService.getInstance().sendSnapshot(channel, terminalId);
    }

    private static final class ApiChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
            pipeline.addLast(new ApiHandler());
        }
    }
}
