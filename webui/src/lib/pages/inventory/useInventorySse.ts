import type { InventoryItem } from '$lib/api/types';
import { openInventorySse } from '$lib/api/inventory';
import { ProductionStatsTracker } from '$lib/utils/productionStats';
import { loadTerminalHistory, queueHistoryUpdate } from '$lib/storage/productionHistory';

type InventorySseStateAccess = {
	get inventoryState(): Map<string, InventoryItem>;
	set inventoryState(value: Map<string, InventoryItem>);

	get inventoryStateDirty(): boolean;
	set inventoryStateDirty(value: boolean);

	get inventoryStateFlushInFlight(): boolean;
	set inventoryStateFlushInFlight(value: boolean);

	get inventoryStateFlushPending(): boolean;
	set inventoryStateFlushPending(value: boolean);

	get inventoryScrollActive(): boolean;
	set inventoryScrollActive(value: boolean);
};

export type InventorySseController = {
	start(): void;
	stop(): void;
	getVersion(): number | null;
	onScroll(): void;
	getProductionRate(uniqueId: string): number | null;
	onChartViewed(uniqueId: string): void;
	destroy(): void;
};

const RATE_CACHE_TTL = 200;
const SCROLL_DEBOUNCE_MS = 150;

export function createInventorySseController({
	state,
	getTerminalId,
	productionStats,
	chartViewedItems,
	productionRateCache,
	flushInventoryState,
	showToast
}: {
	state: InventorySseStateAccess;
	getTerminalId(): string | null | undefined;
	productionStats: ProductionStatsTracker;
	chartViewedItems: Set<string>;
	productionRateCache: Map<string, { rate: number | null; timestamp: number }>;
	flushInventoryState(): Promise<void>;
	showToast(message: string, kind: 'success' | 'error'): void;
}): InventorySseController {
	let eventSource: ReturnType<typeof openInventorySse> | null = null;
	let lastVersion: number | null = null;
	let scrollTimer: ReturnType<typeof setTimeout> | null = null;

	// Flushes are skipped while the tab is hidden; catch up as soon as it becomes visible again.
	function onVisibilityChange(): void {
		if (!document.hidden && state.inventoryStateDirty) {
			void flushInventoryState();
		}
	}

	function stop(): void {
		lastVersion = null;
		if (typeof document !== 'undefined') {
			document.removeEventListener('visibilitychange', onVisibilityChange);
		}
		state.inventoryStateDirty = false;
		state.inventoryStateFlushInFlight = false;
		state.inventoryStateFlushPending = false;

		if (scrollTimer) {
			clearTimeout(scrollTimer);
			scrollTimer = null;
		}

		if (eventSource) {
			try {
				eventSource.close();
			} catch (e) {
				console.debug('[useInventorySse] Exception while closing EventSource:', e);
			}
			eventSource = null;
		}
	}

	function start(): void {
		stop();

		const terminalId = getTerminalId();
		if (!terminalId) return;

		if (typeof document !== 'undefined') {
			document.addEventListener('visibilitychange', onVisibilityChange);
		}

		eventSource = openInventorySse(terminalId, {
			onHello: (msg) => {
				lastVersion = msg.version;
			},
			onSnapshot: (msg) => {
				const currentTerminalId = getTerminalId();
				if (msg.terminalId !== currentTerminalId) return;

				state.inventoryState = new Map(msg.items.map((item) => [item.uniqueId, item]));
				state.inventoryStateDirty = true;
				lastVersion = msg.version;

				productionStats.clearTerminal(currentTerminalId);
				productionRateCache.clear();

				void loadTerminalHistory(currentTerminalId).then((historyMap) => {
					const now = Date.now();

					for (const [uniqueId, points] of historyMap) {
						productionStats.loadHistoricalPoints(currentTerminalId, uniqueId, points);
						chartViewedItems.add(uniqueId);
					}

					for (const item of msg.items) {
						const entry = productionStats.getEntry(currentTerminalId, item.uniqueId);
						if (!entry) {
							productionStats.resetBaseline(currentTerminalId, item.uniqueId, item.amount, now);
						} else if (entry.lastPoint.amount !== item.amount) {
							productionStats.recordUpdate(currentTerminalId, item.uniqueId, item.amount, now);
						}
					}
				});

				void flushInventoryState();
			},
			onDelta: (msg) => {
				const currentTerminalId = getTerminalId();
				if (msg.terminalId !== currentTerminalId) return;
				if (lastVersion == null) return;
				if (msg.version !== lastVersion + 1) {
					showToast('Inventory stream out of sync, resyncing...', 'error');
					start();
					return;
				}

				lastVersion = msg.version;
				const now = Date.now();
				const inventoryState = state.inventoryState;

				for (const id of msg.removed) {
					inventoryState.delete(id);
					productionStats.resetBaseline(currentTerminalId, id, 0, now);
				}

				for (const item of msg.added) {
					inventoryState.set(item.uniqueId, item);
					productionStats.resetBaseline(currentTerminalId, item.uniqueId, item.amount, now);
				}

				for (const patch of msg.updated) {
					const prev = inventoryState.get(patch.uniqueId);
					if (!prev) continue;

					if (prev.amount !== patch.amount) {
						productionStats.recordUpdate(currentTerminalId, patch.uniqueId, patch.amount, now);

						const newRate = productionStats.getRate(currentTerminalId, patch.uniqueId, now);
						productionRateCache.set(patch.uniqueId, { rate: newRate, timestamp: now });

						if (chartViewedItems.has(patch.uniqueId)) {
							const points = productionStats.getPoints(currentTerminalId, patch.uniqueId);
							if (points) {
								queueHistoryUpdate(currentTerminalId, patch.uniqueId, points);
							}
						}
					}

					const nextName = patch.name ?? prev.name;
					inventoryState.set(patch.uniqueId, {
						...prev,
						amount: patch.amount,
						craftable: patch.craftable,
						name: nextName
					});
				}

				state.inventoryStateDirty = true;
				void flushInventoryState();
			}
		});
	}

	function getVersion(): number | null {
		return lastVersion;
	}

	function onScroll(): void {
		state.inventoryScrollActive = true;

		if (scrollTimer) {
			clearTimeout(scrollTimer);
		}

		scrollTimer = setTimeout(() => {
			scrollTimer = null;
			state.inventoryScrollActive = false;
			if (state.inventoryStateDirty && typeof document !== 'undefined' && !document.hidden) {
				void flushInventoryState();
			}
		}, SCROLL_DEBOUNCE_MS);
	}

	function getProductionRate(uniqueId: string): number | null {
		const terminalId = getTerminalId();
		if (!terminalId) return null;

		const now = Date.now();
		const cached = productionRateCache.get(uniqueId);

		if (cached && now - cached.timestamp < RATE_CACHE_TTL) {
			return cached.rate;
		}

		const rate = productionStats.getRate(terminalId, uniqueId, now);
		productionRateCache.set(uniqueId, { rate, timestamp: now });
		return rate;
	}

	function onChartViewed(uniqueId: string): void {
		chartViewedItems.add(uniqueId);
	}

	function destroy(): void {
		stop();
	}

	return {
		start,
		stop,
		getVersion,
		onScroll,
		getProductionRate,
		onChartViewed,
		destroy
	};
}
