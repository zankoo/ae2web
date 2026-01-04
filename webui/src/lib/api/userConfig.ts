import { apiJson } from '$lib/api/client';
import type { InventoryViewConfig } from '$lib/api/types';
import { isMockEnabled, mockFetchInventoryViewConfig, mockSaveInventoryViewConfig } from '$lib/mock/handlers';

export function fetchInventoryViewConfig(terminalId: string): Promise<InventoryViewConfig> {
	if (isMockEnabled()) {
		return mockFetchInventoryViewConfig(terminalId);
	}
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`);
}

export function saveInventoryViewConfig(terminalId: string, config: InventoryViewConfig): Promise<InventoryViewConfig> {
	if (isMockEnabled()) {
		return mockSaveInventoryViewConfig(terminalId, config);
	}
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`, {
		method: 'POST',
		body: JSON.stringify(config ?? {})
	});
}

