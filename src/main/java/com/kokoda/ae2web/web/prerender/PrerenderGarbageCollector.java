package com.kokoda.ae2web.web.prerender;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.util.IconKeyUtils;
import com.kokoda.ae2web.util.PrerenderUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class PrerenderGarbageCollector {
    private final SeenStorageService seen;
    private final IconStorageService icons;
    private final TranslationStorageService translations;
    private final TooltipStorageService tooltips;
    private final PrerenderLockManager locks;
    private final Runnable ensureInitialized;

    private final Object maintenanceLock = new Object();
    private volatile ScheduledExecutorService maintenanceExecutor = null;

    PrerenderGarbageCollector(
            SeenStorageService seen,
            IconStorageService icons,
            TranslationStorageService translations,
            TooltipStorageService tooltips,
            PrerenderLockManager locks,
            Runnable ensureInitialized
    ) {
        this.seen = seen;
        this.icons = icons;
        this.translations = translations;
        this.tooltips = tooltips;
        this.locks = locks;
        this.ensureInitialized = ensureInitialized;
    }

    void startMaintenance() {
        if (maintenanceExecutor != null && !maintenanceExecutor.isShutdown()) {
            return;
        }

        synchronized (maintenanceLock) {
            if (maintenanceExecutor != null && !maintenanceExecutor.isShutdown()) {
                return;
            }

            int intervalSeconds = AE2WebConfig.getPrerenderGcIntervalSeconds();
            if (intervalSeconds <= 0) {
                return;
            }

            maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "AE2Web-PrerenderMaintenance");
                t.setDaemon(true);
                return t;
            });

            maintenanceExecutor.scheduleAtFixedRate(() -> {
                try {
                    runMaintenanceOnce();
                } catch (Exception e) {
                    AE2WebMod.LOGGER.warn("Prerender maintenance failed", e);
                }
            }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    void shutdownMaintenance() {
        ScheduledExecutorService executor = maintenanceExecutor;
        maintenanceExecutor = null;
        if (executor != null) {
            executor.shutdown();
        }
        seen.save(true);
    }

    private void runMaintenanceOnce() {
        ensureInitialized.run();

        int deleted = gcExpiredIcons();
        if (deleted > 0) {
            AE2WebMod.LOGGER.info("Prerender GC: deleted {} expired NBT-variant icons", deleted);
        }

        seen.save(false);
    }

    private int gcExpiredIcons() {
        int ttlSeconds = AE2WebConfig.getPrerenderGcTtlSeconds();
        if (ttlSeconds <= 0) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - (ttlSeconds * 1000L);

        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Long> entry : seen.getLastSeenMap().entrySet()) {
            Long ts = entry.getValue();
            if (ts == null || ts <= 0) continue;

            String iconKey = entry.getKey();
            if (iconKey == null || iconKey.isBlank()) continue;

            boolean locked = locks.isLocked(iconKey);
            boolean baseVariant = isBaseVariant(iconKey);
            if (!PrerenderUtils.shouldGc(ts, cutoff, locked, baseVariant)) {
                continue;
            }

            candidates.add(iconKey);
        }

        if (candidates.isEmpty()) {
            return 0;
        }

        int deleted = 0;
        boolean translationsChanged = false;
        boolean tooltipsChanged = false;

        for (String iconKey : candidates) {
            Long currentSeen = seen.getLastSeenMap().get(iconKey);
            if (currentSeen == null || currentSeen <= 0) continue;

            boolean locked = locks.isLocked(iconKey);
            boolean baseVariant = isBaseVariant(iconKey);
            if (!PrerenderUtils.shouldGc(currentSeen, cutoff, locked, baseVariant)) {
                continue;
            }

            if (icons.deleteIcon(iconKey)) {
                deleted++;
            }
            if (translations.remove(iconKey)) {
                translationsChanged = true;
            }
            if (tooltips.remove(iconKey)) {
                tooltipsChanged = true;
            }
            seen.remove(iconKey);
        }

        if (translationsChanged) {
            translations.save();
        }
        if (tooltipsChanged) {
            tooltips.save();
        }

        seen.save(false);
        return deleted;
    }

    private boolean isBaseVariant(String iconKey) {
        int at = iconKey.lastIndexOf('@');
        if (at <= 0) {
            return true;
        }

        String idStr = iconKey.substring(0, at);
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            return true;
        }

        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null) {
            String base = IconKeyUtils.iconKey(AEItemKey.of(item));
            return iconKey.equals(base);
        }

        var fluid = ForgeRegistries.FLUIDS.getValue(id);
        if (fluid != null && fluid != Fluids.EMPTY) {
            String base = IconKeyUtils.iconKey(AEFluidKey.of(fluid));
            return iconKey.equals(base);
        }

        return true;
    }
}

