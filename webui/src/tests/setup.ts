import '@testing-library/jest-dom/vitest';

function ensureLocalStorage(): void {
	const data = new Map<string, string>();
	const api: Storage = {
		get length() {
			return data.size;
		},
		key: (index: number) => Array.from(data.keys())[index] ?? null,
		getItem: (key: string) => (data.has(key) ? data.get(key)! : null),
		setItem: (key: string, value: string) => data.set(String(key), String(value)),
		removeItem: (key: string) => data.delete(String(key)),
		clear: () => data.clear()
	} as Storage;

	(globalThis as any).localStorage = api;
	if (typeof window !== 'undefined') {
		(window as any).localStorage = api;
	}
}

ensureLocalStorage();

if (typeof window !== 'undefined' && !window.matchMedia) {
	(window as any).matchMedia = (query: string) => ({
		matches: false,
		media: query,
		onchange: null,
		addListener: () => {},
		removeListener: () => {},
		addEventListener: () => {},
		removeEventListener: () => {},
		dispatchEvent: () => false
	});
}

if (!globalThis.ResizeObserver) {
	globalThis.ResizeObserver = class ResizeObserver {
		constructor() {}
		observe() {}
		unobserve() {}
		disconnect() {}
	} as any;
}

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

if (typeof HTMLCanvasElement !== 'undefined') {
	// JSDOM logs noisy "Not implemented" messages for canvas APIs.
	// Provide a minimal 2d context so chart libraries can initialize quietly.
	(HTMLCanvasElement.prototype as any).getContext = function getContext(type: unknown): unknown {
		if (type !== '2d') {
			return null;
		}

		const noop = () => {};

		return new Proxy(
			{
				canvas: this,
				save: noop,
				restore: noop,
				scale: noop,
				rotate: noop,
				translate: noop,
				transform: noop,
				setTransform: noop,
				resetTransform: noop,
				clearRect: noop,
				fillRect: noop,
				strokeRect: noop,
				beginPath: noop,
				closePath: noop,
				moveTo: noop,
				lineTo: noop,
				bezierCurveTo: noop,
				quadraticCurveTo: noop,
				arc: noop,
				rect: noop,
				clip: noop,
				fill: noop,
				stroke: noop,
				drawImage: noop,
				fillText: noop,
				measureText: () => ({ width: 0 }),
				createLinearGradient: () => ({ addColorStop: noop }),
				createRadialGradient: () => ({ addColorStop: noop }),
				createPattern: () => null,
				getImageData: () => ({ data: new Uint8ClampedArray(0) }),
				putImageData: noop
			},
			{
				get(target, prop) {
					if (prop in target) {
						return (target as any)[prop];
					}
					return noop;
				}
			}
		);
	};
}
