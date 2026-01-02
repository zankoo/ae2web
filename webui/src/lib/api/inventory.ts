import { apiJson, withTokenQuery } from '$lib/api/client';
import type { InventoryResponse } from '$lib/api/types';

export type InventoryFilter = 'all' | 'items' | 'fluids';

export async function fetchInventory(
	terminalId: string,
	filter: InventoryFilter
): Promise<InventoryResponse> {
	const params = new URLSearchParams({ type: filter });
	if (terminalId) {
		params.set('terminalId', terminalId);
	}
	return apiJson<InventoryResponse>(`/api/items?${params}`);
}

export type InventorySseMessage = { terminalId?: string; version?: number };

export type InventorySseHandlers = {
	onHello?: (msg: InventorySseMessage) => void;
	onInventory?: (msg: InventorySseMessage) => void;
	onError?: (evt: Event) => void;
};

export function openInventorySse(terminalId: string, handlers: InventorySseHandlers): EventSource {
	const url = withTokenQuery(`/api/events/inventory?terminalId=${encodeURIComponent(terminalId)}`);
	const es = new EventSource(url);

	es.addEventListener('hello', (evt) => {
		try {
			const msg = JSON.parse((evt as MessageEvent).data) as InventorySseMessage;
			handlers.onHello?.(msg);
		} catch {
			handlers.onHello?.({});
		}
	});

	es.addEventListener('inventory', (evt) => {
		try {
			const msg = JSON.parse((evt as MessageEvent).data) as InventorySseMessage;
			handlers.onInventory?.(msg);
		} catch {
			handlers.onInventory?.({});
		}
	});

	es.onerror = (evt) => {
		handlers.onError?.(evt);
	};

	return es;
}

