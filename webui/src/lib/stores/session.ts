import { readable } from 'svelte/store';

export function readTokenFromUrl(url: URL): string {
	return url.searchParams.get('token') ?? '';
}

export const authToken = readable<string>('', (set) => {
	try {
		set(readTokenFromUrl(new URL(window.location.href)));
	} catch {
		set('');
	}
	return () => {};
});

