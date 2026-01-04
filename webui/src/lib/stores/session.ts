import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export function readTokenFromUrl(url: URL): string {
	return url.searchParams.get('token') ?? '';
}

const STORAGE_KEY = 'ae2web.authToken';

// Check for mock mode early and set token if needed
function initMockModeToken(): void {
	if (!browser) return;
	try {
		const urlParams = new URLSearchParams(window.location.search);
		if (urlParams.get('mock') === 'true') {
			localStorage.setItem('ae2web-mock', 'true');
			localStorage.setItem(STORAGE_KEY, 'mock-token');
		}
	} catch {
		// ignore
	}
}

// Initialize mock mode before reading token
initMockModeToken();

type StorageLike = {
	getItem: (key: string) => string | null;
	setItem: (key: string, value: string) => void;
	removeItem: (key: string) => void;
};

function normalizeToken(token: string): string {
	return String(token ?? '').trim();
}

function safeStorage(): StorageLike | null {
	try {
		const storage: any = (globalThis as any).localStorage;
		if (!storage) return null;
		if (typeof storage.getItem !== 'function') return null;
		if (typeof storage.setItem !== 'function') return null;
		if (typeof storage.removeItem !== 'function') return null;
		return storage as StorageLike;
	} catch {
		return null;
	}
}

function readTokenFromStorage(): string {
	const storage = safeStorage();
	if (!storage) return '';
	const raw = storage.getItem(STORAGE_KEY) ?? '';
	return normalizeToken(raw);
}

function initialToken(): string {
	try {
		const fromUrl = normalizeToken(readTokenFromUrl(new URL(window.location.href)));
		if (fromUrl) return fromUrl;
	} catch {
		// ignore
	}
	return readTokenFromStorage();
}

export const authToken = writable<string>(initialToken());

authToken.subscribe((token) => {
	const storage = safeStorage();
	if (!storage) return;

	const normalized = normalizeToken(token);
	if (normalized) {
		storage.setItem(STORAGE_KEY, normalized);
	} else {
		storage.removeItem(STORAGE_KEY);
	}
});

export function setAuthToken(token: string): void {
	authToken.set(normalizeToken(token));
}

