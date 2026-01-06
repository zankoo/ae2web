<script lang="ts">
	import type { CpuDto, GenericStackDto, InventoryItem } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';

	let {
		open = false,
		item = null,
		cpus = [],
		missingItems = [],
		onCraft,
		onRetry,
		onClose
	}: {
		open?: boolean;
		item?: InventoryItem | null;
		cpus?: CpuDto[];
		missingItems?: GenericStackDto[];
		onCraft: (item: InventoryItem, amount: number, cpuName: string) => void;
		onRetry: () => void;
		onClose: () => void;
	} = $props();

	let amount = $state(1);
	let cpuName = $state('');
	let missingFilter = $state('');

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

	function formatMissingAmount(missing: GenericStackDto): string {
		return missing.type === 'fluid'
			? formatFluidAmount(Number(missing.amount ?? 0))
			: `x${formatAmount(Number(missing.amount ?? 0))}`;
	}

	let isMissing = $derived(missingItems.length > 0);
	let missingFilterTerm = $derived(missingFilter.trim().toLowerCase());
	let missingFiltered = $derived(
		missingFilterTerm
			? missingItems.filter((m) => {
					const name = String(m?.name ?? '').toLowerCase();
					const id = String(m?.id ?? '').toLowerCase();
					return name.includes(missingFilterTerm) || id.includes(missingFilterTerm);
				})
			: missingItems
	);
	let missingSorted = $derived(
		[...missingFiltered].sort((a, b) => Number(b?.amount ?? 0) - Number(a?.amount ?? 0))
	);

	// Reset amount when item changes
	let lastItem: InventoryItem | null = null;
	$effect(() => {
		if (item && item !== lastItem) {
			amount = 1;
			cpuName = '';
			missingFilter = '';
			lastItem = item;
		}
	});
</script>

<dialog class="modal backdrop-blur-sm" class:modal-open={open}>
	<div
		class="modal-box p-0 bg-card border border-border/50 shadow-2xl rounded-2xl overflow-hidden flex flex-col max-h-[85vh] {isMissing
			? 'max-w-2xl'
			: 'max-w-md'}"
	>
		<!-- Header -->
		<div class="flex items-center gap-3 px-6 py-4 border-b border-border bg-muted/40 rounded-t-2xl">
			<div
				class="w-10 h-10 rounded-xl flex items-center justify-center {missingItems.length > 0
					? 'bg-warning/10 text-warning'
					: 'bg-accent-secondary/10 text-accent-secondary'}"
			>
				{#if missingItems.length > 0}
					<svg
						class="w-5 h-5"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
						<path d="M12 9v4M12 17h.01" />
					</svg>
				{:else}
					<svg
						class="w-5 h-5"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path
							d="m15 12-8.5 8.5c-.83.83-2.17.83-3 0a2.12 2.12 0 0 1 0-3L12 9M17.64 15 22 10.64"
						/>
						<path
							d="m20.91 11.7-1.25-1.25c-.6-.6-.93-1.4-.93-2.25v-.86L16.01 4.6a5.56 5.56 0 0 0-3.94-1.64H9l.92.82A6.18 6.18 0 0 1 12 8.4v1.56l2 2h2.47l2.26 1.91"
						/>
					</svg>
				{/if}
			</div>
			<h3 class="font-bold text-lg flex-1">
				{missingItems.length > 0 ? 'Missing Items' : 'Crafting Request'}
			</h3>
			<button
				type="button"
				class="btn btn-ghost btn-sm btn-circle"
				aria-label="Close"
				onclick={onClose}
			>
				<svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</button>
		</div>

		<div class="p-6 flex-1 min-h-0 overflow-auto">
			<!-- Target Item Display (always visible) -->
			<div class="flex items-center gap-4 p-4 bg-muted/20 border border-border rounded-xl">
				<div class="w-12 h-12 bg-card rounded-lg p-1 border border-border/50">
					{#if item?.iconKey}
						<IconImage
							iconKey={item.iconKey}
							alt={displayName(item)}
							className="w-full h-full"
							lazy={false}
						/>
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
				{#if missingItems.length > 0}
					<div class="badge badge-lg font-mono">x{amount}</div>
				{/if}
			</div>

			{#if missingItems.length === 0}
				<!-- Input State: Quantity & CPU Selection -->
				<div class="space-y-6">
					<div>
						<label class="label" for="craft-quantity">
							<span
								class="label-text font-bold text-sm uppercase tracking-wider text-muted-foreground"
								>Quantity</span
							>
						</label>
						<input
							id="craft-quantity"
							type="number"
							class="input input-lg input-bordered w-full font-mono text-2xl font-bold bg-muted/10 border-border focus:border-accent"
							min="1"
							max={item?.type === 'fluid' ? 1000 : 64000}
							value={amount}
							oninput={handleAmountInput}
						/>
						<div class="flex gap-2 mt-3">
							{#each [1, 16, 64, 256] as preset (preset)}
								<button
									class="flex-1 btn btn-sm btn-ghost border border-border hover:bg-accent/10 hover:border-accent hover:text-accent font-mono transition-all"
									onclick={() => (amount = preset)}
								>
									{preset}
								</button>
							{/each}
						</div>
					</div>

					<div>
						<label class="label" for="craft-cpu">
							<span
								class="label-text font-bold text-sm uppercase tracking-wider text-muted-foreground"
								>Target CPU</span
							>
						</label>
						<select
							id="craft-cpu"
							class="select select-bordered w-full bg-card focus:border-accent"
							bind:value={cpuName}
						>
							<option value="">Auto (Any Available)</option>
							{#each cpus as cpu, i (cpu.cpuId || (cpu.name ?? `cpu-${i}`))}
								{@const name = cpu.name || `CPU ${i + 1}`}
								{@const status = cpu.busy ? ' (Busy)' : ''}
								<option value={cpu.name ?? ''}>{name}{status}</option>
							{/each}
						</select>
					</div>
				</div>
			{:else}
				<!-- Missing Items State -->
				<div class="mt-6 flex flex-col gap-3 min-h-0">
					<div class="alert alert-warning">
						<svg
							class="w-5 h-5"
							viewBox="0 0 24 24"
							fill="none"
							stroke="currentColor"
							stroke-width="2"
						>
							<circle cx="12" cy="12" r="10" />
							<path d="M12 8v4M12 16h.01" />
						</svg>
						<span
							>Cannot craft: Missing {missingItems.length} item type{missingItems.length > 1
								? 's'
								: ''}</span
						>
					</div>

					<div class="flex items-center gap-2">
						<input
							type="text"
							class="input input-sm input-bordered flex-1 bg-card border-border font-mono"
							placeholder="Filter missing…"
							aria-label="Filter missing items"
							bind:value={missingFilter}
						/>
						<span class="badge badge-sm font-mono"
							>{missingFiltered.length}/{missingItems.length}</span
						>
					</div>

					<div class="flex-1 min-h-0 overflow-auto border border-border rounded-xl bg-muted/10">
						{#if missingSorted.length === 0}
							<div class="p-6 text-center text-muted-foreground font-mono">No matches.</div>
						{:else}
							<div class="grid grid-cols-1 sm:grid-cols-2 gap-2 p-2">
								{#each missingSorted as missing (missing.id)}
									<div
										class="flex items-center gap-3 p-2.5 rounded-lg border border-border bg-card hover:bg-muted/20"
									>
										<div
											class="w-10 h-10 bg-card rounded-lg p-1 border border-border/50 flex-shrink-0"
										>
											{#if missing.iconKey}
												<IconImage
													iconKey={missing.iconKey}
													alt={missing.name}
													className="w-full h-full"
													lazy={true}
												/>
											{:else}
												<img src={PLACEHOLDER_ICON} alt="" class="w-full h-full opacity-50" />
											{/if}
										</div>
										<div class="flex-1 min-w-0">
											<div class="font-medium truncate">{missing.name}</div>
											<div class="text-xs text-muted-foreground font-mono truncate">
												{missing.id}
											</div>
										</div>
										<div class="flex-shrink-0">
											<span class="badge badge-error badge-sm font-mono whitespace-nowrap"
												>{formatMissingAmount(missing)}</span
											>
										</div>
									</div>
								{/each}
							</div>
						{/if}
					</div>
				</div>
			{/if}
		</div>

		<!-- Footer -->
		<div class="flex justify-end gap-3 px-6 py-4 border-t border-border bg-muted/20 rounded-b-2xl">
			{#if missingItems.length === 0}
				<button type="button" class="btn btn-ghost" onclick={onClose}>Cancel</button>
				<button type="button" class="btn btn-primary" onclick={handleCraft}>
					Create Job
					<svg
						class="w-4 h-4 ml-2"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<polyline points="20 6 9 17 4 12" />
					</svg>
				</button>
			{:else}
				<button type="button" class="btn btn-ghost" onclick={onClose}>Close</button>
				<button type="button" class="btn btn-primary" onclick={onRetry}>
					<svg
						class="w-4 h-4 mr-2"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="m15 18-6-6 6-6" />
					</svg>
					Try Different Amount
				</button>
			{/if}
		</div>
	</div>
	<form method="dialog" class="modal-backdrop bg-black/80">
		<button onclick={onClose}>close</button>
	</form>
</dialog>
