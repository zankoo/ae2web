import { describe, expect, it } from 'vitest';

import { arrayMove } from '$lib/utils/arrayMove';

describe('arrayMove', () => {
	it('moves items forward (0 -> 2)', () => {
		expect(arrayMove(['a', 'b', 'c', 'd'], 0, 2)).toEqual(['b', 'c', 'a', 'd']);
	});

	it('moves items backward (2 -> 0)', () => {
		expect(arrayMove(['a', 'b', 'c', 'd'], 2, 0)).toEqual(['c', 'a', 'b', 'd']);
	});

	it('does nothing when indices are invalid', () => {
		expect(arrayMove(['a', 'b'], -1, 1)).toEqual(['a', 'b']);
		expect(arrayMove(['a', 'b'], 0, 2)).toEqual(['a', 'b']);
	});
});

