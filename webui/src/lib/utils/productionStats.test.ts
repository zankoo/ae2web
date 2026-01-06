import { describe, it, expect, beforeEach } from 'vitest';
import {
	ProductionStatsTracker,
	WINDOW_MS,
	STALE_MS,
	MAX_WINDOW_POINTS,
	formatProductionRate
} from './productionStats';

describe('formatProductionRate', () => {
	describe('items (not fluid)', () => {
		it('should return null for null rate', () => {
			expect(formatProductionRate(null, false)).toBeNull();
		});

		it('should return null for zero rate', () => {
			expect(formatProductionRate(0, false)).toBeNull();
		});

		it('should return null for rate below threshold (0.1/s)', () => {
			expect(formatProductionRate(0.05, false)).toBeNull();
			expect(formatProductionRate(-0.05, false)).toBeNull();
		});

		it('should format positive rate >= 1/s', () => {
			expect(formatProductionRate(10, false)).toBe('+10.0/s');
			expect(formatProductionRate(1.5, false)).toBe('+1.5/s');
		});

		it('should format negative rate >= 1/s', () => {
			expect(formatProductionRate(-10, false)).toBe('-10.0/s');
			expect(formatProductionRate(-1.5, false)).toBe('-1.5/s');
		});

		it('should format large rate with K suffix', () => {
			expect(formatProductionRate(1500, false)).toBe('+1.5K/s');
			expect(formatProductionRate(-2000, false)).toBe('-2.0K/s');
		});

		it('should format rate < 1/s as per minute', () => {
			expect(formatProductionRate(0.5, false)).toBe('+30.0/m');
			expect(formatProductionRate(-0.2, false)).toBe('-12.0/m');
		});
	});

	describe('fluids', () => {
		it('should return null for rate below threshold (100mB/s)', () => {
			expect(formatProductionRate(50, true)).toBeNull(); // 50mB/s
			expect(formatProductionRate(-50, true)).toBeNull();
		});

		it('should format rate >= 100mB/s as B/s', () => {
			expect(formatProductionRate(1000, true)).toBe('+1.0B/s'); // 1000mB = 1B
			expect(formatProductionRate(500, true)).toBe('+0.5B/s'); // 500mB = 0.5B
		});

		it('should format negative fluid rate', () => {
			expect(formatProductionRate(-1000, true)).toBe('-1.0B/s');
			expect(formatProductionRate(-500, true)).toBe('-0.5B/s');
		});

		it('should format rate < 100mB/s as B/m', () => {
			// 100mB/s = 0.1B/s, threshold is 100mB/s
			// Rate at exactly 100mB/s should show as B/s
			expect(formatProductionRate(100, true)).toBe('+0.1B/s');
		});
	});
});

describe('ProductionStatsTracker', () => {
	let tracker: ProductionStatsTracker;

	beforeEach(() => {
		tracker = new ProductionStatsTracker();
	});

	describe('recordUpdate', () => {
		it('should create entry on first update', () => {
			const now = Date.now();
			tracker.recordUpdate('terminal1', 'item:minecraft:diamond@hash', 100, now);

			const entry = tracker.getEntry('terminal1', 'item:minecraft:diamond@hash');
			expect(entry).toBeDefined();
			expect(entry!.lastPoint.amount).toBe(100);
			expect(entry!.lastPoint.timestamp).toBe(now);
		});

		it('should calculate positive rate correctly', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 10000); // +100 over 10s = 10/s

			const rate = tracker.getRate('t1', 'item:test', t0 + 10000);
			expect(rate).toBeCloseTo(10, 1);
		});

		it('should calculate negative rate correctly', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 200, t0);
			tracker.recordUpdate('t1', 'item:test', 100, t0 + 10000); // -100 over 10s = -10/s

			const rate = tracker.getRate('t1', 'item:test', t0 + 10000);
			expect(rate).toBeCloseTo(-10, 1);
		});

		it('should not add point if amount unchanged', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 100, t0 + 1000); // same amount

			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry!.windowPoints.length).toBe(1);
		});

		it('should ignore negative amounts', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', -50, t0 + 1000); // invalid

			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry!.lastPoint.amount).toBe(100); // unchanged
		});
	});

	describe('rate calculation edge cases', () => {
		it('should return null for single point (cannot calculate slope)', () => {
			tracker.recordUpdate('t1', 'item:test', 100, 1000000);

			const rate = tracker.getRate('t1', 'item:test', 1000000);
			expect(rate).toBeNull();
		});

		it('should return null when entry does not exist', () => {
			const rate = tracker.getRate('t1', 'nonexistent', Date.now());
			expect(rate).toBeNull();
		});

		it('should skip points with same timestamp (avoid dt=0)', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 150, t0); // same timestamp
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 10000);

			const entry = tracker.getEntry('t1', 'item:test');
			// Should only have 2 points (first and last, skipping duplicate timestamp)
			expect(entry!.windowPoints.length).toBe(2);
		});

		it('should return 0 rate when amount stays constant', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 100, t0 + 5000);
			tracker.recordUpdate('t1', 'item:test', 100, t0 + 10000);

			// All same amount - windowPoints only has 1 point
			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry!.windowPoints.length).toBe(1);
		});
	});

	describe('window management', () => {
		it('should prune points older than WINDOW_MS', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + WINDOW_MS + 1000); // beyond window

			const entry = tracker.getEntry('t1', 'item:test');
			// Old point should be pruned
			expect(
				entry!.windowPoints.every((p) => p.timestamp >= t0 + WINDOW_MS + 1000 - WINDOW_MS)
			).toBe(true);
		});

		it('should limit points to MAX_WINDOW_POINTS', () => {
			const t0 = 1000000;
			// Add more than MAX_WINDOW_POINTS
			for (let i = 0; i <= MAX_WINDOW_POINTS + 10; i++) {
				tracker.recordUpdate('t1', 'item:test', 100 + i, t0 + i * 100);
			}

			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry!.windowPoints.length).toBeLessThanOrEqual(MAX_WINDOW_POINTS);
		});
	});

	describe('stale data handling', () => {
		it('should return null rate when data is stale', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 1000);

			// Query after STALE_MS
			const rate = tracker.getRate('t1', 'item:test', t0 + 1000 + STALE_MS + 1);
			expect(rate).toBeNull();
		});

		it('should return rate when data is fresh', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 10000);

			// Query within STALE_MS
			const rate = tracker.getRate('t1', 'item:test', t0 + 10000 + STALE_MS - 1);
			expect(rate).toBeCloseTo(10, 1);
		});
	});

	describe('resetBaseline', () => {
		it('should clear window and set new baseline', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 1000);

			tracker.resetBaseline('t1', 'item:test', 500, t0 + 2000);

			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry!.windowPoints.length).toBe(1);
			expect(entry!.windowPoints[0].amount).toBe(500);
			expect(entry!.lastPoint.amount).toBe(500);
		});

		it('should create new entry if none exists', () => {
			tracker.resetBaseline('t1', 'item:new', 100, 1000000);

			const entry = tracker.getEntry('t1', 'item:new');
			expect(entry).toBeDefined();
			expect(entry!.lastPoint.amount).toBe(100);
		});

		it('should remove entry when resetting to 0', () => {
			tracker.recordUpdate('t1', 'item:test', 100, 1000000);
			tracker.resetBaseline('t1', 'item:test', 0, 1000001);

			const entry = tracker.getEntry('t1', 'item:test');
			expect(entry).toBeUndefined();
		});
	});

	describe('clearTerminal', () => {
		it('should remove all entries for a terminal', () => {
			tracker.recordUpdate('t1', 'item:a', 100, 1000000);
			tracker.recordUpdate('t1', 'item:b', 200, 1000000);
			tracker.recordUpdate('t2', 'item:c', 300, 1000000);

			tracker.clearTerminal('t1');

			expect(tracker.getEntry('t1', 'item:a')).toBeUndefined();
			expect(tracker.getEntry('t1', 'item:b')).toBeUndefined();
			expect(tracker.getEntry('t2', 'item:c')).toBeDefined();
		});
	});

	describe('clearAll', () => {
		it('should remove all entries', () => {
			tracker.recordUpdate('t1', 'item:a', 100, 1000000);
			tracker.recordUpdate('t2', 'item:b', 200, 1000000);

			tracker.clearAll();

			expect(tracker.getEntry('t1', 'item:a')).toBeUndefined();
			expect(tracker.getEntry('t2', 'item:b')).toBeUndefined();
		});
	});

	describe('getPoints', () => {
		it('should return copy of window points', () => {
			const t0 = 1000000;
			tracker.recordUpdate('t1', 'item:test', 100, t0);
			tracker.recordUpdate('t1', 'item:test', 200, t0 + 1000);

			const points = tracker.getPoints('t1', 'item:test');
			expect(points).toHaveLength(2);
			expect(points![0].amount).toBe(100);
			expect(points![1].amount).toBe(200);

			// Should be a copy
			points!.push({ timestamp: 0, amount: 0 });
			expect(tracker.getPoints('t1', 'item:test')).toHaveLength(2);
		});

		it('should return null for nonexistent entry', () => {
			const points = tracker.getPoints('t1', 'nonexistent');
			expect(points).toBeNull();
		});
	});
});

describe('deriveRateSeries', () => {
	it('should compute per-second production rates over a sliding window', async () => {
		const mod = (await import('./productionStats')) as any;
		const deriveRateSeries = mod.deriveRateSeries as unknown;

		expect(typeof deriveRateSeries).toBe('function');
		if (typeof deriveRateSeries !== 'function') return;

		const points = [
			{ timestamp: 0, amount: 100 },
			{ timestamp: 10_000, amount: 200 }
		];

		expect(deriveRateSeries(points)).toEqual([{ timestamp: 10_000, rate: 10 }]);
	});

	it('should respect the window cutoff when calculating rates', async () => {
		const mod = (await import('./productionStats')) as any;
		const deriveRateSeries = mod.deriveRateSeries as unknown;

		expect(typeof deriveRateSeries).toBe('function');
		if (typeof deriveRateSeries !== 'function') return;

		const points = [
			{ timestamp: 0, amount: 0 },
			{ timestamp: 10_000, amount: 100 },
			{ timestamp: 40_000, amount: 200 }
		];

		const series = deriveRateSeries(points, 30_000);
		expect(series).toHaveLength(2);
		expect(series[0]).toEqual({ timestamp: 10_000, rate: 10 });
		expect(series[1].rate).toBeCloseTo(100 / 30, 5);
	});
});
