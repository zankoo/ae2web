<script lang="ts">
	import type {
		WidgetConfig,
		WidgetType,
		InventoryItem,
		NetworkOverviewOptions,
		TopItemsOptions,
		TrackedItemsOptions,
		HistoryTrendOptions
	} from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';

	interface Props {
		open: boolean;
		widget: WidgetConfig | null;
		inventoryItems: InventoryItem[];
		onSave: (widget: WidgetConfig) => void;
		onClose: () => void;
	}

	let { open, widget, inventoryItems, onSave, onClose }: Props = $props();

	// Local state for editing
	let title = $state('');
	let networkOverviewOpts = $state<NetworkOverviewOptions>({
		showProduction: true,
		showConsumption: true,
		showNetChange: true
	});
	let topItemsOpts = $state<TopItemsOptions>({
		mode: 'production',
		count: 5,
		filter: 'all'
	});
	let trackedItemsOpts = $state<TrackedItemsOptions>({
		trackedIds: []
	});
	let historyTrendOpts = $state<HistoryTrendOptions>({
		itemId: '',
		timeRange: '1h'
	});

	// Item search for tracked items
	let itemSearch = $state('');
	let showItemDropdown = $state(false);

	const titleInputId = 'edit-widget-title';
	const topItemsCountId = 'edit-widget-top-items-count';
	const topItemsFilterId = 'edit-widget-top-items-filter';
	const itemSearchId = 'edit-widget-item-search';
	const historyTimeRangeId = 'edit-widget-history-time-range';

	// Reset state when widget changes
	$effect(() => {
		if (widget) {
			title = widget.title ?? getDefaultTitle(widget.type);

			switch (widget.type) {
				case 'network-overview':
					networkOverviewOpts = { ...(widget.options as NetworkOverviewOptions) };
					break;
				case 'top-items':
					topItemsOpts = { ...(widget.options as TopItemsOptions) };
					break;
				case 'tracked-items':
					trackedItemsOpts = { ...(widget.options as TrackedItemsOptions) };
					break;
				case 'history-trend':
					historyTrendOpts = { ...(widget.options as HistoryTrendOptions) };
					break;
			}
		}
	});

	function getDefaultTitle(type: WidgetType): string {
		switch (type) {
			case 'network-overview':
				return 'Network Overview';
			case 'top-items':
				return 'Top Items';
			case 'tracked-items':
				return 'Tracked Items';
			case 'history-trend':
				return 'History Trend';
		}
	}

	function handleSave(): void {
		if (!widget) return;

		let options: WidgetConfig['options'];
		switch (widget.type) {
			case 'network-overview':
				options = { ...networkOverviewOpts };
				break;
			case 'top-items':
				options = { ...topItemsOpts };
				break;
			case 'tracked-items':
				options = { ...trackedItemsOpts };
				break;
			case 'history-trend':
				options = { ...historyTrendOpts };
				break;
		}

		onSave({
			...widget,
			title,
			options
		});
		handleClose();
	}

	function handleClose(): void {
		itemSearch = '';
		showItemDropdown = false;
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

	// Filtered items for dropdown
	let filteredItems = $derived.by(() => {
		if (!itemSearch.trim()) {
			return inventoryItems.slice(0, 50);
		}
		const query = itemSearch.toLowerCase();
		return inventoryItems
			.filter((item) => displayNameFor(item).toLowerCase().includes(query))
			.slice(0, 50);
	});

	// Get tracked items with their data
	let trackedItemsDisplay = $derived.by(() => {
		const itemMap = new Map(inventoryItems.map((item) => [item.uniqueId, item]));
		return trackedItemsOpts.trackedIds
			.map((id) => itemMap.get(id))
			.filter((item): item is InventoryItem => item !== undefined);
	});

	// Get selected item for history trend
	let selectedHistoryItem = $derived.by(() => {
		return inventoryItems.find((item) => item.uniqueId === historyTrendOpts.itemId) ?? null;
	});

	function addTrackedItem(item: InventoryItem): void {
		if (!trackedItemsOpts.trackedIds.includes(item.uniqueId)) {
			trackedItemsOpts.trackedIds = [...trackedItemsOpts.trackedIds, item.uniqueId];
		}
		itemSearch = '';
		showItemDropdown = false;
	}

	function removeTrackedItem(uniqueId: string): void {
		trackedItemsOpts.trackedIds = trackedItemsOpts.trackedIds.filter((id) => id !== uniqueId);
	}

	function selectHistoryItem(item: InventoryItem): void {
		historyTrendOpts.itemId = item.uniqueId;
		itemSearch = '';
		showItemDropdown = false;
	}
</script>

{#if open && widget}
	<dialog class="modal modal-open" onkeydown={(e) => e.key === 'Escape' && handleClose()}>
		<div class="modal-box max-w-lg">
			<h3 class="font-bold text-lg mb-4">Edit Widget</h3>

			<!-- Common: Title -->
			<div class="form-control mb-4">
				<label class="label" for={titleInputId}>
					<span class="label-text">Title</span>
				</label>
				<input
					id={titleInputId}
					type="text"
					class="input input-bordered w-full"
					bind:value={title}
					placeholder={getDefaultTitle(widget.type)}
				/>
			</div>

			<!-- Widget-specific options -->
			{#if widget.type === 'network-overview'}
				<div class="divider text-xs text-muted-foreground">Display Options</div>
				<div class="form-control">
					<label class="label cursor-pointer">
						<span class="label-text">Show Production</span>
						<input
							type="checkbox"
							class="toggle toggle-primary"
							bind:checked={networkOverviewOpts.showProduction}
						/>
					</label>
					<label class="label cursor-pointer">
						<span class="label-text">Show Consumption</span>
						<input
							type="checkbox"
							class="toggle toggle-primary"
							bind:checked={networkOverviewOpts.showConsumption}
						/>
					</label>
					<label class="label cursor-pointer">
						<span class="label-text">Show Net Change</span>
						<input
							type="checkbox"
							class="toggle toggle-primary"
							bind:checked={networkOverviewOpts.showNetChange}
						/>
					</label>
				</div>
			{:else if widget.type === 'top-items'}
				<div class="divider text-xs text-muted-foreground">Display Options</div>

				<div class="form-control mb-3">
					<div class="label">
						<span class="label-text">Mode</span>
					</div>
					<div class="flex gap-2">
						<label class="label cursor-pointer flex-1 justify-start gap-2">
							<input
								type="radio"
								name="top-items-mode"
								class="radio radio-primary"
								checked={topItemsOpts.mode === 'production'}
								onchange={() => (topItemsOpts.mode = 'production')}
							/>
							<span class="label-text">Production</span>
						</label>
						<label class="label cursor-pointer flex-1 justify-start gap-2">
							<input
								type="radio"
								name="top-items-mode"
								class="radio radio-primary"
								checked={topItemsOpts.mode === 'consumption'}
								onchange={() => (topItemsOpts.mode = 'consumption')}
							/>
							<span class="label-text">Consumption</span>
						</label>
					</div>
				</div>

				<div class="form-control mb-3">
					<label class="label" for={topItemsCountId}>
						<span class="label-text">Number of items</span>
					</label>
					<input
						id={topItemsCountId}
						type="number"
						class="input input-bordered w-full"
						bind:value={topItemsOpts.count}
						min="1"
						max="20"
					/>
				</div>

				<div class="form-control">
					<label class="label" for={topItemsFilterId}>
						<span class="label-text">Filter</span>
					</label>
					<select
						id={topItemsFilterId}
						class="select select-bordered w-full"
						bind:value={topItemsOpts.filter}
					>
						<option value="all">All</option>
						<option value="items">Items Only</option>
						<option value="fluids">Fluids Only</option>
					</select>
				</div>
			{:else if widget.type === 'tracked-items'}
				<div class="divider text-xs text-muted-foreground">Tracked Items</div>

				<!-- Current tracked items -->
				{#if trackedItemsDisplay.length > 0}
					<div class="mb-3 space-y-1">
						{#each trackedItemsDisplay as item (item.uniqueId)}
							<div class="flex items-center gap-2 p-2 bg-base-200 rounded-lg">
								<IconImage iconKey={item.iconKey} alt={displayNameFor(item)} className="w-6 h-6" />
								<span class="flex-1 text-sm truncate">{displayNameFor(item)}</span>
								<button
									type="button"
									class="btn btn-ghost btn-xs btn-circle"
									aria-label={`Remove ${displayNameFor(item)}`}
									onclick={() => removeTrackedItem(item.uniqueId)}
								>
									<svg
										class="w-4 h-4"
										viewBox="0 0 24 24"
										fill="none"
										stroke="currentColor"
										stroke-width="2"
									>
										<path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
									</svg>
								</button>
							</div>
						{/each}
					</div>
				{/if}

				<!-- Add item dropdown -->
				<div class="form-control relative">
					<label class="label" for={itemSearchId}>
						<span class="label-text">Add item</span>
					</label>
					<input
						id={itemSearchId}
						type="text"
						class="input input-bordered w-full"
						placeholder="Search items..."
						bind:value={itemSearch}
						onfocus={() => (showItemDropdown = true)}
					/>

					{#if showItemDropdown && filteredItems.length > 0}
						<div
							class="absolute top-full left-0 right-0 mt-1 bg-base-200 rounded-lg shadow-lg max-h-48 overflow-y-auto z-50"
						>
							{#each filteredItems as item (item.uniqueId)}
								<button
									type="button"
									class="flex items-center gap-2 w-full p-2 hover:bg-base-300 text-left"
									onclick={() => addTrackedItem(item)}
								>
									<IconImage
										iconKey={item.iconKey}
										alt={displayNameFor(item)}
										className="w-6 h-6"
									/>
									<span class="text-sm truncate">{displayNameFor(item)}</span>
								</button>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Click outside to close dropdown -->
				{#if showItemDropdown}
					<!-- svelte-ignore a11y_click_events_have_key_events -->
					<!-- svelte-ignore a11y_no_static_element_interactions -->
					<div class="fixed inset-0 z-40" onclick={() => (showItemDropdown = false)}></div>
				{/if}
			{:else if widget.type === 'history-trend'}
				<div class="divider text-xs text-muted-foreground">Chart Options</div>

				<!-- Current selected item -->
				{#if selectedHistoryItem}
					<div class="mb-3">
						<div class="label">
							<span class="label-text">Current item</span>
						</div>
						<div class="flex items-center gap-2 p-2 bg-base-200 rounded-lg">
							<IconImage
								iconKey={selectedHistoryItem.iconKey}
								alt={displayNameFor(selectedHistoryItem)}
								className="w-6 h-6"
							/>
							<span class="text-sm truncate">{displayNameFor(selectedHistoryItem)}</span>
						</div>
					</div>
				{/if}

				<!-- Item selection -->
				<div class="form-control relative mb-3">
					<label class="label" for={itemSearchId}>
						<span class="label-text">{selectedHistoryItem ? 'Change item' : 'Select item'}</span>
					</label>
					<input
						id={itemSearchId}
						type="text"
						class="input input-bordered w-full"
						placeholder="Search items..."
						bind:value={itemSearch}
						onfocus={() => (showItemDropdown = true)}
					/>

					{#if showItemDropdown && filteredItems.length > 0}
						<div
							class="absolute top-full left-0 right-0 mt-1 bg-base-200 rounded-lg shadow-lg max-h-48 overflow-y-auto z-50"
						>
							{#each filteredItems as item (item.uniqueId)}
								<button
									type="button"
									class="flex items-center gap-2 w-full p-2 hover:bg-base-300 text-left"
									onclick={() => selectHistoryItem(item)}
								>
									<IconImage
										iconKey={item.iconKey}
										alt={displayNameFor(item)}
										className="w-6 h-6"
									/>
									<span class="text-sm truncate">{displayNameFor(item)}</span>
								</button>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Click outside to close dropdown -->
				{#if showItemDropdown}
					<!-- svelte-ignore a11y_click_events_have_key_events -->
					<!-- svelte-ignore a11y_no_static_element_interactions -->
					<div class="fixed inset-0 z-40" onclick={() => (showItemDropdown = false)}></div>
				{/if}

				<!-- Time range -->
				<div class="form-control">
					<label class="label" for={historyTimeRangeId}>
						<span class="label-text">Time Range</span>
					</label>
					<select
						id={historyTimeRangeId}
						class="select select-bordered w-full"
						bind:value={historyTrendOpts.timeRange}
					>
						<option value="1h">Last Hour</option>
						<option value="6h">Last 6 Hours</option>
						<option value="24h">Last 24 Hours</option>
						<option value="7d">Last 7 Days</option>
					</select>
				</div>
			{/if}

			<div class="modal-action">
				<button type="button" class="btn btn-ghost" onclick={handleClose}>Cancel</button>
				<button type="button" class="btn btn-primary" onclick={handleSave}>Save</button>
			</div>
		</div>
		<form method="dialog" class="modal-backdrop">
			<button type="button" onclick={handleClose}>close</button>
		</form>
	</dialog>
{/if}
