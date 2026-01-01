package com.kokoda.ae2web.web.handlers.crafting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CraftingExecutors {
    private static final Object LOCK = new Object();
    private static volatile ExecutorService executor;

    private CraftingExecutors() {}

    public static ExecutorService get() {
        ExecutorService current = executor;
        if (current != null && !current.isShutdown()) {
            return current;
        }

        synchronized (LOCK) {
            current = executor;
            if (current == null || current.isShutdown()) {
                executor = Executors.newFixedThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "AE2Web-Crafting");
                    thread.setDaemon(true);
                    return thread;
                });
            }
            return executor;
        }
    }

    public static void shutdown() {
        ExecutorService toShutdown;
        synchronized (LOCK) {
            toShutdown = executor;
            executor = null;
        }
        if (toShutdown == null) {
            return;
        }

        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                toShutdown.shutdownNow();
            }
        } catch (InterruptedException e) {
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

