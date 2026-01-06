import { apiJson } from '$lib/api/client';
import type { DashboardConfig } from '$lib/api/types';

export function fetchDashboardConfig(terminalId: string): Promise<DashboardConfig> {
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<DashboardConfig>(`/api/user/dashboardConfig?${params}`);
}

export function saveDashboardConfig(
	terminalId: string,
	config: DashboardConfig
): Promise<DashboardConfig> {
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<DashboardConfig>(`/api/user/dashboardConfig?${params}`, {
		method: 'POST',
		body: JSON.stringify(config ?? {})
	});
}
