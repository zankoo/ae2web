import { fetchTooltip } from '$lib/api/prerender';
import { getTooltipFromStorage, setTooltipInStorage } from '$lib/storage/tooltipCache';
import { legacyTextToHtml } from '$lib/utils/minecraftText';
import { createTooltipPositioner, type TooltipPositioner } from '$lib/utils/tooltipPositioner';

import type { InventoryGridDomAccess, TooltipDomAccess } from './types';

type TooltipController = {
	onMouseOver(e: MouseEvent): void;
	onMouseMove(e: MouseEvent): void;
	onMouseOut(e: MouseEvent): void;
	destroy(): void;
};

export function createTooltipController({
	getGrid,
	getTooltip,
	setVisible,
	setHtmlLines
}: {
	getGrid(): InventoryGridDomAccess | null | undefined;
	getTooltip(): TooltipDomAccess | null | undefined;
	setVisible(visible: boolean): void;
	setHtmlLines(lines: string[]): void;
}): TooltipController {
	const cache = new Map<string, string[]>();

	let hoverKey: string | null = null;
	let positioner: TooltipPositioner | null = null;
	let lastX = 0;
	let lastY = 0;
	let hasLastPos = false;
	let visible = false;

	function ensurePositioner(): TooltipPositioner | null {
		const tooltip = getTooltip();
		const tooltipEl = tooltip?.getElement?.();
		if (!tooltipEl) return null;
		if (positioner) return positioner;

		positioner = createTooltipPositioner({
			requestAnimationFrame: (cb) => window.requestAnimationFrame(cb),
			cancelAnimationFrame: (id) => window.cancelAnimationFrame(id),
			getTooltipSize: () => {
				const rect = tooltipEl.getBoundingClientRect();
				return { width: rect.width, height: rect.height };
			},
			getViewportSize: () => ({ width: window.innerWidth, height: window.innerHeight }),
			applyPosition: (left, top) => {
				tooltip?.setPosition(left, top);
			}
		});

		return positioner;
	}

	function showTooltipLoading(): void {
		setHtmlLines(['Loading...'].map((line) => legacyTextToHtml(line)));
		visible = true;
		setVisible(true);
		const p = ensurePositioner();
		p?.invalidateSize();
	}

	function renderTooltip(lines: string[]): void {
		const normalized = lines.filter(Boolean);
		if (normalized.length === 0) {
			setHtmlLines([legacyTextToHtml('(no tooltip)')]);
		} else {
			setHtmlLines(normalized.map((line) => legacyTextToHtml(line)));
		}

		visible = true;
		setVisible(true);
		const p = ensurePositioner();
		p?.invalidateSize();
		if (hasLastPos) {
			p?.onMouseMove(lastX, lastY);
		}
	}

	function hideTooltip(): void {
		visible = false;
		setVisible(false);
		positioner?.cancel();
	}

	async function requestTooltip(iconKey: string): Promise<void> {
		// L1: Check memory cache
		const memoryCached = cache.get(iconKey);
		if (memoryCached) {
			if (hoverKey === iconKey) renderTooltip(memoryCached);
			return;
		}

		// L2: Check localStorage cache
		const storageCached = getTooltipFromStorage(iconKey);
		if (storageCached) {
			cache.set(iconKey, storageCached);
			if (hoverKey === iconKey) renderTooltip(storageCached);
			return;
		}

		// L3: Fetch from API
		try {
			const data = await fetchTooltip(iconKey);
			const tooltip = Array.isArray(data?.tooltip) ? data.tooltip.filter(Boolean) : [];
			cache.set(iconKey, tooltip);
			setTooltipInStorage(iconKey, tooltip);
			if (hoverKey === iconKey) renderTooltip(tooltip);
		} catch {
			if (hoverKey === iconKey) renderTooltip(['(tooltip not prerendered yet)']);
		}
	}

	function onMouseOver(e: MouseEvent): void {
		const gridEl = getGrid()?.getGridElement?.();
		const slot = (e.target as Element | null)?.closest?.('.item-slot') as HTMLElement | null;
		if (!slot || !gridEl?.contains(slot)) return;

		const iconKey = slot.dataset.iconId;
		if (!iconKey) return;

		lastX = e.clientX;
		lastY = e.clientY;
		hasLastPos = true;

		hoverKey = iconKey;
		showTooltipLoading();
		ensurePositioner()?.onMouseMove(lastX, lastY);
		void requestTooltip(iconKey);
	}

	function onMouseMove(e: MouseEvent): void {
		if (!visible) return;
		lastX = e.clientX;
		lastY = e.clientY;
		hasLastPos = true;
		ensurePositioner()?.onMouseMove(lastX, lastY);
	}

	function onMouseOut(e: MouseEvent): void {
		const fromSlot = (e.target as Element | null)?.closest?.('.item-slot');
		const toSlot = (e.relatedTarget as Element | null)?.closest?.('.item-slot');
		if (fromSlot && fromSlot === toSlot) return;

		hoverKey = null;
		hasLastPos = false;
		hideTooltip();
	}

	function destroy(): void {
		hoverKey = null;
		hasLastPos = false;
		cache.clear();
		positioner?.cancel();
		positioner = null;
		visible = false;
		setVisible(false);
	}

	return { onMouseOver, onMouseMove, onMouseOut, destroy };
}
