package com.kokoda.ae2web.web.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionHistoryServiceTest {
    private static final String TERMINAL = "t1";
    private static final String ITEM = "item:minecraft:iron_ingot";

    @TempDir
    Path tempDir;

    private List<ProductionHistoryService.DataPoint> points(ProductionHistoryService service) {
        return service.getHistory(TERMINAL, ITEM, 0).points();
    }

    @Test
    void changeInsideThrottleWindowIsKeptAsTrailingPoint() {
        ProductionHistoryService service = new ProductionHistoryService(tempDir);

        service.recordDataPoint(TERMINAL, ITEM, 100, 0);
        service.recordDataPoint(TERMINAL, ITEM, 150, 60_000);
        service.recordDataPoint(TERMINAL, ITEM, 0, 90_000);

        assertThat(points(service)).containsExactly(
                new ProductionHistoryService.DataPoint(0, 100),
                new ProductionHistoryService.DataPoint(60_000, 150),
                new ProductionHistoryService.DataPoint(90_000, 0));
    }

    @Test
    void repeatedChangesInsideWindowReplaceTheTrailingPoint() {
        ProductionHistoryService service = new ProductionHistoryService(tempDir);

        service.recordDataPoint(TERMINAL, ITEM, 100, 0);
        service.recordDataPoint(TERMINAL, ITEM, 110, 10_000);
        service.recordDataPoint(TERMINAL, ITEM, 120, 20_000);
        service.recordDataPoint(TERMINAL, ITEM, 130, 30_000);

        assertThat(points(service)).containsExactly(
                new ProductionHistoryService.DataPoint(0, 100),
                new ProductionHistoryService.DataPoint(30_000, 130));
    }

    @Test
    void returningToAnchorAmountInsideWindowDropsTrailingPoint() {
        ProductionHistoryService service = new ProductionHistoryService(tempDir);

        service.recordDataPoint(TERMINAL, ITEM, 100, 0);
        service.recordDataPoint(TERMINAL, ITEM, 150, 10_000);
        service.recordDataPoint(TERMINAL, ITEM, 100, 20_000);

        assertThat(points(service)).containsExactly(new ProductionHistoryService.DataPoint(0, 100));
    }

    @Test
    void changeAfterWindowStartsNewAnchor() {
        ProductionHistoryService service = new ProductionHistoryService(tempDir);

        service.recordDataPoint(TERMINAL, ITEM, 100, 0);
        service.recordDataPoint(TERMINAL, ITEM, 150, 30_000);
        service.recordDataPoint(TERMINAL, ITEM, 200, 70_000);
        service.recordDataPoint(TERMINAL, ITEM, 210, 80_000);

        assertThat(points(service)).containsExactly(
                new ProductionHistoryService.DataPoint(0, 100),
                new ProductionHistoryService.DataPoint(30_000, 150),
                new ProductionHistoryService.DataPoint(70_000, 200),
                new ProductionHistoryService.DataPoint(80_000, 210));
    }

    @Test
    void unchangedAmountIsNotRecorded() {
        ProductionHistoryService service = new ProductionHistoryService(tempDir);

        service.recordDataPoint(TERMINAL, ITEM, 100, 0);
        service.recordDataPoint(TERMINAL, ITEM, 100, 120_000);

        assertThat(points(service)).containsExactly(new ProductionHistoryService.DataPoint(0, 100));
    }
}
