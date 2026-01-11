/**
 * Tooltip Cache - localStorage persistence with LRU eviction.
 *
 * iconKey format: namespace:path@h1_<sha256hex>
 * The hash ensures content-addressability - same iconKey = same tooltip forever.
 */

const STORAGE_KEY = 'ae2web.tooltipCache';
const SCHEMA_VERSION = 1;
const MAX_ENTRIES = 8000;
const EVICT_BATCH_SIZE = 1000;
const WRITE_DEBOUNCE_MS = 500;

interface TooltipCacheEntry {
	tooltip: string[];
	accessedAt: number;
}

interface TooltipCacheData {
	schemaVersion: number;
	entries: Record<string, TooltipCacheEntry>;
}

let cachedData: TooltipCacheData | null = null;
let pendingWrite = false;
let writeTimer: ReturnType<typeof setTimeout> | null = null;

type StorageLike = {
	getItem: (key: string) => string | null;
	setItem: (key: string, value: string) => void;
	removeItem: (key: string) => void;
};

function safeStorage(): StorageLike | null {
	try {
		const storage = globalThis.localStorage as StorageLike | undefined;
		if (!storage) return null;
		if (typeof storage.getItem !== 'function') return null;
		if (typeof storage.setItem !== 'function') return null;
		// Test write capability
		storage.setItem('__test__', '1');
		storage.removeItem('__test__');
		return storage;
	} catch {
		return null;
	}
}

function loadCache(): TooltipCacheData | null {
	const storage = safeStorage();
	if (!storage) return null;

	try {
		const raw = storage.getItem(STORAGE_KEY);
		if (!raw) return null;

		const data = JSON.parse(raw) as TooltipCacheData;
		if (data.schemaVersion !== SCHEMA_VERSION) {
			storage.removeItem(STORAGE_KEY);
			return null;
		}
		return data;
	} catch {
		storage.removeItem(STORAGE_KEY);
		return null;
	}
}

function ensureCache(): TooltipCacheData {
	if (cachedData) return cachedData;

	cachedData = loadCache();
	if (!cachedData) {
		cachedData = {
			schemaVersion: SCHEMA_VERSION,
			entries: {}
		};
	}
	return cachedData;
}

function scheduleWrite(): void {
	if (pendingWrite) return;
	pendingWrite = true;

	if (writeTimer) clearTimeout(writeTimer);
	writeTimer = setTimeout(() => {
		writeTimer = null;
		pendingWrite = false;
		flushToStorage();
	}, WRITE_DEBOUNCE_MS);
}

function flushToStorage(): void {
	if (!cachedData) return;
	const storage = safeStorage();
	if (!storage) return;

	try {
		const json = JSON.stringify(cachedData);
		storage.setItem(STORAGE_KEY, json);
	} catch (e) {
		if (e instanceof DOMException && e.name === 'QuotaExceededError') {
			evictOldest(EVICT_BATCH_SIZE * 2);
			try {
				storage.setItem(STORAGE_KEY, JSON.stringify(cachedData));
			} catch {
				cachedData.entries = {};
				storage.setItem(STORAGE_KEY, JSON.stringify(cachedData));
			}
		}
	}
}

function evictOldest(count: number): void {
	if (!cachedData) return;

	const entries = Object.entries(cachedData.entries);
	if (entries.length <= count) {
		cachedData.entries = {};
		return;
	}

	entries.sort((a, b) => a[1].accessedAt - b[1].accessedAt);
	const toRemove = entries.slice(0, count);
	for (const [key] of toRemove) {
		delete cachedData.entries[key];
	}
}

/**
 * Get tooltip from localStorage cache.
 * Returns null if not found.
 */
export function getTooltipFromStorage(iconKey: string): string[] | null {
	const cache = ensureCache();
	const entry = cache.entries[iconKey];
	if (!entry) return null;

	entry.accessedAt = Date.now();
	scheduleWrite();

	return entry.tooltip;
}

/**
 * Store tooltip in localStorage cache.
 */
export function setTooltipInStorage(iconKey: string, tooltip: string[]): void {
	const cache = ensureCache();

	const entryCount = Object.keys(cache.entries).length;
	if (entryCount >= MAX_ENTRIES) {
		evictOldest(EVICT_BATCH_SIZE);
	}

	cache.entries[iconKey] = {
		tooltip,
		accessedAt: Date.now()
	};

	scheduleWrite();
}

/**
 * Clear all cached tooltips.
 */
export function clearTooltipCache(): void {
	cachedData = null;
	const storage = safeStorage();
	storage?.removeItem(STORAGE_KEY);
}

/**
 * Get cache statistics for debugging.
 */
export function getTooltipCacheStats(): {
	entryCount: number;
	oldestAccess: number | null;
} {
	if (!cachedData) {
		const loaded = loadCache();
		if (!loaded) {
			return { entryCount: 0, oldestAccess: null };
		}
		cachedData = loaded;
	}

	const entries = Object.values(cachedData.entries);
	const oldestAccess = entries.length > 0 ? Math.min(...entries.map((e) => e.accessedAt)) : null;

	return {
		entryCount: entries.length,
		oldestAccess
	};
}

// For testing: reset internal state
export function _resetForTesting(): void {
	cachedData = null;
	pendingWrite = false;
	if (writeTimer) {
		clearTimeout(writeTimer);
		writeTimer = null;
	}
}

// For testing: flush immediately without waiting for debounce
export function _flushForTesting(): void {
	if (writeTimer) {
		clearTimeout(writeTimer);
		writeTimer = null;
	}
	pendingWrite = false;
	flushToStorage();
}
