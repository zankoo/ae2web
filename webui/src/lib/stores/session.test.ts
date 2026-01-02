import { describe, expect, it } from 'vitest';

import { readTokenFromUrl } from '$lib/stores/session';

describe('session token', () => {
	it('reads token from URL query', () => {
		const url = new URL('http://localhost/?token=abc');
		expect(readTokenFromUrl(url)).toBe('abc');
	});

	it('returns empty string when token missing', () => {
		const url = new URL('http://localhost/');
		expect(readTokenFromUrl(url)).toBe('');
	});
});

