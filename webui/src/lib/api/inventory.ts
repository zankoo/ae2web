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

type JsonRecord = Record<string, unknown>;

function isRecord(value: unknown): value is JsonRecord {
	return typeof value === 'object' && value !== null;
}

function readRequiredString(obj: JsonRecord, key: string): string {
	const value = obj[key];
	if (typeof value !== 'string' || !value.trim()) {
		throw new Error(`Invalid ${key}`);
	}
	return value;
}

function readRequiredNumber(obj: JsonRecord, key: string): number {
	const value = obj[key];
	if (typeof value !== 'number' || !Number.isFinite(value)) {
		throw new Error(`Invalid ${key}`);
	}
	return value;
}

function readOptionalString(obj: JsonRecord, key: string): string | undefined {
	const value = obj[key];
	if (typeof value !== 'string') {
		return undefined;
	}
	const trimmed = value.trim();
	return trimmed ? trimmed : undefined;
}

function parseEventJson(evt: Event): unknown {
	const raw = (evt as MessageEvent).data;
	if (typeof raw !== 'string') {
		throw new Error('Invalid SSE payload');
	}
	return JSON.parse(raw) as unknown;
}

export type InventorySseHelloMessage = { terminalId: string; version: number };

export type InventorySseSnapshotMessage = {
	terminalId: string;
	version: number;
	items: InventoryItem[];
	total: number;
};

export type InventorySseDeltaUpdate = {
	uniqueId: string;
	amount: number;
	craftable: boolean;
	name?: string;
};

export type InventorySseDeltaMessage = {
	terminalId: string;
	version: number;
	added: InventoryItem[];
	updated: InventorySseDeltaUpdate[];
	removed: string[];
};

export type InventorySseHandlers = {
	onHello?: (msg: InventorySseHelloMessage) => void;
	onSnapshot?: (msg: InventorySseSnapshotMessage) => void;
	onDelta?: (msg: InventorySseDeltaMessage) => void;
	onError?: (evt: Event) => void;
};

function parseHelloMessage(data: unknown): InventorySseHelloMessage {
	if (!isRecord(data)) {
		throw new Error('Invalid hello message');
	}

	return {
		terminalId: readRequiredString(data, 'terminalId'),
		version: readRequiredNumber(data, 'version')
	};
}

function parseSnapshotMessage(data: unknown): InventorySseSnapshotMessage {
	if (!isRecord(data)) {
		throw new Error('Invalid snapshot message');
	}

	const terminalId = readRequiredString(data, 'terminalId');
	const version = readRequiredNumber(data, 'version');
	const items = data.items;
	const total = data.total;

	if (!Array.isArray(items)) {
		throw new Error('Invalid items');
	}
	if (typeof total !== 'number' || !Number.isFinite(total)) {
		throw new Error('Invalid total');
	}

	return { terminalId, version, items: items as InventoryItem[], total };
}

function parseDeltaMessage(data: unknown): InventorySseDeltaMessage {
	if (!isRecord(data)) {
		throw new Error('Invalid delta message');
	}

	const terminalId = readRequiredString(data, 'terminalId');
	const version = readRequiredNumber(data, 'version');
	const updated = Array.isArray(data.updated) ? data.updated.map(parseDeltaUpdate) : [];

	return {
		terminalId,
		version,
		added: Array.isArray(data.added) ? (data.added as InventoryItem[]) : [],
		updated,
		removed: Array.isArray(data.removed)
			? data.removed
					.filter((value): value is string => typeof value === 'string')
					.map((value) => value.trim())
					.filter(Boolean)
			: []
	};
}

function parseDeltaUpdate(data: unknown): InventorySseDeltaUpdate {
	if (!isRecord(data)) {
		throw new Error('Invalid delta update');
	}

	const uniqueId = readRequiredString(data, 'uniqueId');
	const amount = readRequiredNumber(data, 'amount');
	const craftable = data.craftable;
	if (typeof craftable !== 'boolean') {
		throw new Error('Invalid craftable');
	}

	return {
		uniqueId,
		amount,
		craftable,
		name: readOptionalString(data, 'name')
	};
}

export function openInventorySse(
	terminalId: string,
	handlers: InventorySseHandlers
): EventSource | { close: () => void } {
	if (isMockEnabled()) {
		return mockOpenInventorySse(terminalId, handlers);
	}

	const url = withTokenQuery(`/api/events/inventory?terminalId=${encodeURIComponent(terminalId)}`);
	const es = new EventSource(url);

	es.addEventListener('hello', (evt) => {
		try {
			handlers.onHello?.(parseHelloMessage(parseEventJson(evt)));
		} catch (error) {
			console.warn('Invalid inventory SSE hello message', error);
			handlers.onError?.(evt);
		}
	});

	es.addEventListener('snapshot', (evt) => {
		try {
			handlers.onSnapshot?.(parseSnapshotMessage(parseEventJson(evt)));
		} catch (error) {
			console.warn('Invalid inventory SSE snapshot message', error);
			handlers.onError?.(evt);
		}
	});

	es.addEventListener('delta', (evt) => {
		try {
			handlers.onDelta?.(parseDeltaMessage(parseEventJson(evt)));
		} catch (error) {
			console.warn('Invalid inventory SSE delta message', error);
			handlers.onError?.(evt);
		}
	});

	es.onerror = (evt) => {
		handlers.onError?.(evt);
	};

	return es;
}
