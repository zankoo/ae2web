package com.kokoda.ae2web.web.handlers.crafting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingExecutorsTest {
    @AfterEach
    void tearDown() {
        CraftingExecutors.shutdown();
    }

    @Test
    void getCreatesDaemonThreadsWithExpectedName() throws Exception {
        ExecutorService executor = CraftingExecutors.get();

        Future<String> name = executor.submit(() -> Thread.currentThread().getName());
        Future<Boolean> daemon = executor.submit(() -> Thread.currentThread().isDaemon());

        assertThat(name.get(2, TimeUnit.SECONDS)).startsWith("AE2Web-Crafting");
        assertThat(daemon.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void getReturnsNewExecutorAfterShutdown() {
        ExecutorService first = CraftingExecutors.get();
        CraftingExecutors.shutdown();

        ExecutorService second = CraftingExecutors.get();
        assertThat(second).isNotSameAs(first);
    }
}

