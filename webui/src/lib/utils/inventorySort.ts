import type { InventoryItem } from '$lib/api/types';

const LEGACY_FORMATTING_CODE = /\u00a7[0-9A-FK-OR]/gi;
const DEFAULT_COLLATOR = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

function stripLegacyFormatting(text: string): string {
	return String(text).replace(LEGACY_FORMATTING_CODE, '');
}

function getDisplayName(item: InventoryItem): string {
	const name = item?.name;
	if (typeof name === 'string' && name.trim()) {
		return stripLegacyFormatting(name).trim();
	}

	const id = item?.id;
	if (typeof id === 'string' && id.includes(':')) {
		const [, path] = id.split(':', 2);
		return path || id;
	}

	if (typeof id === 'string') {
		return id;
	}

	return '';
}

function getAmount(item: InventoryItem): number {
	const amount = Number(item?.amount);
	return Number.isFinite(amount) ? amount : 0;
}

function compareStableKey(a: InventoryItem, b: InventoryItem): number {
	const keyA = a?.uniqueId ?? a?.id ?? '';
	const keyB = b?.uniqueId ?? b?.id ?? '';
	return DEFAULT_COLLATOR.compare(String(keyA), String(keyB));
}

export function sortInventoryItems(items: InventoryItem[], sortKey: string): InventoryItem[] {
	const list = Array.isArray(items) ? items.slice() : [];
	const mode = typeof sortKey === 'string' ? sortKey.toLowerCase() : 'amount';

	list.sort((a, b) => {
		if (mode === 'name') {
			const nameCmp = DEFAULT_COLLATOR.compare(getDisplayName(a), getDisplayName(b));
			if (nameCmp !== 0) return nameCmp;

			const amountDiff = getAmount(b) - getAmount(a);
			if (amountDiff !== 0) return amountDiff;

			return compareStableKey(a, b);
		}

		const amountDiff = getAmount(b) - getAmount(a);
		if (amountDiff !== 0) return amountDiff;

		const nameCmp = DEFAULT_COLLATOR.compare(getDisplayName(a), getDisplayName(b));
		if (nameCmp !== 0) return nameCmp;

		return compareStableKey(a, b);
	});

	return list;
}

