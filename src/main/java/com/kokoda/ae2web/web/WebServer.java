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
import java.util.concurrent.ConcurrentHashMap;

public final class WebServer {
    private static final WebServer instance = new WebServer();

    private final Object lifecycleLock = new Object();
    private final Map<String, WebTerminalBlockEntity> terminals = new ConcurrentHashMap<>();
    private volatile EventLoopGroup bossGroup;
    private volatile EventLoopGroup workerGroup;
    private volatile Channel serverChannel;
    private volatile Thread serverThread;
    private volatile boolean running;

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

            int port = AE2WebConfig.getHttpPort();
            serverThread = new Thread(() -> runServer(port), "AE2Web-HTTP");
            serverThread.setDaemon(true);
            serverThread.start();
        }
    }

    private void runServer(int port) {
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

            Channel channel = bootstrap.bind(port).sync().channel();
            synchronized (lifecycleLock) {
                serverChannel = channel;
                running = true;
            }

            AE2WebMod.LOGGER.info("AE2 Web Terminal HTTP server started on port {}", port);
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

        synchronized (lifecycleLock) {
            running = false;
            terminals.clear();

            if (serverChannel == null && bossGroup == null && workerGroup == null) {
                return;
            }

            localServerChannel = serverChannel;
            localWorkerGroup = workerGroup;
            localBossGroup = bossGroup;

            serverChannel = null;
            workerGroup = null;
            bossGroup = null;
        }

        if (localServerChannel != null) {
            localServerChannel.close();
        }

        if (localWorkerGroup != null) {
            localWorkerGroup.shutdownGracefully();
        }

        if (localBossGroup != null) {
            localBossGroup.shutdownGracefully();
        }

        AE2WebMod.LOGGER.info("AE2 Web Terminal HTTP server stopped");
    }

    public void registerTerminal(WebTerminalBlockEntity terminal) {
        terminals.put(terminal.getTerminalId(), terminal);
        AE2WebMod.LOGGER.debug("Registered terminal: {}", terminal.getTerminalId());
    }

    public void unregisterTerminal(WebTerminalBlockEntity terminal) {
        terminals.remove(terminal.getTerminalId());
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

    private static final class ApiChannelInitializer extends ChannelInitializer<SocketChannel> {
        @Override
        protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            pipeline.addLast(new ApiHandler());
        }
    }
}
