<script lang="ts">
	import type { WidgetType, WidgetConfig, InventoryItem } from '$lib/api/types';

	interface Props {
		open: boolean;
		inventoryItems: InventoryItem[];
		onAdd: (widget: WidgetConfig) => void;
		onClose: () => void;
	}

	let { open, inventoryItems, onAdd, onClose }: Props = $props();

	// Widget type definitions
	const widgetTypes: Array<{
		type: WidgetType;
		title: string;
		description: string;
		icon: string;
		defaultW: number;
		defaultH: number;
	}> = [
		{
			type: 'network-overview',
			title: 'Network Overview',
			description: 'Shows total production, consumption, and net change across all items',
			icon: '📊',
			defaultW: 1,
			defaultH: 1
		},
		{
			type: 'top-items',
			title: 'Top Items',
			description: 'Displays items with highest production or consumption rates',
			icon: '🔝',
			defaultW: 1,
			defaultH: 2
		},
		{
			type: 'tracked-items',
			title: 'Tracked Items',
			description: 'Monitor specific items you select',
			icon: '📌',
			defaultW: 1,
			defaultH: 2
		},
		{
			type: 'history-trend',
			title: 'History Trend',
			description: 'Shows production/consumption trend over time for a single item',
			icon: '📈',
			defaultW: 2,
			defaultH: 1
		}
	];

	// For history-trend, allow selecting an item
	let selectedWidgetType: WidgetType | null = $state(null);
	let selectedItemId: string | null = $state(null);
	let topItemsMode: 'production' | 'consumption' = $state('production');
	const historyItemSelectId = 'add-widget-history-item';

	function generateId(): string {
		return `widget-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
	}

	function selectWidgetType(type: WidgetType): void {
		selectedWidgetType = type;
		selectedItemId = null;
	}

	function confirmAdd(): void {
		if (!selectedWidgetType) return;

		const def = widgetTypes.find((w) => w.type === selectedWidgetType);
		if (!def) return;

		let widget: WidgetConfig;

		switch (selectedWidgetType) {
			case 'network-overview':
				widget = {
					id: generateId(),
					type: 'network-overview',
					title: 'Network Overview',
					w: def.defaultW,
					h: def.defaultH,
					x: 0,
					y: 0,
					options: {
						showProduction: true,
						showConsumption: true,
						showNetChange: true
					}
				};
				break;

			case 'top-items':
				widget = {
					id: generateId(),
					type: 'top-items',
					title: topItemsMode === 'production' ? 'Top Production' : 'Top Consumption',
					w: def.defaultW,
					h: def.defaultH,
					x: 0,
					y: 0,
					options: {
						mode: topItemsMode,
						count: 5,
						filter: 'all'
					}
				};
				break;

			case 'tracked-items':
				widget = {
					id: generateId(),
					type: 'tracked-items',
					title: 'Tracked Items',
					w: def.defaultW,
					h: def.defaultH,
					x: 0,
					y: 0,
					options: {
						trackedIds: []
					}
				};
				break;

			case 'history-trend': {
				if (!selectedItemId) {
					return;
				}
				const item = inventoryItems.find((i) => i.uniqueId === selectedItemId);
				widget = {
					id: generateId(),
					type: 'history-trend',
					title: item?.name || 'History Trend',
					w: def.defaultW,
					h: def.defaultH,
					x: 0,
					y: 0,
					options: {
						itemId: selectedItemId,
						timeRange: '1h'
					}
				};
				break;
			}

			default:
				return;
		}

		onAdd(widget);
		handleClose();
	}

	function handleClose(): void {
		selectedWidgetType = null;
		selectedItemId = null;
		topItemsMode = 'production';
		onClose();
	}

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

{#if open}
	<dialog class="modal modal-open" onkeydown={(e) => e.key === 'Escape' && handleClose()}>
		<div class="modal-box max-w-lg">
			<h3 class="font-bold text-lg mb-4">Add Widget</h3>

			{#if !selectedWidgetType}
				<!-- Widget Type Selection -->
				<div class="grid grid-cols-2 gap-3">
					{#each widgetTypes as wt (wt.type)}
						<button
							type="button"
							class="card bg-base-200 hover:bg-base-300 transition-colors cursor-pointer p-4 text-left"
							onclick={() => selectWidgetType(wt.type)}
						>
							<div class="text-2xl mb-2">{wt.icon}</div>
							<div class="font-semibold text-sm">{wt.title}</div>
							<div class="text-xs text-muted-foreground mt-1">{wt.description}</div>
						</button>
					{/each}
				</div>
			{:else if selectedWidgetType === 'top-items'}
				<!-- Top Items Mode Selection -->
				<div class="space-y-4">
					<button
						type="button"
						class="text-sm text-muted-foreground hover:text-foreground flex items-center gap-1"
						onclick={() => (selectedWidgetType = null)}
					>
						<svg
							class="w-4 h-4"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M15.75 19.5L8.25 12l7.5-7.5"
							/>
						</svg>
						Back
					</button>

					<div class="form-control">
						<label class="label cursor-pointer">
							<span class="label-text">Top Production</span>
							<input
								type="radio"
								name="top-items-mode"
								class="radio radio-primary"
								checked={topItemsMode === 'production'}
								onchange={() => (topItemsMode = 'production')}
							/>
						</label>
						<label class="label cursor-pointer">
							<span class="label-text">Top Consumption</span>
							<input
								type="radio"
								name="top-items-mode"
								class="radio radio-primary"
								checked={topItemsMode === 'consumption'}
								onchange={() => (topItemsMode = 'consumption')}
							/>
						</label>
					</div>

					<button type="button" class="btn btn-primary w-full" onclick={confirmAdd}>
						Add Widget
					</button>
				</div>
			{:else if selectedWidgetType === 'history-trend'}
				<!-- Item Selection for History Trend -->
				<div class="space-y-4">
					<button
						type="button"
						class="text-sm text-muted-foreground hover:text-foreground flex items-center gap-1"
						onclick={() => (selectedWidgetType = null)}
					>
						<svg
							class="w-4 h-4"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M15.75 19.5L8.25 12l7.5-7.5"
							/>
						</svg>
						Back
					</button>

					<div class="form-control">
						<label class="label" for={historyItemSelectId}>
							<span class="label-text">Select an item to track</span>
						</label>
						<select
							id={historyItemSelectId}
							class="select select-bordered w-full"
							value={selectedItemId ?? ''}
							onchange={(e) => (selectedItemId = e.currentTarget.value || null)}
						>
							<option value="">Select an item...</option>
							{#each inventoryItems.slice(0, 100) as item (item.uniqueId)}
								<option value={item.uniqueId}>{displayNameFor(item)}</option>
							{/each}
						</select>
						{#if inventoryItems.length > 100}
							<span class="text-xs text-muted-foreground mt-1">Showing first 100 items</span>
						{/if}
					</div>

					<button
						type="button"
						class="btn btn-primary w-full"
						disabled={!selectedItemId}
						onclick={confirmAdd}
					>
						Add Widget
					</button>
				</div>
			{:else}
				<!-- Direct add for other types -->
				<div class="space-y-4">
					<button
						type="button"
						class="text-sm text-muted-foreground hover:text-foreground flex items-center gap-1"
						onclick={() => (selectedWidgetType = null)}
					>
						<svg
							class="w-4 h-4"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
						>
							<path
								stroke-linecap="round"
								stroke-linejoin="round"
								d="M15.75 19.5L8.25 12l7.5-7.5"
							/>
						</svg>
						Back
					</button>

					<p class="text-sm text-muted-foreground">
						Click "Add Widget" to add a {widgetTypes.find((w) => w.type === selectedWidgetType)
							?.title} widget.
					</p>

					<button type="button" class="btn btn-primary w-full" onclick={confirmAdd}>
						Add Widget
					</button>
				</div>
			{/if}

			<div class="modal-action">
				<button type="button" class="btn btn-ghost" onclick={handleClose}>Cancel</button>
			</div>
		</div>
		<form method="dialog" class="modal-backdrop">
			<button type="button" onclick={handleClose}>close</button>
		</form>
	</dialog>
{/if}
