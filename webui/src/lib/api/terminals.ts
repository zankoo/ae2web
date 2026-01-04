import { apiJson } from '$lib/api/client';
import type { TerminalsResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchTerminals } from '$lib/mock/handlers';

export function fetchTerminals(): Promise<TerminalsResponse> {
	if (isMockEnabled()) {
		return mockFetchTerminals();
	}
	return apiJson<TerminalsResponse>('/api/terminals');
}

