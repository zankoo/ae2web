<script lang="ts">
	import type { CpuDto, InventoryItem } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import Button from '$lib/components/Button.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';

	export let open: boolean = false;
	export let item: InventoryItem | null = null;
	export let cpus: CpuDto[] = [];
	export let onCraft: (item: InventoryItem, amount: number, cpuName: string) => void;
	export let onClose: () => void;

	let amount = 1;
	let cpuName = '';

	function displayName(item: InventoryItem | null): string {
		if (!item) return 'Item Name';
		const name = item.name;
		if (typeof name === 'string' && name.trim()) {
			return name;
		}
		if (item.id.includes(':')) {
			return item.id.split(':', 2)[1] || item.id;
		}
		return item.id;
	}

	function handleAmountInput(e: Event): void {
		const value = (e.target as HTMLInputElement | null)?.valueAsNumber;
		if (typeof value === 'number' && Number.isFinite(value)) {
			amount = value;
		}
	}

	function handleCraft(): void {
		if (item) {
			onCraft(item, amount, cpuName);
		}
	}

	// Reset amount when item changes
	$: if (item) {
		amount = 1;
		cpuName = '';
	}
</script>

<dialog class="modal" class:modal-open={open}>
	<div class="modal-box max-w-md p-0 bg-card border border-border/50 shadow-2xl rounded-2xl overflow-visible">
		<div class="flex items-center gap-3 px-6 py-4 border-b border-border bg-muted/40 rounded-t-2xl">
			<div class="w-10 h-10 rounded-xl bg-accent-secondary/10 flex items-center justify-center text-accent-secondary">
				<svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="m15 12-8.5 8.5c-.83.83-2.17.83-3 0a2.12 2.12 0 0 1 0-3L12 9M17.64 15 22 10.64" />
					<path d="m20.91 11.7-1.25-1.25c-.6-.6-.93-1.4-.93-2.25v-.86L16.01 4.6a5.56 5.56 0 0 0-3.94-1.64H9l.92.82A6.18 6.18 0 0 1 12 8.4v1.56l2 2h2.47l2.26 1.91" />
				</svg>
			</div>
			<h3 class="font-bold text-lg flex-1">Crafting Request</h3>
			<Button variant="ghost" size="sm" className="btn-circle" onClick={onClose}>
				<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</Button>
		</div>

		<div class="p-6 space-y-6">
			<!-- Item Display -->
			<div class="flex items-center gap-4 p-4 bg-muted/20 border border-border rounded-xl">
				<div class="w-12 h-12 bg-card rounded-lg p-1 border border-border/50">
					{#if item?.iconKey}
						<IconImage iconKey={item.iconKey} alt={displayName(item)} className="w-full h-full" lazy={false} />
					{:else}
						<img src={PLACEHOLDER_ICON} alt="" class="w-full h-full opacity-50" />
					{/if}
				</div>
				<div class="flex-1 min-w-0 overflow-hidden">
					<div class="font-bold text-lg truncate">{displayName(item)}</div>
					<div class="text-sm text-muted-foreground font-mono truncate" title={item?.id ?? ''}>
						{item?.id ?? 'minecraft:item'}
						{item?.type === 'fluid' ? ' [Fluid]' : ''}
					</div>
				</div>
			</div>

			<!-- Quantity -->
			<div>
				<label class="label" for="craft-quantity">
					<span class="label-text font-bold text-sm uppercase tracking-wider text-muted-foreground">Quantity</span>
				</label>
				<input
					id="craft-quantity"
					type="number"
					class="input input-lg input-bordered w-full font-mono text-2xl font-bold bg-muted/10 border-border focus:border-accent"
					min="1"
					max={item?.type === 'fluid' ? 1000 : 64000}
					value={amount}
					on:input={handleAmountInput}
				/>
				<div class="flex gap-2 mt-3">
					{#each [1, 16, 64, 256] as preset}
						<button
							class="flex-1 btn btn-sm btn-ghost border border-border hover:bg-accent/10 hover:border-accent hover:text-accent font-mono transition-all"
							on:click={() => (amount = preset)}
						>
							{preset}
						</button>
					{/each}
				</div>
			</div>

			<!-- CPU Select -->
			<div>
				<label class="label" for="craft-cpu">
					<span class="label-text font-bold text-sm uppercase tracking-wider text-muted-foreground">Target CPU</span>
				</label>
				<select id="craft-cpu" class="select select-bordered w-full bg-card focus:border-accent" bind:value={cpuName}>
					<option value="">Auto (Any Available)</option>
					{#each cpus as cpu, i (cpu.cpuId || (cpu.name ?? `cpu-${i}`))}
						{@const name = cpu.name || `CPU ${i + 1}`}
						{@const status = cpu.busy ? ' (Busy)' : ''}
						<option value={cpu.name ?? ''}>{name}{status}</option>
					{/each}
				</select>
			</div>
		</div>

		<div class="flex justify-end gap-3 px-6 py-4 border-t border-border bg-muted/20 rounded-b-2xl">
			<Button variant="ghost" onClick={onClose}>Cancel</Button>
			<Button variant="primary" onClick={handleCraft}>
				Create Job
				<svg class="w-4 h-4 ml-2" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<polyline points="20 6 9 17 4 12" />
				</svg>
			</Button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop bg-black/80 backdrop-blur-sm">
		<button on:click={onClose}>close</button>
	</form>
</dialog>
