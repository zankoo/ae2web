/**
 * Production Statistics Tracker
 *
 * Tracks inventory changes over time to calculate production/consumption rates.
 * Uses a sliding window approach for rate calculation.
 */

/** Window size for rate calculation (30 seconds) */
export const WINDOW_MS = 30_000;

/** Data is considered stale after this duration (10 seconds) */
export const STALE_MS = 10_000;

/** Maximum number of points to keep in window */
export const MAX_WINDOW_POINTS = 100;

export interface DataPoint {
	timestamp: number;
	amount: number;
}

export interface RatePoint {
	timestamp: number;
	rate: number;
}

/**
 * Derive a time series of production/consumption rates from inventory data points.
 *
 * For each point (starting at index 1), calculates the slope against the oldest point
 * within the sliding window. If no earlier point is within the window, falls back to
 * using the most recent earlier point.
 *
 * Rates are returned in the same unit as `amount` per second:
 * - items: items/s
 * - fluids: mB/s (milliBuckets/s)
 */
export function deriveRateSeries(points: DataPoint[], windowMs: number = WINDOW_MS): RatePoint[] {
	if (!points || points.length < 2) return [];

	const series: RatePoint[] = [];
	let oldestIdx = 0;

	for (let i = 1; i < points.length; i++) {
		const point = points[i];
		const cutoff = point.timestamp - windowMs;

		// Maintain oldestIdx as the oldest usable point within the window, but never beyond i - 1.
		while (oldestIdx < i - 1 && points[oldestIdx].timestamp < cutoff) {
			oldestIdx++;
		}

		const oldest = points[oldestIdx];
		const dtSeconds = (point.timestamp - oldest.timestamp) / 1000;
		if (dtSeconds <= 0) continue;

		series.push({
			timestamp: point.timestamp,
			rate: (point.amount - oldest.amount) / dtSeconds
		});
	}

	return series;
}

export interface ProductionEntry {
	lastPoint: DataPoint;
	windowPoints: DataPoint[];
}

/**
 * Tracks production/consumption rates for inventory items across terminals.
 * Key structure: terminalId -> uniqueId -> ProductionEntry
 */
/**
 * Format production rate for display.
 * @param rate Rate per second
 * @param isFluid Whether the item is a fluid (affects unit display)
 * @returns Formatted string like "+12.3/s" or "-1.5B/m", or null if below threshold
 */
export function formatProductionRate(rate: number | null, isFluid: boolean): string | null {
	if (rate === null || rate === 0) {
		return null;
	}

	// Threshold: items 0.1/s, fluids 100mB/s (0.1B/s)
	const threshold = isFluid ? 100 : 0.1;
	const absRate = Math.abs(rate);

	if (absRate < threshold) {
		return null;
	}

	const sign = rate > 0 ? '+' : '';

	if (isFluid) {
		// Fluid: convert mB/s to B/s for display
		const rateInBuckets = rate / 1000;
		return `${sign}${rateInBuckets.toFixed(1)}B/s`;
	} else {
		// Items
		if (absRate >= 1) {
			// >= 1/s: show as X.X/s
			if (absRate >= 1000) {
				return `${sign}${(rate / 1000).toFixed(1)}K/s`;
			}
			return `${sign}${rate.toFixed(1)}/s`;
		} else {
			// < 1/s: convert to per minute
			const ratePerMin = rate * 60;
			return `${sign}${ratePerMin.toFixed(1)}/m`;
		}
	}
}

export class ProductionStatsTracker {
	private entries: Map<string, Map<string, ProductionEntry>> = new Map();

	/**
	 * Record an inventory update for an item.
	 * Only records if amount has changed and is valid.
	 */
	recordUpdate(terminalId: string, uniqueId: string, amount: number, timestamp: number): void {
		// Ignore invalid amounts
		if (amount < 0) {
			return;
		}

		let terminalEntries = this.entries.get(terminalId);
		if (!terminalEntries) {
			terminalEntries = new Map();
			this.entries.set(terminalId, terminalEntries);
		}

		let entry = terminalEntries.get(uniqueId);

		if (!entry) {
			// First update for this item
			const point: DataPoint = { timestamp, amount };
			entry = {
				lastPoint: point,
				windowPoints: [point]
			};
			terminalEntries.set(uniqueId, entry);
			return;
		}

		// Skip if amount hasn't changed
		if (entry.lastPoint.amount === amount) {
			return;
		}

		// Skip if timestamp is same as last point (avoid dt=0)
		if (entry.lastPoint.timestamp === timestamp) {
			return;
		}

		const point: DataPoint = { timestamp, amount };
		entry.lastPoint = point;
		entry.windowPoints.push(point);

		// Prune window
		this.pruneWindow(entry, timestamp);
	}

	/**
	 * Reset baseline for an item (used for added/removed events).
	 * If amount is 0, removes the entry entirely.
	 */
	resetBaseline(terminalId: string, uniqueId: string, amount: number, timestamp: number): void {
		if (amount === 0) {
			// Remove entry entirely
			const terminalEntries = this.entries.get(terminalId);
			if (terminalEntries) {
				terminalEntries.delete(uniqueId);
			}
			return;
		}

		let terminalEntries = this.entries.get(terminalId);
		if (!terminalEntries) {
			terminalEntries = new Map();
			this.entries.set(terminalId, terminalEntries);
		}

		const point: DataPoint = { timestamp, amount };
		terminalEntries.set(uniqueId, {
			lastPoint: point,
			windowPoints: [point]
		});
	}

	/**
	 * Get the production rate for an item (items/second).
	 * Returns null if:
	 * - Entry doesn't exist
	 * - Not enough data points
	 * - Data is stale
	 */
	getRate(terminalId: string, uniqueId: string, now: number): number | null {
		const entry = this.getEntry(terminalId, uniqueId);
		if (!entry) {
			return null;
		}

		// Check if data is stale
		if (now - entry.lastPoint.timestamp > STALE_MS) {
			return null;
		}

		// Need at least 2 points to calculate rate
		if (entry.windowPoints.length < 2) {
			return null;
		}

		const oldest = entry.windowPoints[0];
		const latest = entry.lastPoint;

		const dt = (latest.timestamp - oldest.timestamp) / 1000; // Convert to seconds
		if (dt <= 0) {
			return null;
		}

		return (latest.amount - oldest.amount) / dt;
	}

	/**
	 * Get entry for an item.
	 */
	getEntry(terminalId: string, uniqueId: string): ProductionEntry | undefined {
		return this.entries.get(terminalId)?.get(uniqueId);
	}

	/**
	 * Get a copy of window points for an item.
	 */
	getPoints(terminalId: string, uniqueId: string): DataPoint[] | null {
		const entry = this.getEntry(terminalId, uniqueId);
		if (!entry) {
			return null;
		}
		return [...entry.windowPoints];
	}

	/**
	 * Load historical data points for an item.
	 * This method properly handles out-of-order timestamps by:
	 * 1. Merging with existing points
	 * 2. Sorting by timestamp
	 * 3. Deduplicating by timestamp
	 * 4. Pruning to window size
	 */
	loadHistoricalPoints(terminalId: string, uniqueId: string, historicalPoints: DataPoint[]): void {
		if (!historicalPoints || historicalPoints.length === 0) return;

		let terminalEntries = this.entries.get(terminalId);
		if (!terminalEntries) {
			terminalEntries = new Map();
			this.entries.set(terminalId, terminalEntries);
		}

		const entry = terminalEntries.get(uniqueId);

		// Merge historical points with existing points
		let allPoints: DataPoint[];
		if (entry) {
			allPoints = [...entry.windowPoints, ...historicalPoints];
		} else {
			allPoints = [...historicalPoints];
		}

		// Sort by timestamp ascending
		allPoints.sort((a, b) => a.timestamp - b.timestamp);

		// Deduplicate by timestamp (keep first occurrence)
		const seen = new Set<number>();
		const deduped: DataPoint[] = [];
		for (const p of allPoints) {
			if (!seen.has(p.timestamp)) {
				seen.add(p.timestamp);
				deduped.push(p);
			}
		}

		// Prune to window size based on most recent point
		const now = deduped.length > 0 ? deduped[deduped.length - 1].timestamp : Date.now();
		const cutoff = now - WINDOW_MS;
		const windowPoints = deduped.filter((p) => p.timestamp >= cutoff);

		// Keep within MAX_WINDOW_POINTS
		while (windowPoints.length > MAX_WINDOW_POINTS) {
			windowPoints.shift();
		}

		if (windowPoints.length === 0) {
			// No valid points, remove entry
			terminalEntries.delete(uniqueId);
			return;
		}

		// Update entry
		terminalEntries.set(uniqueId, {
			lastPoint: windowPoints[windowPoints.length - 1],
			windowPoints
		});
	}

	/**
	 * Clear all entries for a terminal.
	 */
	clearTerminal(terminalId: string): void {
		this.entries.delete(terminalId);
	}

	/**
	 * Clear all entries.
	 */
	clearAll(): void {
		this.entries.clear();
	}

	/**
	 * Prune window to stay within time and point limits.
	 */
	private pruneWindow(entry: ProductionEntry, now: number): void {
		const cutoff = now - WINDOW_MS;

		// Remove points older than window
		while (entry.windowPoints.length > 0 && entry.windowPoints[0].timestamp < cutoff) {
			entry.windowPoints.shift();
		}

		// Limit to MAX_WINDOW_POINTS
		while (entry.windowPoints.length > MAX_WINDOW_POINTS) {
			entry.windowPoints.shift();
		}
	}
}
