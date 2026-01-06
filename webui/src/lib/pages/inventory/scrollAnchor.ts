import type { InventoryGridDomAccess, ScrollAnchor } from './types';

function escapeCssAttr(value: string): string {
	if (window.CSS && typeof window.CSS.escape === 'function') {
		return window.CSS.escape(value);
	}
	return String(value).replace(/["\\]/g, '\\$&');
}

export function captureScrollAnchor(grid: InventoryGridDomAccess): ScrollAnchor | null {
	const inventoryScrollEl = grid?.getScrollElement?.();
	const inventoryGridEl = grid?.getGridElement?.();
	if (!inventoryScrollEl || !inventoryGridEl) return null;

	try {
		const scrollRect = inventoryScrollEl.getBoundingClientRect();
		const x = scrollRect.left + Math.min(16, scrollRect.width / 2);
		const y = scrollRect.top + 16;
		const hit = document.elementFromPoint(x, y) as Element | null;
		const slot = hit?.closest?.('.item-slot') as HTMLElement | null;
		if (slot && inventoryGridEl.contains(slot)) {
			const id = slot.dataset.itemId;
			if (!id) return null;
			const rect = slot.getBoundingClientRect();
			return { id, topOffset: rect.top - scrollRect.top };
		}
	} catch {
		// ignore
	}
	return null;
}

export function restoreScrollAnchor(grid: InventoryGridDomAccess, anchor: ScrollAnchor | null): number {
	const inventoryScrollEl = grid?.getScrollElement?.();
	const inventoryGridEl = grid?.getGridElement?.();
	if (!anchor?.id || !inventoryScrollEl || !inventoryGridEl) return 0;

	try {
		const selector = `[data-item-id="${escapeCssAttr(anchor.id)}"]`;
		const el = inventoryGridEl.querySelector(selector) as HTMLElement | null;
		if (!el) return 0;

		const scrollRect = inventoryScrollEl.getBoundingClientRect();
		const rect = el.getBoundingClientRect();
		const newOffset = rect.top - scrollRect.top;
		const delta = newOffset - anchor.topOffset;
		if (Number.isFinite(delta) && Math.abs(delta) > 0.5) {
			inventoryScrollEl.scrollTop += delta;
			return delta;
		}
	} catch {
		// ignore
	}
	return 0;
}

