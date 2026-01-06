package com.kokoda.ae2web.web.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kokoda.ae2web.AE2WebMod;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Service for storing and retrieving production history data.
 * Stores item quantity snapshots over time for trend analysis.
 */
public final class ProductionHistoryService {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ProductionHistoryService INSTANCE = new ProductionHistoryService(resolveDefaultRoot());

    /** Maximum history duration (7 days) */
    private static final long MAX_HISTORY_MS = 7L * 24 * 60 * 60 * 1000;

    /** Minimum interval between data points (1 minute) */
    private static final long MIN_INTERVAL_MS = 60 * 1000;

    /** Save interval (5 minutes) */
    private static final long SAVE_INTERVAL_MS = 5 * 60 * 1000;

    /** Cleanup interval (1 hour) */
    private static final long CLEANUP_INTERVAL_MS = 60 * 60 * 1000;

    private final Path rootDir;
    private final Object ioLock = new Object();
    private final Object dirtyLock = new Object();

    /** terminalId -> uniqueId -> List<DataPoint> */
    private final Map<String, Map<String, List<DataPoint>>> historyData = new ConcurrentHashMap<>();

    /** terminalId -> uniqueId -> lastRecordedTimestamp */
    private final Map<String, Map<String, Long>> lastRecordTime = new ConcurrentHashMap<>();

    private boolean dirty = false;
    private @Nullable ScheduledFuture<?> saveTask;
    private @Nullable ScheduledFuture<?> cleanupTask;

    public record DataPoint(long timestamp, long amount) {}

    public record HistoryResponse(String terminalId, String uniqueId, List<DataPoint> points) {}

    public ProductionHistoryService(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    }

    public static ProductionHistoryService getInstance() {
        return INSTANCE;
    }

    /**
     * Start background tasks for periodic saving and cleanup.
     */
    public void start(ScheduledExecutorService executor) {
        if (saveTask != null) {
            return;
        }

        loadAllData();
        cleanupOldData();
        saveIfDirty();

        saveTask = executor.scheduleAtFixedRate(
                this::saveIfDirty,
                SAVE_INTERVAL_MS,
                SAVE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        cleanupTask = executor.scheduleAtFixedRate(
                this::cleanupOldData,
                CLEANUP_INTERVAL_MS,
                CLEANUP_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stop background tasks and save data.
     */
    public void stop() {
        if (saveTask != null) {
            saveTask.cancel(false);
            saveTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
            cleanupTask = null;
        }
        saveIfDirty();
    }

    /**
     * Record a data point for an item.
     * Only records if enough time has passed since last record.
     */
    public void recordDataPoint(String terminalId, String uniqueId, long amount, long timestamp) {
        if (terminalId == null || terminalId.isBlank() || uniqueId == null || uniqueId.isBlank()) {
            return;
        }

        Map<String, Long> terminalLastRecords = lastRecordTime
                .computeIfAbsent(terminalId, k -> new ConcurrentHashMap<>());

        Map<String, List<DataPoint>> terminalHistory = historyData
                .computeIfAbsent(terminalId, k -> new ConcurrentHashMap<>());

        List<DataPoint> points = terminalHistory.computeIfAbsent(uniqueId, k -> new ArrayList<>());

        synchronized (points) {
            Long lastTime = terminalLastRecords.get(uniqueId);
            if (lastTime != null && timestamp - lastTime < MIN_INTERVAL_MS) {
                return;
            }

            // Skip if same amount as last point
            if (!points.isEmpty() && points.get(points.size() - 1).amount() == amount) {
                return;
            }

            points.add(new DataPoint(timestamp, amount));
            terminalLastRecords.put(uniqueId, timestamp);
            markDirty();
        }
    }

    /**
     * Get history for a specific item.
     */
    public HistoryResponse getHistory(String terminalId, String uniqueId, long fromTimestamp) {
        Map<String, List<DataPoint>> terminalHistory = historyData.get(terminalId);
        if (terminalHistory == null) {
            return new HistoryResponse(terminalId, uniqueId, Collections.emptyList());
        }

        List<DataPoint> points = terminalHistory.get(uniqueId);
        if (points == null || points.isEmpty()) {
            return new HistoryResponse(terminalId, uniqueId, Collections.emptyList());
        }

        List<DataPoint> filtered;
        synchronized (points) {
            if (fromTimestamp <= 0) {
                filtered = new ArrayList<>(points);
            } else {
                filtered = new ArrayList<>();
                for (DataPoint p : points) {
                    if (p.timestamp() >= fromTimestamp) {
                        filtered.add(p);
                    }
                }
            }
        }

        return new HistoryResponse(terminalId, uniqueId, filtered);
    }

    /**
     * Get history for multiple items.
     */
    public List<HistoryResponse> getHistoryBatch(String terminalId, List<String> uniqueIds, long fromTimestamp) {
        List<HistoryResponse> results = new ArrayList<>();
        for (String uniqueId : uniqueIds) {
            results.add(getHistory(terminalId, uniqueId, fromTimestamp));
        }
        return results;
    }

    /**
     * Clear history for a terminal.
     */
    public void clearTerminal(String terminalId) {
        historyData.remove(terminalId);
        lastRecordTime.remove(terminalId);
        markDirty();
    }

    /**
     * Clear all history.
     */
    public void clearAll() {
        historyData.clear();
        lastRecordTime.clear();
        markDirty();
    }

    private void saveIfDirty() {
        if (!consumeDirty()) return;
        if (!saveAllData()) {
            markDirty();
        }
    }

    private void cleanupOldData() {
        long cutoff = System.currentTimeMillis() - MAX_HISTORY_MS;
        boolean anyChanged = false;

        Iterator<Map.Entry<String, Map<String, List<DataPoint>>>> terminalIter = historyData.entrySet().iterator();
        while (terminalIter.hasNext()) {
            Map.Entry<String, Map<String, List<DataPoint>>> terminalEntry = terminalIter.next();
            String terminalId = terminalEntry.getKey();
            Map<String, List<DataPoint>> itemMap = terminalEntry.getValue();
            Iterator<Map.Entry<String, List<DataPoint>>> itemIter = itemMap.entrySet().iterator();

            while (itemIter.hasNext()) {
                Map.Entry<String, List<DataPoint>> itemEntry = itemIter.next();
                String uniqueId = itemEntry.getKey();
                List<DataPoint> points = itemEntry.getValue();

                synchronized (points) {
                    boolean removed = points.removeIf(p -> p.timestamp() < cutoff);
                    if (removed) {
                        anyChanged = true;
                    }
                    if (points.isEmpty()) {
                        itemIter.remove();
                        // Also clean up lastRecordTime for this item
                        Map<String, Long> terminalRecordTimes = lastRecordTime.get(terminalId);
                        if (terminalRecordTimes != null) {
                            terminalRecordTimes.remove(uniqueId);
                        }
                        anyChanged = true;
                    }
                }
            }

            // If terminal map is now empty, remove the terminal entry entirely
            if (itemMap.isEmpty()) {
                terminalIter.remove();
                lastRecordTime.remove(terminalId);
            }
        }

        if (anyChanged) {
            markDirty();
        }
    }

    private void loadAllData() {
        if (!Files.isDirectory(rootDir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(rootDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadTerminalData);
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to list history data directory", e);
        }
    }

    private void loadTerminalData(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return;
        }

        String terminalId = fileName.substring(0, fileName.length() - 5);

        synchronized (ioLock) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, List<DataPoint>>>() {}.getType();
                Map<String, List<DataPoint>> data = GSON.fromJson(reader, type);

                if (data != null && !data.isEmpty()) {
                    Map<String, List<DataPoint>> terminalHistory = new ConcurrentHashMap<>();
                    Map<String, Long> terminalRecordTimes = new ConcurrentHashMap<>();

                    for (Map.Entry<String, List<DataPoint>> entry : data.entrySet()) {
                        String uniqueId = entry.getKey();
                        List<DataPoint> rawPoints = entry.getValue();
                        if (uniqueId == null || uniqueId.isBlank() || rawPoints == null || rawPoints.isEmpty()) continue;

                        List<DataPoint> points = new ArrayList<>(rawPoints.size());
                        for (DataPoint p : rawPoints) {
                            if (p != null && p.timestamp() > 0) {
                                points.add(p);
                            }
                        }

                        if (points.isEmpty()) continue;

                        points.sort(Comparator.comparingLong(DataPoint::timestamp));
                        // Deduplicate by timestamp, keep last value for the same timestamp.
                        List<DataPoint> deduped = new ArrayList<>(points.size());
                        DataPoint last = null;
                        for (DataPoint p : points) {
                            if (last != null && last.timestamp() == p.timestamp()) {
                                deduped.set(deduped.size() - 1, p);
                            } else {
                                deduped.add(p);
                            }
                            last = p;
                        }

                        terminalHistory.put(uniqueId, deduped);
                        terminalRecordTimes.put(uniqueId, deduped.get(deduped.size() - 1).timestamp());
                    }
                    if (!terminalHistory.isEmpty()) {
                        historyData.put(terminalId, terminalHistory);
                        lastRecordTime.put(terminalId, terminalRecordTimes);
                    }
                }
            } catch (Exception e) {
                AE2WebMod.LOGGER.debug("Failed to load history data for terminal {}", terminalId, e);
            }
        }
    }

    private boolean saveAllData() {
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            AE2WebMod.LOGGER.warn("Failed to create history data directory", e);
            return false;
        }

        boolean ok = true;
        Set<String> activeTerminals = new HashSet<>(historyData.keySet());
        for (Map.Entry<String, Map<String, List<DataPoint>>> terminalEntry : historyData.entrySet()) {
            ok &= saveTerminalData(terminalEntry.getKey(), terminalEntry.getValue());
        }

        ok &= deleteOrphanFiles(activeTerminals);
        return ok;
    }

    private boolean deleteOrphanFiles(Set<String> activeTerminalIds) {
        if (!Files.isDirectory(rootDir)) return true;

        synchronized (ioLock) {
            try (Stream<Path> stream = Files.list(rootDir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> {
                            String name = p.getFileName().toString();
                            String terminalId = name.substring(0, name.length() - 5);
                            if (activeTerminalIds.contains(terminalId)) return;
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                AE2WebMod.LOGGER.debug("Failed to delete orphaned history file {}", p, e);
                            }
                        });
                return true;
            } catch (IOException e) {
                AE2WebMod.LOGGER.debug("Failed to scan history data directory for orphaned files", e);
                return false;
            }
        }
    }

    private boolean saveTerminalData(String terminalId, Map<String, List<DataPoint>> data) {
        Path path = rootDir.resolve(terminalId + ".json");
        Path tmp = rootDir.resolve(terminalId + ".json.tmp");

        // Create a snapshot of data to save
        Map<String, List<DataPoint>> snapshot = new HashMap<>();
        for (Map.Entry<String, List<DataPoint>> entry : data.entrySet()) {
            List<DataPoint> points = entry.getValue();
            synchronized (points) {
                if (!points.isEmpty()) {
                    snapshot.put(entry.getKey(), new ArrayList<>(points));
                }
            }
        }

        if (snapshot.isEmpty()) {
            // Delete file if no data
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                AE2WebMod.LOGGER.debug("Failed to delete empty history file {}", path, e);
                return false;
            }
            return true;
        }

        synchronized (ioLock) {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(snapshot, writer);
            } catch (Exception e) {
                AE2WebMod.LOGGER.warn("Failed to write history data tmp file {}", tmp, e);
                return false;
            }

            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    AE2WebMod.LOGGER.warn("Failed to persist history data file {}", path, ex);
                    return false;
                }
            }
        }
        return true;
    }

    private void markDirty() {
        synchronized (dirtyLock) {
            dirty = true;
        }
    }

    private boolean consumeDirty() {
        synchronized (dirtyLock) {
            if (!dirty) return false;
            dirty = false;
            return true;
        }
    }

    private static Path resolveDefaultRoot() {
        String override = System.getProperty("ae2web.history.storageRoot");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get("config", "ae2web", "history-data");
    }
}
