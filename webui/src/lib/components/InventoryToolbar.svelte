<script lang="ts">
	import type { InventoryFilter } from '$lib/api/inventory';
	import Badge from '$lib/components/Badge.svelte';

	export type SortMode = 'amount' | 'name';

	export let filter: InventoryFilter = 'all';
	export let sort: SortMode = 'amount';
	export let searchValue: string = '';
	export let onFilterChange: (filter: InventoryFilter) => void;
	export let onSortChange: (sort: SortMode) => void;
	export let onSearchInput: () => void;
	export let onSearchKeyUp: (e: KeyboardEvent) => void;
	export let onClearSearch: () => void;
</script>

<!-- Panel Header -->
<div class="flex items-center justify-between px-4 py-3 border-b border-border bg-muted/30">
	<div class="flex items-center gap-3">
		<h2 class="font-bold text-xs uppercase tracking-widest text-muted-foreground">
			Network Storage
		</h2>
		{#if filter !== 'all'}
			<Badge label={filter} className="hidden sm:inline-flex" />
		{/if}
	</div>

	<!-- Sort/Filter Controls -->
	<div class="flex items-center gap-2">
		<div class="join shadow-sm border border-border rounded-lg bg-card">
			<button
				class="join-item btn btn-xs btn-ghost hover:bg-muted font-sans font-normal {sort ===
				'amount'
					? 'bg-muted text-foreground font-medium'
					: 'text-muted-foreground'}"
				on:click={() => onSortChange('amount')}
			>
				Amount
			</button>
			<button
				class="join-item btn btn-xs btn-ghost hover:bg-muted font-sans font-normal {sort === 'name'
					? 'bg-muted text-foreground font-medium'
					: 'text-muted-foreground'}"
				on:click={() => onSortChange('name')}
			>
				Name
			</button>
		</div>
		<div class="w-px h-4 bg-border mx-1"></div>
		<div class="join shadow-sm border border-border rounded-lg bg-card">
			<button
				class="join-item btn btn-xs btn-ghost hover:bg-muted font-sans font-normal {filter ===
				'all'
					? 'bg-muted text-foreground font-medium'
					: 'text-muted-foreground'}"
				on:click={() => onFilterChange('all')}
			>
				All
			</button>
			<button
				class="join-item btn btn-xs btn-ghost hover:bg-muted font-sans font-normal {filter ===
				'items'
					? 'bg-muted text-foreground font-medium'
					: 'text-muted-foreground'}"
				on:click={() => onFilterChange('items')}
			>
				Items
			</button>
			<button
				class="join-item btn btn-xs btn-ghost hover:bg-muted font-sans font-normal {filter ===
				'fluids'
					? 'bg-muted text-foreground font-medium'
					: 'text-muted-foreground'}"
				on:click={() => onFilterChange('fluids')}
			>
				Fluids
			</button>
		</div>
	</div>
</div>

<!-- Search -->
<div class="px-4 py-3 border-b border-border bg-card">
	<div class="relative w-full">
		<div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
			<svg
				class="h-4 w-4 text-muted-foreground/60"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="2"
			>
				<circle cx="11" cy="11" r="8"></circle>
				<line x1="21" y1="21" x2="16.65" y2="16.65"></line>
			</svg>
		</div>
		<input
			type="text"
			class="input input-sm w-full pl-10 pr-10 bg-muted/30 border-border focus:border-accent focus:ring-1 focus:ring-accent font-mono text-sm transition-all rounded-lg"
			placeholder="Search system..."
			bind:value={searchValue}
			on:keyup={onSearchKeyUp}
			on:input={onSearchInput}
		/>
		{#if searchValue}
			<button
				class="absolute inset-y-0 right-0 pr-3 flex items-center cursor-pointer text-muted-foreground hover:text-foreground"
				aria-label="Clear search"
				on:click={onClearSearch}
			>
				<svg
					class="h-4 w-4"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
				>
					<line x1="18" y1="6" x2="6" y2="18"></line>
					<line x1="6" y1="6" x2="18" y2="18"></line>
				</svg>
			</button>
		{/if}
	</div>
</div>
