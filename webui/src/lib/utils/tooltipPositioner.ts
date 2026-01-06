export type TooltipPositionArgs = {
	x: number;
	y: number;
	tooltipWidth: number;
	tooltipHeight: number;
	viewportWidth: number;
	viewportHeight: number;
	padding?: number;
	margin?: number;
};

export function computeTooltipPosition({
	x,
	y,
	tooltipWidth,
	tooltipHeight,
	viewportWidth,
	viewportHeight,
	padding = 12,
	margin = 10
}: TooltipPositionArgs): { left: number; top: number } {
	let left = x + margin;
	let top = y + margin;

	const maxLeft = viewportWidth - tooltipWidth - padding;
	const maxTop = viewportHeight - tooltipHeight - padding;

	if (left > maxLeft) {
		left = Math.max(padding, x - tooltipWidth - margin);
	}
	if (top > maxTop) {
		top = Math.max(padding, y - tooltipHeight - margin);
	}

	return { left, top };
}

export type TooltipPositioner = {
	invalidateSize: () => void;
	onMouseMove: (x: number, y: number) => void;
	cancel: () => void;
};

export type TooltipPositionerArgs = {
	requestAnimationFrame: (cb: FrameRequestCallback) => number;
	cancelAnimationFrame: (id: number) => void;
	getTooltipSize: () => { width: number; height: number };
	getViewportSize: () => { width: number; height: number };
	applyPosition: (left: number, top: number) => void;
	padding?: number;
	margin?: number;
};

export function createTooltipPositioner({
	requestAnimationFrame,
	cancelAnimationFrame,
	getTooltipSize,
	getViewportSize,
	applyPosition,
	padding = 12,
	margin = 10
}: TooltipPositionerArgs): TooltipPositioner {
	let cachedSize: { width: number; height: number } | null = null;
	let pendingX: number | null = null;
	let pendingY: number | null = null;
	let rafId = 0;

	function invalidateSize(): void {
		cachedSize = null;
	}

	function cancel(): void {
		if (rafId) {
			cancelAnimationFrame(rafId);
		}
		rafId = 0;
		pendingX = null;
		pendingY = null;
	}

	function flushFrame(): void {
		rafId = 0;
		if (pendingX == null || pendingY == null) {
			return;
		}

		const size = cachedSize ?? getTooltipSize();
		cachedSize = size;
		const viewport = getViewportSize();

		const { left, top } = computeTooltipPosition({
			x: pendingX,
			y: pendingY,
			tooltipWidth: size.width,
			tooltipHeight: size.height,
			viewportWidth: viewport.width,
			viewportHeight: viewport.height,
			padding,
			margin
		});

		applyPosition(left, top);
	}

	function onMouseMove(x: number, y: number): void {
		pendingX = x;
		pendingY = y;
		if (rafId) {
			return;
		}
		rafId = requestAnimationFrame(flushFrame);
	}

	return {
		invalidateSize,
		onMouseMove,
		cancel
	};
}
