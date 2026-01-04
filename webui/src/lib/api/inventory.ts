import { apiJson, withTokenQuery } from '$lib/api/client';
import type { InventoryItem, InventoryResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchItems, mockOpenInventorySse } from '$lib/mock/handlers';

export type InventoryFilter = 'all' | 'items' | 'fluids';

export async function fetchInventory(
	terminalId: string,
	filter: InventoryFilter
): Promise<InventoryResponse> {
	if (isMockEnabled()) {
		return mockFetchItems({ terminalId });
	}
	const params = new URLSearchParams({ type: filter });
	if (terminalId) {
		params.set('terminalId', terminalId);
	}
	return apiJson<InventoryResponse>(`/api/items?${params}`);
}

export type InventorySseHelloMessage = { terminalId?: string; version?: number };

export type InventorySseSnapshotMessage = {
	terminalId?: string;
	version?: number;
	items?: InventoryItem[];
	total?: number;
};

export type InventorySseDeltaUpdate = { uniqueId: string; amount: number; craftable: boolean; name?: string };

export type InventorySseDeltaMessage = {
	terminalId?: string;
	version?: number;
	added?: InventoryItem[];
	updated?: InventorySseDeltaUpdate[];
	removed?: string[];
};

export type InventorySseHandlers = {
	onHello?: (msg: InventorySseHelloMessage) => void;
	onSnapshot?: (msg: InventorySseSnapshotMessage) => void;
	onDelta?: (msg: InventorySseDeltaMessage) => void;
	onError?: (evt: Event) => void;
};

export function openInventorySse(terminalId: string, handlers: InventorySseHandlers): EventSource | { close: () => void } {
	if (isMockEnabled()) {
		return mockOpenInventorySse(terminalId, handlers);
	}

	const url = withTokenQuery(`/api/events/inventory?terminalId=${encodeURIComponent(terminalId)}`);
	const es = new EventSource(url);

	es.addEventListener('hello', (evt) => {
		try {
			const msg = JSON.parse((evt as MessageEvent).data) as InventorySseHelloMessage;
			handlers.onHello?.(msg);
		} catch {
			handlers.onHello?.({});
		}
	});

	es.addEventListener('snapshot', (evt) => {
		try {
			const msg = JSON.parse((evt as MessageEvent).data) as InventorySseSnapshotMessage;
			handlers.onSnapshot?.(msg);
		} catch {
			handlers.onSnapshot?.({});
		}
	});

	es.addEventListener('delta', (evt) => {
		try {
			const msg = JSON.parse((evt as MessageEvent).data) as InventorySseDeltaMessage;
			handlers.onDelta?.(msg);
		} catch {
			handlers.onDelta?.({});
		}
	});

	es.onerror = (evt) => {
		handlers.onError?.(evt);
	};

	return es;
}
