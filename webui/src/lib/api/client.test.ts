import { describe, expect, it, vi } from 'vitest';

import { apiJson, withTokenQuery } from '$lib/api/client';

function setUrl(url: string): void {
	window.history.replaceState({}, '', url);
}

describe('api client', () => {
	it('adds Authorization header when token exists', async () => {
		setUrl('/?token=abc');

		const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => {
			return new Response(JSON.stringify({ ok: true }), { status: 200 });
		});
		vi.stubGlobal('fetch', fetchMock);

		await apiJson<{ ok: boolean }>('/api/status');

		expect(fetchMock).toHaveBeenCalledTimes(1);
		const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
		const headers = new Headers(init.headers);
		expect(headers.get('Authorization')).toBe('Bearer abc');
	});

	it('uses server error JSON when present', async () => {
		setUrl('/?token=abc');

		const fetchMock = vi.fn(async () => {
			return new Response(JSON.stringify({ error: 'Unauthorized - Invalid or missing token' }), {
				status: 401
			});
		});
		vi.stubGlobal('fetch', fetchMock);

		await expect(apiJson('/api/status')).rejects.toThrow(/Unauthorized/);
	});

	it('appends token query param for EventSource/img', () => {
		setUrl('/?token=xyz');
		expect(withTokenQuery('/api/events/inventory?terminalId=t1')).toContain('token=xyz');
	});
});
