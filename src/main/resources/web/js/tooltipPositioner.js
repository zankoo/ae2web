export function computeTooltipPosition({
    x,
    y,
    tooltipWidth,
    tooltipHeight,
    viewportWidth,
    viewportHeight,
    padding = 12,
    margin = 10,
}) {
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

export function createTooltipPositioner({
    requestAnimationFrame,
    cancelAnimationFrame,
    getTooltipSize,
    getViewportSize,
    applyPosition,
    padding = 12,
    margin = 10,
}) {
    let cachedSize = null;
    let pendingX = null;
    let pendingY = null;
    let rafId = 0;

    function invalidateSize() {
        cachedSize = null;
    }

    function cancel() {
        if (rafId && typeof cancelAnimationFrame === 'function') {
            cancelAnimationFrame(rafId);
        }
        rafId = 0;
        pendingX = null;
        pendingY = null;
    }

    function flushFrame() {
        rafId = 0;
        if (pendingX == null || pendingY == null) {
            return;
        }

        const size = cachedSize || getTooltipSize();
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
            margin,
        });

        applyPosition(left, top);
    }

    function onMouseMove(x, y) {
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
        cancel,
    };
}
