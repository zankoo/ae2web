import { apiJson } from '$lib/api/client';
import type { CpuProcessResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchCpuProcess } from '$lib/mock/handlers';

export function fetchCpuProcess(terminalId: string, cpuId: string): Promise<CpuProcessResponse> {
	if (isMockEnabled()) {
		return mockFetchCpuProcess(cpuId);
	}
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	if (cpuId) params.set('cpuId', cpuId);
	return apiJson<CpuProcessResponse>(`/api/cpus/process?${params}`);
}

