import { get } from 'svelte/store';
import { describe, expect, it, vi } from 'vitest';

describe('session token', () => {
	it('initializes from URL token and persists to localStorage', async () => {
		vi.resetModules();
		window.localStorage.clear();
		window.history.pushState({}, '', '/?token=abc');

		const mod = await import('$lib/stores/session');
		expect(get(mod.authToken)).toBe('abc');
		expect(window.localStorage.getItem('ae2web.authToken')).toBe('abc');
	});

	it('falls back to localStorage when URL token missing', async () => {
		vi.resetModules();
		window.localStorage.clear();
		window.localStorage.setItem('ae2web.authToken', 'xyz');
		window.history.pushState({}, '', '/');

		const mod = await import('$lib/stores/session');
		expect(get(mod.authToken)).toBe('xyz');
	});

	it('setAuthToken updates store and localStorage', async () => {
		vi.resetModules();
		window.localStorage.clear();
		window.history.pushState({}, '', '/');

		const mod = await import('$lib/stores/session');
		mod.setAuthToken('hello');
		expect(get(mod.authToken)).toBe('hello');
		expect(window.localStorage.getItem('ae2web.authToken')).toBe('hello');
	});
});
