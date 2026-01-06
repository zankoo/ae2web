import type { InventoryFilter } from '$lib/api/inventory';
import type { InventoryItem } from '$lib/api/types';
import type { SortMode } from '$lib/components/InventoryToolbar.svelte';
import { sortInventoryItems } from '$lib/utils/inventorySort';
import { matchItem } from '$lib/utils/pinyin';

import { displayNameFor } from './displayName';

export function computeVisibleItems(
	items: InventoryItem[],
	{
		filter,
		searchTerm,
		sort
	}: { filter: InventoryFilter; searchTerm: string; sort: SortMode }
): InventoryItem[] {
	let out = items;

	if (filter === 'items') {
		out = out.filter((item) => item.type === 'item');
	} else if (filter === 'fluids') {
		out = out.filter((item) => item.type === 'fluid');
	}

	if (searchTerm) {
		out = out.filter((item) => {
			const name = displayNameFor(item);
			return matchItem(name, searchTerm) || matchItem(item.id, searchTerm);
		});
	}

	return sortInventoryItems(out, sort);
}

export function hasPositionChanged(previous: InventoryItem[], next: InventoryItem[]): boolean {
	if (previous.length === 0) return false;
	if (next.length === 0) return false;

	const prevIndexById = new Map<string, number>();
	for (let i = 0; i < previous.length; i++) {
		prevIndexById.set(previous[i].uniqueId, i);
	}

	for (let i = 0; i < next.length; i++) {
		const prevIndex = prevIndexById.get(next[i].uniqueId);
		if (prevIndex != null && prevIndex !== i) {
			return true;
		}
	}

	return false;
}

