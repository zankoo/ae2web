import { apiJson, withTokenQuery } from '$lib/api/client';
import type { TooltipResponse } from '$lib/api/types';
import { isMockEnabled, mockFetchTooltip } from '$lib/mock/handlers';

export function iconUrl(iconKey: string): string {
	return withTokenQuery(`/api/prerender/image?id=${encodeURIComponent(iconKey)}`);
}

export function fetchTooltip(iconKey: string): Promise<TooltipResponse> {
	if (isMockEnabled()) {
		return mockFetchTooltip(iconKey);
	}
	return apiJson<TooltipResponse>(`/api/prerender/tooltip?id=${encodeURIComponent(iconKey)}`);
}

