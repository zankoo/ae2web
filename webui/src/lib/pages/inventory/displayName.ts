import type { InventoryItem } from '$lib/api/types';

export function displayNameFor(item: InventoryItem): string {
	if (item.name.trim()) return item.name;

	const colon = item.id.indexOf(':');
	if (colon >= 0 && colon < item.id.length - 1) {
		return item.id.slice(colon + 1);
	}
	return item.id;
}

