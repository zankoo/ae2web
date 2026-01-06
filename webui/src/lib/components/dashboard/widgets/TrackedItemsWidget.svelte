<script lang="ts">
	import type { TrackedItemsOptions, InventoryItem } from '$lib/api/types';
	import { formatProductionRate, type ProductionStatsTracker } from '$lib/utils/productionStats';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';
	import IconImage from '$lib/components/IconImage.svelte';

	interface Props {
		options: TrackedItemsOptions;
		terminalId: string;
		inventoryItems: InventoryItem[];
		productionStats: ProductionStatsTracker | null;
	}

	let { options, terminalId, inventoryItems, productionStats }: Props = $props();

	let trackedItems = $derived.by(() => {
		if (!options.trackedIds || options.trackedIds.length === 0) return [];

		const now = Date.now();
		const itemMap = new Map(inventoryItems.map((item) => [item.uniqueId, item]));

		return options.trackedIds
			.map((id) => {
				const item = itemMap.get(id);
				if (!item) return null;

				const rate = productionStats?.getRate(terminalId, id, now) ?? null;
				return { item, rate };
			})
			.filter((x): x is { item: InventoryItem; rate: number | null } => x !== null);
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

	function formatItemAmount(item: InventoryItem): string {
		return item.type === 'fluid'
			? formatFluidAmount(Number(item.amount))
			: formatAmount(Number(item.amount));
	}
</script>

<div class="tracked-items-list">
	{#if trackedItems.length === 0}
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
					d="M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z"
				/>
			</svg>
			<p class="text-xs text-muted-foreground/50">No tracked items</p>
			<p class="text-xs text-muted-foreground/30 mt-1">Edit this widget to add items</p>
		</div>
	{:else}
		{#each trackedItems as { item, rate } (item.uniqueId)}
			<div class="tracked-item">
				<div class="item-icon">
					<IconImage iconKey={item.iconKey} alt={displayNameFor(item)} className="w-8 h-8" />
				</div>
				<div class="item-info">
					<span class="item-name">{displayNameFor(item)}</span>
					<span class="item-amount">{formatItemAmount(item)}</span>
				</div>
				{#if rate !== null && rate !== 0}
					<span class="item-rate {rate > 0 ? 'positive' : 'negative'}">
						{formatProductionRate(rate, item.type === 'fluid')}
					</span>
				{:else}
					<span class="item-rate idle">--</span>
				{/if}
			</div>
		{/each}
	{/if}
</div>

<style>
	.tracked-items-list {
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

	.tracked-item {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		padding: 0.5rem 0.75rem;
		border-radius: 0.5rem;
		background: color-mix(in srgb, var(--color-muted) 30%, transparent);
		transition: background 0.15s ease;
	}

	.tracked-item:hover {
		background: color-mix(in srgb, var(--color-muted) 50%, transparent);
	}

	.item-icon {
		flex-shrink: 0;
	}

	.item-icon :global(img) {
		image-rendering: pixelated;
	}

	.item-info {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 0.125rem;
	}

	.item-name {
		font-size: 0.8rem;
		color: var(--color-foreground);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.item-amount {
		font-family: var(--font-mono);
		font-size: 0.7rem;
		color: var(--color-muted-foreground);
	}

	.item-rate {
		font-family: var(--font-mono);
		font-size: 0.75rem;
		font-weight: 600;
		white-space: nowrap;
		padding: 0.25rem 0.5rem;
		border-radius: 0.375rem;
	}

	.item-rate.positive {
		color: #10b981;
		background: color-mix(in srgb, #10b981 10%, transparent);
	}

	.item-rate.negative {
		color: #ef4444;
		background: color-mix(in srgb, #ef4444 10%, transparent);
	}

	.item-rate.idle {
		color: var(--color-muted-foreground);
		background: transparent;
	}
</style>
