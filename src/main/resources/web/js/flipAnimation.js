export function computeFlipDelta(oldRect, newRect) {
    const oldLeft = Number(oldRect?.left);
    const oldTop = Number(oldRect?.top);
    const newLeft = Number(newRect?.left);
    const newTop = Number(newRect?.top);

    const dx = (Number.isFinite(oldLeft) ? oldLeft : 0) - (Number.isFinite(newLeft) ? newLeft : 0);
    const dy = (Number.isFinite(oldTop) ? oldTop : 0) - (Number.isFinite(newTop) ? newTop : 0);
    return { dx, dy };
}

