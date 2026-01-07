import { apiJson } from '$lib/api/client';
import type { InventoryViewConfig } from '$lib/api/types';
import {
	isMockEnabled,
	mockFetchInventoryViewConfig,
	mockSaveInventoryViewConfig
} from '$lib/mock/handlers';

/**
 * Normalize and validate an InventoryViewConfig from the server.
 * Ensures all fields have valid types and values.
 */
export function normalizeInventoryViewConfig(
	raw: InventoryViewConfig | null | undefined
): InventoryViewConfig {
	const whitelist = Array.isArray(raw?.whitelist)
		? raw.whitelist.filter((x) => typeof x === 'string' && x.trim())
		: [];
	const blacklist = Array.isArray(raw?.blacklist)
		? raw.blacklist.filter((x) => typeof x === 'string' && x.trim())
		: [];
	const categories = Array.isArray(raw?.categories)
		? raw.categories
				.filter(
					(x) =>
						x &&
						typeof x.name === 'string' &&
						x.name.trim() &&
						typeof x.tagRegex === 'string' &&
						x.tagRegex.trim()
				)
				.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
		: [];
	const collapsed =
		raw?.collapsed && typeof raw.collapsed === 'object'
			? { ...(raw.collapsed as Record<string, boolean>) }
			: {};
	return { whitelist, blacklist, categories, collapsed };
}

export async function fetchInventoryViewConfig(terminalId: string): Promise<InventoryViewConfig> {
	if (isMockEnabled()) {
		const raw = await mockFetchInventoryViewConfig(terminalId);
		return normalizeInventoryViewConfig(raw);
	}
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	const raw = await apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`);
	return normalizeInventoryViewConfig(raw);
}

export async function saveInventoryViewConfig(
	terminalId: string,
	config: InventoryViewConfig
): Promise<InventoryViewConfig> {
	if (isMockEnabled()) {
		const raw = await mockSaveInventoryViewConfig(terminalId, config);
		return normalizeInventoryViewConfig(raw);
	}
	const params = new URLSearchParams();
	if (terminalId) params.set('terminalId', terminalId);
	const raw = await apiJson<InventoryViewConfig>(`/api/user/inventoryConfig?${params}`, {
		method: 'POST',
		body: JSON.stringify(config ?? {})
	});
	return normalizeInventoryViewConfig(raw);
}
