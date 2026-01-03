import { apiJson } from '$lib/api/client';
import type { InventoryViewConfig } from '$lib/api/types';

export function fetchInventoryViewConfig(terminalId: string): Promise<InventoryViewConfig> {
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`);
}

export function saveInventoryViewConfig(terminalId: string, config: InventoryViewConfig): Promise<InventoryViewConfig> {
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	return apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`, {
		method: 'POST',
		body: JSON.stringify(config ?? {})
	});
}

