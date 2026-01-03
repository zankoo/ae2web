export type FlipPosition = { left: number; top: number };

export type PlayFlipAnimationArgs = {
	elements: Iterable<HTMLElement>;
	getKey: (node: HTMLElement) => string | null;
	from: Map<string, FlipPosition>;
	durationMs: number;
	scrollDeltaY?: number;
	easing?: string;
};

const activeAnimations = new WeakMap<Element, Animation>();

export function playFlipAnimation({
	elements,
	getKey,
	from,
	durationMs,
	scrollDeltaY = 0,
	easing = 'cubic-bezier(0.22, 1, 0.36, 1)'
}: PlayFlipAnimationArgs): number {
	if (!durationMs) return 0;

	// Important: avoid alternating layout reads and writes in a single loop,
	// or Chrome may repeatedly force synchronous layout and jank hard.
	const pending: Array<{ element: HTMLElement; dx: number; dy: number }> = [];
	for (const element of elements) {
		if (!element.isConnected) continue;
		const key = getKey(element);
		if (!key) continue;

		const fromPos = from.get(key);
		if (!fromPos) continue;

		const to = element.getBoundingClientRect();
		const dx = fromPos.left - to.left;
		const dy = fromPos.top - scrollDeltaY - to.top;

		if (!Number.isFinite(dx) || !Number.isFinite(dy)) continue;
		if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) continue;

		const absDx = Math.abs(dx);
		const absDy = Math.abs(dy);
		const slotWidth = Number.isFinite(to.width) && to.width > 0 ? to.width : 64;
		const slotHeight = Number.isFinite(to.height) && to.height > 0 ? to.height : 64;

		// When a grid reorder causes a row wrap (e.g. last column → first column),
		// FLIP produces a huge horizontal jump that looks bad and can also be expensive.
		const looksLikeWrapJump = absDy > slotHeight * 0.5 && absDx > slotWidth * 3 && absDx > absDy * 3;
		if (looksLikeWrapJump) continue;

		pending.push({ element, dx, dy });
	}

	let animated = 0;
	for (const { element, dx, dy } of pending) {
		activeAnimations.get(element)?.cancel();
		const animation = element.animate(
			[
				{ transform: `translate3d(${dx}px, ${dy}px, 0)` },
				{ transform: 'translate3d(0, 0, 0)' }
			],
			{ duration: durationMs, easing }
		);
		activeAnimations.set(element, animation);
		animated += 1;
	}

	return animated;
}
