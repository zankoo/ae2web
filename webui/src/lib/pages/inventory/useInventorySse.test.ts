import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { InventoryItem } from '$lib/api/types';
import { ProductionStatsTracker } from '$lib/utils/productionStats';

type Handlers = { onSnapshot(msg: unknown): void };
let handlers: Handlers | null = null;

vi.mock('$lib/api/inventory', () => ({
	openInventorySse: (_terminalId: string, h: Handlers) => {
		handlers = h;
		return { close: vi.fn() };
	}
}));

vi.mock('$lib/storage/productionHistory', () => ({
	loadTerminalHistory: vi.fn(async () => new Map()),
	queueHistoryUpdate: vi.fn()
}));

const { createInventorySseController } = await import('./useInventorySse');

function setHidden(hidden: boolean): void {
	Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
}

function createController(flushInventoryState: () => Promise<void>) {
	const state = {
		inventoryState: new Map<string, InventoryItem>(),
		inventoryStateDirty: false,
		inventoryStateFlushInFlight: false,
		inventoryStateFlushPending: false,
		inventoryScrollActive: false
	};
	const controller = createInventorySseController({
		state,
		getTerminalId: () => 't1',
		productionStats: new ProductionStatsTracker(),
		chartViewedItems: new Set(),
		productionRateCache: new Map(),
		flushInventoryState,
		showToast: vi.fn()
	});
	return { state, controller };
}

describe('createInventorySseController visibility handling', () => {
	beforeEach(() => {
		handlers = null;
	});

	afterEach(() => {
		setHidden(false);
	});

	it('flushes pending inventory when the tab becomes visible again', () => {
		const flush = vi.fn(async () => {});
		const { state, controller } = createController(flush);
		controller.start();

		setHidden(true);
		handlers!.onSnapshot({ terminalId: 't1', version: 1, items: [] });
		expect(state.inventoryStateDirty).toBe(true);
		flush.mockClear();

		setHidden(false);
		document.dispatchEvent(new Event('visibilitychange'));

		expect(flush).toHaveBeenCalledTimes(1);
		controller.destroy();
	});

	it('does not flush on visibility change when nothing is pending', () => {
		const flush = vi.fn(async () => {});
		const { controller } = createController(flush);
		controller.start();

		document.dispatchEvent(new Event('visibilitychange'));

		expect(flush).not.toHaveBeenCalled();
		controller.destroy();
	});

	it('stops listening after stop()', () => {
		const flush = vi.fn(async () => {});
		const { state, controller } = createController(flush);
		controller.start();
		controller.stop();

		state.inventoryStateDirty = true;
		document.dispatchEvent(new Event('visibilitychange'));

		expect(flush).not.toHaveBeenCalled();
	});
});
