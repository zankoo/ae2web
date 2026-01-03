export function formatAmount(num: number): string {
	if (num >= 1e18) return `${(num / 1e18).toFixed(1).replace('.0', '')}E`;
	if (num >= 1e15) return `${(num / 1e15).toFixed(1).replace('.0', '')}P`;
	if (num >= 1e12) return `${(num / 1e12).toFixed(1).replace('.0', '')}T`;
	if (num >= 1e9) return `${(num / 1e9).toFixed(1).replace('.0', '')}G`;
	if (num >= 1e6) return `${(num / 1e6).toFixed(1).replace('.0', '')}M`;
	if (num >= 1e3) return `${(num / 1e3).toFixed(1).replace('.0', '')}K`;
	return String(num);
}

export function formatNumber(num: number): string {
	return String(num).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export function formatFluidAmount(milliBuckets: number): string {
	if (!Number.isFinite(milliBuckets)) {
		return '';
	}

	const buckets = milliBuckets / 1000;
	if (buckets >= 1000) {
		return `${formatAmount(buckets)}B`;
	}

	const text = buckets.toFixed(3).replace(/\.?0+$/, '');
	return `${text}B`;
}
