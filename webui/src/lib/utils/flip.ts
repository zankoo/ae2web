export type SortMode = 'amount' | 'name';

export function computeFlipDuration(
	sort: SortMode,
	itemCount: number,
	requestedByUser: boolean,
	{ maxItems = 400, durationMs = 180 }: { maxItems?: number; durationMs?: number } = {}
): number {
	if (!requestedByUser) return 0;
	if (sort !== 'amount') return 0;
	if (itemCount > maxItems) return 0;
	return durationMs;
}

