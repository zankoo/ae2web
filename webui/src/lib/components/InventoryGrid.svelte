<script lang="ts">
	import type { InventoryItem } from '$lib/api/types';
	import type { InventoryRenderNode } from '$lib/utils/inventoryGrouping';
	import IconImage from '$lib/components/IconImage.svelte';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';

	export let renderNodes: InventoryRenderNode[] = [];
	export let onItemClick: (item: InventoryItem) => void;
	export let onToggleSection: (key: string) => void;
	export let onMouseOver: (e: MouseEvent) => void;
	export let onMouseMove: (e: MouseEvent) => void;
	export let onMouseOut: (e: MouseEvent) => void;
	export let onScroll: () => void;

	let scrollEl: HTMLDivElement | null = null;
	let gridEl: HTMLDivElement | null = null;

	export function getScrollElement(): HTMLDivElement | null {
		return scrollEl;
	}

	export function getGridElement(): HTMLDivElement | null {
		return gridEl;
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

	function handleItemKeydown(e: KeyboardEvent, item: InventoryItem): void {
		if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			if (item.craftable) onItemClick(item);
		}
	}
</script>

<div
	class="flex-1 overflow-y-auto p-4 bg-muted/5 texture-dots-dark"
	style="scrollbar-gutter: stable;"
	bind:this={scrollEl}
	on:scroll={onScroll}
>
	<!-- svelte-ignore a11y_mouse_events_have_key_events -->
	<div
		class="grid gap-2"
		style="grid-template-columns: repeat(auto-fill, minmax(64px, 1fr));"
		role="grid"
		tabindex="-1"
		bind:this={gridEl}
		on:mouseover={onMouseOver}
		on:mousemove={onMouseMove}
		on:mouseout={onMouseOut}
	>
		{#if renderNodes.length === 0}
			<div
				class="col-span-full flex flex-col items-center justify-center h-48 text-muted-foreground/50 gap-2"
			>
				<svg
					class="w-10 h-10 opacity-30"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="1.5"
					><path d="M20 12V8H6a2 2 0 0 1-2-2c0-1.1.9-2 2-2h12v4"></path><path
						d="M4 6v12c0 1.1.9 2 2 2h14v-4"
					></path><path d="M18 12a2 2 0 0 0-2 2c0 1.1.9 2 2 2h2v-4Z"></path></svg
				>
				<span class="text-sm font-medium">No items found</span>
			</div>
		{:else}
			{#each renderNodes as node (node.kind === 'header' ? `h:${node.key}` : node.key)}
				{#if node.kind === 'header'}
					<div
						class="inventory-group-header my-2"
						data-section-key={node.key}
						style="grid-column: 1 / -1;"
					>
						<div
							class="flex items-center gap-2 p-2 rounded-lg bg-muted/60 border border-border/50 text-xs uppercase font-bold tracking-wider text-muted-foreground w-full"
						>
							<button
								class="w-5 h-5 flex items-center justify-center rounded hover:bg-muted text-foreground"
								aria-label="{node.collapsed ? 'Expand' : 'Collapse'} {node.title}"
								on:click={() => onToggleSection(node.key)}
							>
								{node.collapsed ? '+' : '−'}
							</button>
							<span class="inventory-group-title text-foreground">{node.title}</span>
							<span class="ml-auto font-mono text-muted-foreground">{node.count}</span>
						</div>
					</div>
				{:else}
					{@const item = node.item}
					<div
						class="item-slot {item.type === 'fluid' ? 'fluid' : ''} {item.craftable
							? 'craftable'
							: ''}"
						data-item-id={item.uniqueId}
						data-icon-id={item.iconKey}
						data-item-type={item.type}
						role="button"
						tabindex="-1"
						on:click={() => item.craftable && onItemClick(item)}
						on:keydown={(e) => handleItemKeydown(e, item)}
					>
						<IconImage iconKey={item.iconKey} alt={displayNameFor(item)} className="item-icon" />
						<span class="item-name">{displayNameFor(item)}</span>
						<span class="item-amount">
							{item.type === 'fluid'
								? formatFluidAmount(Number(item.amount))
								: formatAmount(Number(item.amount))}
						</span>
					</div>
				{/if}
			{/each}
		{/if}
	</div>
</div>
