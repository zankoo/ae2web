import { describe, expect, it } from 'vitest';

import type { InventoryItem } from '$lib/api/types';
import { computeVisibleItems, hasPositionChanged } from '$lib/pages/inventory/visibleItems';

function item(overrides: Partial<InventoryItem>): InventoryItem {
	const id = overrides.id ?? 'mod:test';
	return {
		type: overrides.type ?? 'item',
		id,
		uniqueId: overrides.uniqueId ?? `${id}@u`,
		iconKey: overrides.iconKey ?? `${id}@h1_test`,
		name: overrides.name ?? id,
		translationKey: '',
		namespace: id.split(':')[0] ?? 'minecraft',
		amount: overrides.amount ?? 1,
		craftable: overrides.craftable ?? false,
		tags: overrides.tags
	};
}

describe('visibleItems', () => {
	it('filters by item/fluid type', () => {
		const items: InventoryItem[] = [
			item({ id: 'mod:a', uniqueId: 'a', type: 'item', amount: 1 }),
			item({ id: 'mod:b', uniqueId: 'b', type: 'fluid', amount: 2 })
		];

		expect(
			computeVisibleItems(items, { filter: 'items', searchTerm: '', sort: 'amount' }).map(
				(x) => x.type
			)
		).toEqual(['item']);

		expect(
			computeVisibleItems(items, { filter: 'fluids', searchTerm: '', sort: 'amount' }).map(
				(x) => x.type
			)
		).toEqual(['fluid']);
	});

	it('filters by searchTerm using name or id', () => {
		const items: InventoryItem[] = [
			item({ id: 'mod:apple', uniqueId: 'a', name: 'Apple', amount: 1 }),
			item({ id: 'mod:banana', uniqueId: 'b', name: 'Banana', amount: 2 })
		];

		expect(
			computeVisibleItems(items, { filter: 'all', searchTerm: 'app', sort: 'amount' }).map(
				(x) => x.id
			)
		).toEqual(['mod:apple']);

		expect(
			computeVisibleItems(items, { filter: 'all', searchTerm: 'banana', sort: 'amount' }).map(
				(x) => x.id
			)
		).toEqual(['mod:banana']);
	});

	it('sorts by amount descending', () => {
		const items: InventoryItem[] = [
			item({ id: 'mod:low', uniqueId: 'a', name: 'Low', amount: 1 }),
			item({ id: 'mod:high', uniqueId: 'b', name: 'High', amount: 10 })
		];

		expect(
			computeVisibleItems(items, { filter: 'all', searchTerm: '', sort: 'amount' }).map(
				(x) => x.id
			)
		).toEqual(['mod:high', 'mod:low']);
	});

	it('detects position changes', () => {
		const a = item({ id: 'mod:a', uniqueId: 'a' });
		const b = item({ id: 'mod:b', uniqueId: 'b' });

		expect(hasPositionChanged([a, b], [a, b])).toBe(false);
		expect(hasPositionChanged([a, b], [b, a])).toBe(true);
	});
});
