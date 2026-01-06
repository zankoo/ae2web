/**
 * Production History Storage - IndexedDB persistence for production stats.
 *
 * Features:
 * - Store production history points by {terminalId, uniqueId}
 * - Throttled writes (every 2 seconds)
 * - TTL-based cleanup (24 hours)
 * - Incremental updates (not full overwrites)
 */

import type { DataPoint } from '$lib/utils/productionStats';

/**
 * Check if IndexedDB is available in the current environment.
 * Returns false in SSR, some test environments, or restricted contexts.
 */
function isIndexedDBAvailable(): boolean {
	try {
		return typeof indexedDB !== 'undefined' && indexedDB !== null;
	} catch {
		// Some security policies throw when accessing indexedDB
		return false;
	}
}

const DB_NAME = 'ae2web-production-stats';
const DB_VERSION = 1;
const STORE_NAME = 'history';

const WRITE_THROTTLE_MS = 2000;
const TTL_MS = 24 * 60 * 60 * 1000; // 24 hours

interface HistoryEntry {
	/** Composite key: `${terminalId}:${uniqueId}` */
	key: string;
	terminalId: string;
	uniqueId: string;
	points: DataPoint[];
	lastUpdated: number;
}

let db: IDBDatabase | null = null;
let dbPromise: Promise<IDBDatabase> | null = null;

const pendingWrites: Map<string, HistoryEntry> = new Map();
let writeTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * Open or get the IndexedDB database.
 * Returns null if IndexedDB is not available.
 */
async function getDb(): Promise<IDBDatabase | null> {
	if (!isIndexedDBAvailable()) {
		return null;
	}

	if (db) return db;
	if (dbPromise) return dbPromise;

	dbPromise = new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, DB_VERSION);

		request.onerror = () => {
			console.error('Failed to open production history DB:', request.error);
			reject(request.error);
		};

		request.onsuccess = () => {
			db = request.result;
			resolve(db);
		};

		request.onupgradeneeded = (event) => {
			const database = (event.target as IDBOpenDBRequest).result;

			if (!database.objectStoreNames.contains(STORE_NAME)) {
				const store = database.createObjectStore(STORE_NAME, { keyPath: 'key' });
				store.createIndex('terminalId', 'terminalId', { unique: false });
				store.createIndex('lastUpdated', 'lastUpdated', { unique: false });
			}
		};
	});

	return dbPromise;
}

/**
 * Build composite key from terminalId and uniqueId.
 */
function buildKey(terminalId: string, uniqueId: string): string {
	return `${terminalId}:${uniqueId}`;
}

/**
 * Load history for a specific item.
 */
export async function loadHistory(
	terminalId: string,
	uniqueId: string
): Promise<DataPoint[] | null> {
	try {
		const database = await getDb();
		if (!database) return null;
		const key = buildKey(terminalId, uniqueId);

		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readonly');
			const store = tx.objectStore(STORE_NAME);
			const request = store.get(key);

			request.onsuccess = () => {
				const entry = request.result as HistoryEntry | undefined;
				if (!entry) {
					resolve(null);
					return;
				}

				// Filter out expired points
				const now = Date.now();
				const validPoints = entry.points.filter((p) => now - p.timestamp < TTL_MS);
				resolve(validPoints.length > 0 ? validPoints : null);
			};

			request.onerror = () => reject(request.error);
		});
	} catch (e) {
		console.error('Failed to load production history:', e);
		return null;
	}
}

/**
 * Load all history for a terminal.
 */
export async function loadTerminalHistory(terminalId: string): Promise<Map<string, DataPoint[]>> {
	try {
		const database = await getDb();
		if (!database) return new Map();
		const result = new Map<string, DataPoint[]>();

		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readonly');
			const store = tx.objectStore(STORE_NAME);
			const index = store.index('terminalId');
			const request = index.openCursor(IDBKeyRange.only(terminalId));

			request.onsuccess = () => {
				const cursor = request.result;
				if (cursor) {
					const entry = cursor.value as HistoryEntry;
					const now = Date.now();
					const validPoints = entry.points.filter((p) => now - p.timestamp < TTL_MS);
					if (validPoints.length > 0) {
						result.set(entry.uniqueId, validPoints);
					}
					cursor.continue();
				} else {
					resolve(result);
				}
			};

			request.onerror = () => reject(request.error);
		});
	} catch (e) {
		console.error('Failed to load terminal history:', e);
		return new Map();
	}
}

/**
 * Queue a history update for throttled writing.
 */
export function queueHistoryUpdate(
	terminalId: string,
	uniqueId: string,
	points: DataPoint[]
): void {
	if (!isIndexedDBAvailable()) return;

	const key = buildKey(terminalId, uniqueId);

	// Filter to only keep points within TTL
	const now = Date.now();
	const validPoints = points.filter((p) => now - p.timestamp < TTL_MS);

	if (validPoints.length === 0) {
		// Remove from pending if exists
		pendingWrites.delete(key);
		return;
	}

	pendingWrites.set(key, {
		key,
		terminalId,
		uniqueId,
		points: validPoints,
		lastUpdated: now
	});

	scheduleWrite();
}

/**
 * Remove history for a specific item.
 */
export async function removeHistory(terminalId: string, uniqueId: string): Promise<void> {
	const key = buildKey(terminalId, uniqueId);
	pendingWrites.delete(key);

	try {
		const database = await getDb();
		if (!database) return;
		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readwrite');
			const store = tx.objectStore(STORE_NAME);
			const request = store.delete(key);

			request.onsuccess = () => resolve();
			request.onerror = () => reject(request.error);
		});
	} catch (e) {
		console.error('Failed to remove production history:', e);
	}
}

/**
 * Clear all history for a terminal.
 */
export async function clearTerminalHistory(terminalId: string): Promise<void> {
	// Clear pending writes for this terminal
	for (const [key, entry] of pendingWrites) {
		if (entry.terminalId === terminalId) {
			pendingWrites.delete(key);
		}
	}

	try {
		const database = await getDb();
		if (!database) return;
		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readwrite');
			const store = tx.objectStore(STORE_NAME);
			const index = store.index('terminalId');
			const request = index.openCursor(IDBKeyRange.only(terminalId));

			request.onsuccess = () => {
				const cursor = request.result;
				if (cursor) {
					cursor.delete();
					cursor.continue();
				} else {
					resolve();
				}
			};

			request.onerror = () => reject(request.error);
		});
	} catch (e) {
		console.error('Failed to clear terminal history:', e);
	}
}

/**
 * Run TTL cleanup on the entire database.
 */
export async function cleanupExpired(): Promise<number> {
	try {
		const database = await getDb();
		if (!database) return 0;
		const now = Date.now();
		let cleanedCount = 0;

		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readwrite');
			const store = tx.objectStore(STORE_NAME);
			const request = store.openCursor();

			request.onsuccess = () => {
				const cursor = request.result;
				if (cursor) {
					const entry = cursor.value as HistoryEntry;

					// Check if entry is too old
					if (now - entry.lastUpdated > TTL_MS) {
						cursor.delete();
						cleanedCount++;
					} else {
						// Filter points within TTL
						const validPoints = entry.points.filter((p) => now - p.timestamp < TTL_MS);
						if (validPoints.length === 0) {
							cursor.delete();
							cleanedCount++;
						} else if (validPoints.length !== entry.points.length) {
							cursor.update({
								...entry,
								points: validPoints,
								lastUpdated: now
							});
						}
					}
					cursor.continue();
				} else {
					resolve(cleanedCount);
				}
			};

			request.onerror = () => reject(request.error);
		});
	} catch (e) {
		console.error('Failed to cleanup expired history:', e);
		return 0;
	}
}

/**
 * Schedule a throttled write of pending entries.
 */
function scheduleWrite(): void {
	if (writeTimer) return;

	writeTimer = setTimeout(() => {
		writeTimer = null;
		void flushPendingWrites();
	}, WRITE_THROTTLE_MS);
}

/**
 * Flush all pending writes to IndexedDB.
 */
async function flushPendingWrites(): Promise<void> {
	if (pendingWrites.size === 0) return;

	const entries = Array.from(pendingWrites.values());
	pendingWrites.clear();

	try {
		const database = await getDb();
		if (!database) return;

		return new Promise((resolve, reject) => {
			const tx = database.transaction(STORE_NAME, 'readwrite');
			const store = tx.objectStore(STORE_NAME);

			for (const entry of entries) {
				store.put(entry);
			}

			tx.oncomplete = () => resolve();
			tx.onerror = () => reject(tx.error);
		});
	} catch (e) {
		console.error('Failed to flush production history:', e);
	}
}

/**
 * Force flush pending writes (call before page unload).
 */
export function forceFlush(): void {
	if (writeTimer) {
		clearTimeout(writeTimer);
		writeTimer = null;
	}
	void flushPendingWrites();
}
