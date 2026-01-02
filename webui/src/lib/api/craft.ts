import { apiJson } from '$lib/api/client';
import type { CraftOkResponse, CraftRequest } from '$lib/api/types';

export function submitCraft(request: CraftRequest): Promise<CraftOkResponse> {
	return apiJson<CraftOkResponse>('/api/craft', {
		method: 'POST',
		body: JSON.stringify(request)
	});
}

