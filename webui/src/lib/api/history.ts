import { apiJson } from '$lib/api/client';

export interface HistoryDataPoint {
	timestamp: number;
	amount: number;
}

export interface HistoryResponse {
	terminalId: string;
	uniqueId: string;
	points: HistoryDataPoint[];
}

/**
 * Fetch history for a single item.
 */
export function fetchHistory(
	terminalId: string,
	uniqueId: string,
	from?: number
): Promise<HistoryResponse> {
	const params = new URLSearchParams();
	params.set('terminalId', terminalId);
	params.set('uniqueId', uniqueId);
	if (from !== undefined && from > 0) {
		params.set('from', String(from));
	}
	return apiJson<HistoryResponse>(`/api/history?${params}`);
}

/**
 * Fetch history for multiple items.
 */
export function fetchHistoryBatch(
	terminalId: string,
	uniqueIds: string[],
	from?: number
): Promise<HistoryResponse[]> {
	const params = new URLSearchParams();
	params.set('terminalId', terminalId);
	params.set('uniqueId', uniqueIds.join(','));
	if (from !== undefined && from > 0) {
		params.set('from', String(from));
	}
	return apiJson<HistoryResponse[]>(`/api/history?${params}`);
}
