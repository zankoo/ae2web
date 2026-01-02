import { apiJson } from '$lib/api/client';
import type { CpusResponse } from '$lib/api/types';

export function fetchCpus(terminalId: string): Promise<CpusResponse> {
	const params = new URLSearchParams();
	if (terminalId) {
		params.set('terminalId', terminalId);
	}
	return apiJson<CpusResponse>(`/api/cpus?${params}`);
}

