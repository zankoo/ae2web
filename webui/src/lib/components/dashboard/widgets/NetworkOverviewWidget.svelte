<script lang="ts">
	import type { NetworkOverviewOptions, InventoryItem } from '$lib/api/types';
	import type { ProductionStatsTracker } from '$lib/utils/productionStats';

	interface Props {
		options: NetworkOverviewOptions;
		terminalId: string;
		inventoryItems: InventoryItem[];
		productionStats: ProductionStatsTracker | null;
	}

	let { options, terminalId, inventoryItems, productionStats }: Props = $props();

	let stats = $derived.by(() => {
		if (!productionStats || !terminalId) {
			return { production: 0, consumption: 0, netChange: 0, activeCount: 0 };
		}

		const now = Date.now();
		let production = 0;
		let consumption = 0;
		let activeCount = 0;

		for (const item of inventoryItems) {
			const rate = productionStats.getRate(terminalId, item.uniqueId, now);
			if (rate === null || rate === 0) continue;

			activeCount++;
			if (rate > 0) {
				production += rate;
			} else {
				consumption += Math.abs(rate);
			}
		}

		return {
			production,
			consumption,
			netChange: production - consumption,
			activeCount
		};
	});

	function formatRate(rate: number): string {
		const absRate = Math.abs(rate);
		if (absRate >= 1000) {
			return `${(rate / 1000).toFixed(1)}K`;
		}
		if (absRate >= 1) {
			return rate.toFixed(1);
		}
		return rate.toFixed(2);
	}
</script>

<div class="grid grid-cols-1 gap-3">
	{#if options.showProduction}
		<div class="stat-card production">
			<div class="stat-icon">
				<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						d="M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941"
					/>
				</svg>
			</div>
			<div class="stat-content">
				<div class="stat-label">Production</div>
				<div class="stat-value">+{formatRate(stats.production)}/s</div>
			</div>
		</div>
	{/if}

	{#if options.showConsumption}
		<div class="stat-card consumption">
			<div class="stat-icon">
				<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						d="M2.25 6L9 12.75l4.286-4.286a11.948 11.948 0 014.306 6.43l.776 2.898m0 0l3.182-5.511m-3.182 5.51l-5.511-3.181"
					/>
				</svg>
			</div>
			<div class="stat-content">
				<div class="stat-label">Consumption</div>
				<div class="stat-value">-{formatRate(stats.consumption)}/s</div>
			</div>
		</div>
	{/if}

	{#if options.showNetChange}
		<div class="stat-card {stats.netChange >= 0 ? 'positive' : 'negative'}">
			<div class="stat-icon">
				<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						d="M3 7.5L7.5 3m0 0L12 7.5M7.5 3v13.5m13.5 0L16.5 21m0 0L12 16.5m4.5 4.5V7.5"
					/>
				</svg>
			</div>
			<div class="stat-content">
				<div class="stat-label">Net Change</div>
				<div class="stat-value">
					{stats.netChange >= 0 ? '+' : ''}{formatRate(stats.netChange)}/s
				</div>
			</div>
		</div>
	{/if}

	<div class="stat-card neutral">
		<div class="stat-icon">
			<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6z"
				/>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25z"
				/>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6z"
				/>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z"
				/>
			</svg>
		</div>
		<div class="stat-content">
			<div class="stat-label">Active Items</div>
			<div class="stat-value">{stats.activeCount}</div>
		</div>
	</div>
</div>

<style>
	.stat-card {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.75rem 1rem;
		border-radius: 0.75rem;
		transition: transform 0.15s ease;
	}

	.stat-card:hover {
		transform: translateX(2px);
	}

	.stat-card.production {
		background: color-mix(in srgb, #10b981 10%, transparent);
		color: #10b981;
	}

	.stat-card.consumption {
		background: color-mix(in srgb, #ef4444 10%, transparent);
		color: #ef4444;
	}

	.stat-card.positive {
		background: color-mix(in srgb, #10b981 10%, transparent);
		color: #10b981;
	}

	.stat-card.negative {
		background: color-mix(in srgb, #ef4444 10%, transparent);
		color: #ef4444;
	}

	.stat-card.neutral {
		background: color-mix(in srgb, var(--color-accent) 10%, transparent);
		color: var(--color-accent);
	}

	.stat-icon {
		flex-shrink: 0;
	}

	.stat-content {
		flex: 1;
		min-width: 0;
	}

	.stat-label {
		font-size: 0.7rem;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		font-weight: 600;
		opacity: 0.8;
	}

	.stat-value {
		font-family: var(--font-mono);
		font-size: 1.25rem;
		font-weight: 700;
		line-height: 1.2;
	}
</style>
