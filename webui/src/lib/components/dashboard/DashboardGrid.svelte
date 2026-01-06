<script lang="ts">
	import { dndzone } from 'svelte-dnd-action';
	import type {
		WidgetConfig,
		InventoryItem,
		NetworkOverviewOptions,
		TopItemsOptions,
		TrackedItemsOptions,
		HistoryTrendOptions
	} from '$lib/api/types';
	import type { ProductionStatsTracker } from '$lib/utils/productionStats';
	import WidgetCard from './WidgetCard.svelte';
	import NetworkOverviewWidget from './widgets/NetworkOverviewWidget.svelte';
	import TopItemsWidget from './widgets/TopItemsWidget.svelte';
	import TrackedItemsWidget from './widgets/TrackedItemsWidget.svelte';
	import HistoryTrendWidget from './widgets/HistoryTrendWidget.svelte';

	interface Props {
		widgets: WidgetConfig[];
		terminalId: string;
		inventoryItems: InventoryItem[];
		productionStats: ProductionStatsTracker | null;
		onWidgetsChange: (widgets: WidgetConfig[]) => void;
		onEditWidget: (widget: WidgetConfig) => void;
		onDeleteWidget: (widgetId: string) => void;
	}

	let {
		widgets,
		terminalId,
		inventoryItems,
		productionStats,
		onWidgetsChange,
		onEditWidget,
		onDeleteWidget
	}: Props = $props();

	const flipDurationMs = 200;

	function handleDndConsider(e: CustomEvent<{ items: WidgetConfig[] }>) {
		widgets = e.detail.items;
	}

	function handleDndFinalize(e: CustomEvent<{ items: WidgetConfig[] }>) {
		widgets = e.detail.items;
		onWidgetsChange(widgets);
	}

	function getWidgetStyle(widget: WidgetConfig): string {
		return `grid-column: span ${widget.w}; grid-row: span ${widget.h};`;
	}
</script>

<div class="dashboard-grid-container">
	{#if widgets.length === 0}
		<div class="empty-state">
			<svg
				class="w-16 h-16 text-muted-foreground/30 mb-4"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="1"
			>
				<path
					stroke-linecap="round"
					stroke-linejoin="round"
					d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z"
				/>
			</svg>
			<h3 class="text-lg font-display text-muted-foreground mb-2">No Widgets</h3>
			<p class="text-sm text-muted-foreground/70">Click "Add Widget" to get started</p>
		</div>
	{:else}
		<div
			class="dashboard-grid"
			use:dndzone={{
				items: widgets,
				flipDurationMs,
				dropTargetStyle: {},
				dragDisabled: false,
				type: 'widget'
			}}
			onconsider={handleDndConsider}
			onfinalize={handleDndFinalize}
		>
			{#each widgets as widget (widget.id)}
				<div class="widget-wrapper" style={getWidgetStyle(widget)}>
					<WidgetCard
						config={widget}
						onEdit={() => onEditWidget(widget)}
						onDelete={() => onDeleteWidget(widget.id)}
					>
						{#if widget.type === 'network-overview'}
							<NetworkOverviewWidget
								options={widget.options as NetworkOverviewOptions}
								{terminalId}
								{inventoryItems}
								{productionStats}
							/>
						{:else if widget.type === 'top-items'}
							<TopItemsWidget
								options={widget.options as TopItemsOptions}
								{terminalId}
								{inventoryItems}
								{productionStats}
							/>
						{:else if widget.type === 'tracked-items'}
							<TrackedItemsWidget
								options={widget.options as TrackedItemsOptions}
								{terminalId}
								{inventoryItems}
								{productionStats}
							/>
						{:else if widget.type === 'history-trend'}
							<HistoryTrendWidget
								options={widget.options as HistoryTrendOptions}
								{terminalId}
								{inventoryItems}
								{productionStats}
							/>
						{/if}
					</WidgetCard>
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.dashboard-grid-container {
		width: 100%;
		min-height: 100%;
	}

	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		min-height: 400px;
		text-align: center;
	}

	.dashboard-grid {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 1rem;
		min-height: 100%;
	}

	.widget-wrapper {
		min-height: 180px;
	}

	/* Responsive columns */
	@media (max-width: 1280px) {
		.dashboard-grid {
			grid-template-columns: repeat(3, 1fr);
		}
	}

	@media (max-width: 1024px) {
		.dashboard-grid {
			grid-template-columns: repeat(2, 1fr);
		}
	}

	@media (max-width: 640px) {
		.dashboard-grid {
			grid-template-columns: 1fr;
		}
	}

	/* Drag styles */
	:global(.widget-wrapper[data-dnd-ghost]) {
		opacity: 0.7;
	}

	:global(.widget-wrapper[data-dnd-placeholder]) {
		opacity: 0.3;
	}
</style>
