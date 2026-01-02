import { apiJson } from '$lib/api/client';
import type { TerminalsResponse } from '$lib/api/types';

export function fetchTerminals(): Promise<TerminalsResponse> {
	return apiJson<TerminalsResponse>('/api/terminals');
}

