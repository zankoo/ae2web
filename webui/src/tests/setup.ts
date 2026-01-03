import '@testing-library/jest-dom/vitest';

function ensureLocalStorage(): void {
	const storage: any = (globalThis as any).localStorage;
	if (storage && typeof storage.getItem === 'function' && typeof storage.setItem === 'function') {
		return;
	}

	const data = new Map<string, string>();
	const api = {
		getItem: (key: string) => (data.has(key) ? data.get(key)! : null),
		setItem: (key: string, value: string) => data.set(String(key), String(value)),
		removeItem: (key: string) => data.delete(String(key)),
		clear: () => data.clear()
	};

	(globalThis as any).localStorage = api;
	if (typeof window !== 'undefined') {
		(window as any).localStorage = api;
	}
}

ensureLocalStorage();

if (!globalThis.IntersectionObserver) {
	// Minimal stub for components that use lazy-loading in JSDOM.
	globalThis.IntersectionObserver = class IntersectionObserver {
		constructor() {}
		observe() {}
		unobserve() {}
		disconnect() {}
		takeRecords() {
			return [];
		}
	} as any;
}
