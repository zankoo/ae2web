import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('tooltipCache', () => {
	beforeEach(() => {
		vi.resetModules();
		window.localStorage.clear();
	});

	it('returns null for non-existent key', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		expect(mod.getTooltipFromStorage('ae2:item@hash123')).toBeNull();
	});

	it('stores and retrieves tooltip', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		const tooltip = ['Line 1', 'Line 2'];
		mod.setTooltipInStorage('ae2:item@hash123', tooltip);
		mod._flushForTesting();

		const retrieved = mod.getTooltipFromStorage('ae2:item@hash123');
		expect(retrieved).toEqual(tooltip);
	});

	it('persists to localStorage', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		mod.setTooltipInStorage('ae2:item@hash123', ['Test']);
		mod._flushForTesting();

		const raw = window.localStorage.getItem('ae2web.tooltipCache');
		expect(raw).toBeTruthy();

		const data = JSON.parse(raw!);
		expect(data.schemaVersion).toBe(1);
		expect(data.entries['ae2:item@hash123'].tooltip).toEqual(['Test']);
	});

	it('loads from localStorage on fresh import', async () => {
		window.localStorage.setItem(
			'ae2web.tooltipCache',
			JSON.stringify({
				schemaVersion: 1,
				entries: {
					'ae2:item@hash123': { tooltip: ['Cached'], accessedAt: Date.now() }
				}
			})
		);

		vi.resetModules();
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		const retrieved = mod.getTooltipFromStorage('ae2:item@hash123');
		expect(retrieved).toEqual(['Cached']);
	});

	it('clears cache when schema version changes', async () => {
		window.localStorage.setItem(
			'ae2web.tooltipCache',
			JSON.stringify({
				schemaVersion: 999,
				entries: {
					'ae2:item@hash123': { tooltip: ['Old'], accessedAt: Date.now() }
				}
			})
		);

		vi.resetModules();
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		expect(mod.getTooltipFromStorage('ae2:item@hash123')).toBeNull();
	});

	it('clearTooltipCache removes all data', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		mod.setTooltipInStorage('ae2:item@hash123', ['Test']);
		mod._flushForTesting();
		expect(window.localStorage.getItem('ae2web.tooltipCache')).toBeTruthy();

		mod.clearTooltipCache();
		expect(window.localStorage.getItem('ae2web.tooltipCache')).toBeNull();
		expect(mod.getTooltipFromStorage('ae2:item@hash123')).toBeNull();
	});

	it('getTooltipCacheStats returns correct count', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		expect(mod.getTooltipCacheStats().entryCount).toBe(0);

		mod.setTooltipInStorage('key1', ['a']);
		mod.setTooltipInStorage('key2', ['b']);
		mod.setTooltipInStorage('key3', ['c']);
		mod._flushForTesting();

		expect(mod.getTooltipCacheStats().entryCount).toBe(3);
	});

	it('updates accessedAt on read', async () => {
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		const now = Date.now();
		vi.setSystemTime(now);

		mod.setTooltipInStorage('ae2:item@hash123', ['Test']);
		mod._flushForTesting();

		vi.setSystemTime(now + 10000);
		mod.getTooltipFromStorage('ae2:item@hash123');
		mod._flushForTesting();

		const raw = window.localStorage.getItem('ae2web.tooltipCache');
		const data = JSON.parse(raw!);
		expect(data.entries['ae2:item@hash123'].accessedAt).toBe(now + 10000);

		vi.useRealTimers();
	});

	it('handles corrupted localStorage gracefully', async () => {
		window.localStorage.setItem('ae2web.tooltipCache', 'not-valid-json');

		vi.resetModules();
		const mod = await import('$lib/storage/tooltipCache');
		mod._resetForTesting();

		expect(mod.getTooltipFromStorage('any-key')).toBeNull();
		mod.setTooltipInStorage('new-key', ['Works']);
		mod._flushForTesting();

		expect(mod.getTooltipFromStorage('new-key')).toEqual(['Works']);
	});
});
