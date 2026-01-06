export type ScrollAnchor = { id: string; topOffset: number };

export type InventoryGridDomAccess = {
	getScrollElement(): HTMLElement | null | undefined;
	getGridElement(): HTMLElement | null | undefined;
};

export type TooltipDomAccess = {
	getElement(): HTMLElement | null | undefined;
	setPosition(left: number, top: number): void;
};

