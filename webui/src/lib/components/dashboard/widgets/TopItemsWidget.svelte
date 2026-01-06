<script lang="ts">
	import type { TopItemsOptions, InventoryItem } from '$lib/api/types';
	import { formatProductionRate, type ProductionStatsTracker } from '$lib/utils/productionStats';
	import IconImage from '$lib/components/IconImage.svelte';

	interface Props {
		options: TopItemsOptions;
		terminalId: string;
		inventoryItems: InventoryItem[];
		productionStats: ProductionStatsTracker | null;
	}

	let { options, terminalId, inventoryItems, productionStats }: Props = $props();

	let topItems = $derived.by(() => {
		if (!productionStats || !terminalId) return [];

		const now = Date.now();
		const isProduction = options.mode === 'production';

		const itemsWithRates = inventoryItems
			.filter((item) => {
				if (options.filter === 'items') return item.type === 'item';
				if (options.filter === 'fluids') return item.type === 'fluid';
				return true;
			})
			.map((item) => ({
				item,
				rate: productionStats.getRate(terminalId, item.uniqueId, now) ?? 0
			}))
			.filter((x) => {
				if (isProduction) return x.rate > 0;
				return x.rate < 0;
			})
			.sort((a, b) => {
				if (isProduction) return b.rate - a.rate;
				return a.rate - b.rate;
			});

		return itemsWithRates.slice(0, options.count);
	});

	function displayNameFor(item: InventoryItem): string {
		const name = item?.name;
		if (typeof name === 'string' && name.trim()) {
			return name;
		}
		if (item.id.includes(':')) {
			return item.id.split(':', 2)[1] || item.id;
		}
		return item.id;
	}
</script>

<div class="top-items-list">
	{#if topItems.length === 0}
		<div class="empty-state">
			<svg
				class="w-8 h-8 text-muted-foreground/30 mb-2"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="1.5"
			>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5"
				/>
			</svg>
			<p class="text-xs text-muted-foreground/50">No {options.mode} data yet</p>
		</div>
	{:else}
		{#each topItems as { item, rate }, i (item.uniqueId)}
			<div class="top-item">
				<span class="rank">{i + 1}</span>
				<div class="item-icon">
					<IconImage iconKey={item.iconKey} alt={displayNameFor(item)} className="w-6 h-6" />
				</div>
				<span class="item-name">{displayNameFor(item)}</span>
				<span class="item-rate {rate > 0 ? 'positive' : 'negative'}">
					{formatProductionRate(rate, item.type === 'fluid')}
				</span>
			</div>
		{/each}
	{/if}
</div>

<style>
	.top-items-list {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 2rem 1rem;
		text-align: center;
	}

	.top-item {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.5rem 0.75rem;
		border-radius: 0.5rem;
		background: color-mix(in srgb, var(--color-muted) 30%, transparent);
		transition: background 0.15s ease;
	}

	.top-item:hover {
		background: color-mix(in srgb, var(--color-muted) 50%, transparent);
	}

	.rank {
		width: 1.25rem;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		font-weight: 700;
		color: var(--color-muted-foreground);
		text-align: center;
	}

	.item-icon {
		flex-shrink: 0;
	}

	.item-icon :global(img) {
		image-rendering: pixelated;
	}

	.item-name {
		flex: 1;
		font-size: 0.8rem;
		color: var(--color-foreground);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.item-rate {
		font-family: var(--font-mono);
		font-size: 0.75rem;
		font-weight: 600;
		white-space: nowrap;
	}

	.item-rate.positive {
		color: #10b981;
	}

	.item-rate.negative {
		color: #ef4444;
	}
</style>
