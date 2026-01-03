import { describe, expect, it, vi } from 'vitest';

import { playFlipAnimation } from '$lib/utils/flipAnimation';

describe('playFlipAnimation', () => {
	it('does nothing when duration is 0 (avoids layout work)', () => {
		const el = document.createElement('div');
		el.className = 'item-slot';
		el.dataset.itemId = 'item:a';

		const getBoundingClientRect = vi.fn(() => ({ left: 10, top: 10 }) as any);
		(el as any).getBoundingClientRect = getBoundingClientRect;

		const animate = vi.fn();
		(el as any).animate = animate;

		const animated = playFlipAnimation({
			elements: [el],
			getKey: (node) => node.dataset.itemId ?? null,
			from: new Map([['item:a', { left: 10, top: 10 }]]),
			durationMs: 0
		});

		expect(animated).toBe(0);
		expect(getBoundingClientRect).not.toHaveBeenCalled();
		expect(animate).not.toHaveBeenCalled();
	});

	it('animates elements that moved', () => {
		const el = document.createElement('div');
		el.className = 'item-slot';
		el.dataset.itemId = 'item:a';
		document.body.appendChild(el);

		(el as any).getBoundingClientRect = vi.fn(() => ({ left: 20, top: 30, width: 64, height: 64 }) as any);

		const animationObj = { cancel: vi.fn() };
		const animate = vi.fn(() => animationObj);
		(el as any).animate = animate;

		const animated = playFlipAnimation({
			elements: [el],
			getKey: (node) => node.dataset.itemId ?? null,
			from: new Map([['item:a', { left: 10, top: 10 }]]),
			durationMs: 120
		});

		expect(animated).toBe(1);
		expect(animate).toHaveBeenCalledTimes(1);
	});

	it('skips animation when horizontal movement is extremely large (avoids wrap-jump across columns)', () => {
		const el = document.createElement('div');
		el.className = 'item-slot';
		el.dataset.itemId = 'item:a';
		document.body.appendChild(el);

		(el as any).getBoundingClientRect = vi.fn(() => ({ left: 0, top: 64, width: 64, height: 64 }) as any);

		const animationObj = { cancel: vi.fn() };
		const animate = vi.fn(() => animationObj);
		(el as any).animate = animate;

		const animated = playFlipAnimation({
			elements: [el],
			getKey: (node) => node.dataset.itemId ?? null,
			from: new Map([['item:a', { left: 1000, top: 0 }]]),
			durationMs: 120
		});

		expect(animated).toBe(0);
		expect(animate).not.toHaveBeenCalled();
	});
});
