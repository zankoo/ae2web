import { get } from 'svelte/store';

import { authToken } from '$lib/stores/session';
import type { GenericStackDto } from '$lib/api/types';

type ErrorBody = { error?: unknown; missing?: unknown };

export class CraftMissingError extends Error {
	readonly missing: GenericStackDto[];

	constructor(message: string, missing: GenericStackDto[]) {
		super(message);
		this.name = 'CraftMissingError';
		this.missing = missing;
	}
}

async function tryReadErrorBody(resp: Response): Promise<ErrorBody | null> {
	try {
		return (await resp.clone().json()) as ErrorBody;
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
		const body = await tryReadErrorBody(resp);
		const errorMsg =
			body && typeof body.error === 'string' && body.error.trim() ? body.error.trim() : null;

		if (body && Array.isArray(body.missing)) {
			throw new CraftMissingError(
				errorMsg ?? 'Missing items for crafting',
				body.missing as GenericStackDto[]
			);
		}

		throw new Error(errorMsg ?? `HTTP ${resp.status}`);
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
