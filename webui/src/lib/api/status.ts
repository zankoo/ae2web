import { apiJson } from '$lib/api/client';
import type { StatusResponse } from '$lib/api/types';

export function fetchStatus(): Promise<StatusResponse> {
	return apiJson<StatusResponse>('/api/status');
}

