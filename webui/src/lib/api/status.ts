import { apiJson } from '$lib/api/client';
import type { StatusResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchStatus } from '$lib/mock/handlers';

export function fetchStatus(): Promise<StatusResponse> {
	if (isMockEnabled()) {
		return mockFetchStatus();
	}
	return apiJson<StatusResponse>('/api/status');
}
