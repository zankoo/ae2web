package com.kokoda.ae2web.web.prerender;

import com.kokoda.ae2web.AE2WebMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PrerenderLockManager {
    private static final long LOCK_TTL_MS = 60000; // 1 minute
    private static final long LOCK_CLEANUP_INTERVAL_MS = 30000; // 30 seconds

    private record LockInfo(String clientId, long expireTime) {
    }

    private final Map<String, LockInfo> renderingLocks = new ConcurrentHashMap<>();
    private volatile long lastLockCleanup = 0;

    PrerenderStorageService.ClaimResult claimItemsForRendering(
            List<String> requestedIds,
            String clientId,
            IconStorageService icons
    ) {
        long now = System.currentTimeMillis();
        List<String> claimed = new ArrayList<>();
        int alreadyRendered = 0;
        int lockedBySelf = 0;
        int lockedByOthers = 0;

        cleanExpiredLocksIfNeeded(now);

        for (String id : requestedIds) {
            if (icons.hasIcon(id)) {
                alreadyRendered++;
                continue;
            }

            final int[] result = {0}; // 0=claimed, 1=lockedBySelf, 2=lockedByOthers
            renderingLocks.compute(id, (key, existing) -> {
                if (existing != null && existing.expireTime() >= now) {
                    if (existing.clientId().equals(clientId)) {
                        result[0] = 1;
                        return new LockInfo(clientId, now + LOCK_TTL_MS);
                    }
                    result[0] = 2;
                    return existing;
                }

                result[0] = 0;
                return new LockInfo(clientId, now + LOCK_TTL_MS);
            });

            switch (result[0]) {
                case 0 -> claimed.add(id);
                case 1 -> {
                    claimed.add(id);
                    lockedBySelf++;
                }
                case 2 -> lockedByOthers++;
            }
        }

        if (!claimed.isEmpty()) {
            AE2WebMod.LOGGER.debug("Client {} claimed {} items for rendering", clientId, claimed.size());
        }

        return new PrerenderStorageService.ClaimResult(claimed, alreadyRendered, lockedBySelf, lockedByOthers);
    }

    void releaseLock(String iconKey) {
        renderingLocks.remove(iconKey);
    }

    boolean isLocked(String iconKey) {
        return renderingLocks.containsKey(iconKey);
    }

    int activeLocks() {
        return renderingLocks.size();
    }

    void clear() {
        renderingLocks.clear();
    }

    private void cleanExpiredLocksIfNeeded(long now) {
        if (now - lastLockCleanup <= LOCK_CLEANUP_INTERVAL_MS) {
            return;
        }
        lastLockCleanup = now;
        renderingLocks.entrySet().removeIf(e -> e.getValue().expireTime() < now);
    }
}

