import { describe, expect, it } from 'vitest';

import { computeFlipDuration } from '$lib/utils/flip';

describe('computeFlipDuration', () => {
	it('enables flip for user-triggered amount sort under max items', () => {
		expect(computeFlipDuration('amount', 100, true)).toBe(180);
	});

	it('disables flip when not user-triggered', () => {
		expect(computeFlipDuration('amount', 100, false)).toBe(0);
	});

	it('disables flip for non-amount sorts', () => {
		expect(computeFlipDuration('name', 100, true)).toBe(0);
	});

	it('disables flip when item count exceeds threshold', () => {
		expect(computeFlipDuration('amount', 500, true, { maxItems: 400 })).toBe(0);
	});
});

