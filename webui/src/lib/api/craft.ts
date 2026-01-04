import { apiJson } from '$lib/api/client';
import type { CraftOkResponse, CraftRequest } from '$lib/api/types';
import { isMockEnabled, mockSubmitCraft } from '$lib/mock/handlers';

export function submitCraft(request: CraftRequest): Promise<CraftOkResponse> {
	if (isMockEnabled()) {
		return mockSubmitCraft();
	}
	return apiJson<CraftOkResponse>('/api/craft', {
		method: 'POST',
		body: JSON.stringify(request)
	});
}

