import { describe, expect, it } from 'vitest';

import * as format from '$lib/utils/format';

describe('formatFluidAmount', () => {
	it('formats 1000mB as 1B', () => {
		const fn = (format as any).formatFluidAmount as ((mb: number) => string) | undefined;
		expect(typeof fn).toBe('function');
		expect(fn?.(1000)).toBe('1B');
	});

	it('formats 1500mB as 1.5B', () => {
		const fn = (format as any).formatFluidAmount as ((mb: number) => string) | undefined;
		expect(fn?.(1500)).toBe('1.5B');
	});

	it('formats large values using suffixes', () => {
		const fn = (format as any).formatFluidAmount as ((mb: number) => string) | undefined;
		expect(fn?.(1_000_000)).toBe('1KB');
	});
});
