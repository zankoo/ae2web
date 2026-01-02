import { get } from 'svelte/store';

import { authToken } from '$lib/stores/session';

type ErrorBody = { error?: string };

async function tryReadError(resp: Response): Promise<string | null> {
	try {
		const json = (await resp.clone().json()) as ErrorBody;
		const error = json?.error;
		return typeof error === 'string' && error.trim() ? error : null;
	} catch {
		return null;
	}
}

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
	const headers = new Headers(init.headers);

	const token = get(authToken);
	if (token) {
		headers.set('Authorization', `Bearer ${token}`);
	}

	if (init.body != null && !headers.has('Content-Type')) {
		headers.set('Content-Type', 'application/json');
	}

	const resp = await fetch(path, { ...init, headers });
	if (!resp.ok) {
		const serverMsg = await tryReadError(resp);
		throw new Error(serverMsg ?? `HTTP ${resp.status}`);
	}

	return (await resp.json()) as T;
}

export function withTokenQuery(url: string): string {
	const token = get(authToken);
	if (!token) {
		return url;
	}

	const parsed = new URL(url, window.location.href);
	parsed.searchParams.set('token', token);
	return parsed.toString();
}

