import { apiJson } from '$lib/api/client';
import type { CpuProcessResponse } from '$lib/api/types';

export function fetchCpuProcess(terminalId: string, cpuId: string): Promise<CpuProcessResponse> {
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	if (cpuId) params.set('cpuId', cpuId);
	return apiJson<CpuProcessResponse>(`/api/cpus/process?${params}`);
}

