import { apiJson } from '$lib/api/client';
import type { CpusResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchCpus } from '$lib/mock/handlers';

export function fetchCpus(terminalId: string): Promise<CpusResponse> {
	if (isMockEnabled()) {
		return mockFetchCpus();
	}
	const params = new URLSearchParams();
	if (terminalId) {
		params.set('terminalId', terminalId);
	}
	return apiJson<CpusResponse>(`/api/cpus?${params}`);
}

