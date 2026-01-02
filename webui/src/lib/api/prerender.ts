import { apiJson, withTokenQuery } from '$lib/api/client';
import type { TooltipResponse } from '$lib/api/types';

export function iconUrl(iconKey: string): string {
	return withTokenQuery(`/api/prerender/image?id=${encodeURIComponent(iconKey)}`);
}

export function fetchTooltip(iconKey: string): Promise<TooltipResponse> {
	return apiJson<TooltipResponse>(`/api/prerender/tooltip?id=${encodeURIComponent(iconKey)}`);
}

