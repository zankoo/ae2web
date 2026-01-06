<script lang="ts">
	import type { CpuDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount } from '$lib/utils/format';

	let {
		cpus = [],
		onCpuClick,
		onRefresh,
		onSearch,
		onScrollToTop,
		onClearSearch
	}: {
		cpus?: CpuDto[];
		onCpuClick: (cpu: CpuDto) => void;
		onRefresh: () => void;
		onSearch: () => void;
		onScrollToTop: () => void;
		onClearSearch: () => void;
	} = $props();

	let cpuCount = $derived(cpus.length);
</script>

<div class="w-80 flex flex-col gap-6 flex-shrink-0 hidden lg:flex">
	<!-- Quick Actions -->
	<div class="card bg-card border border-border rounded-xl shadow-sm">
		<div class="card-body p-6">
			<div class="flex items-center gap-2 mb-4">
				<svg
					class="w-4 h-4 text-accent"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					><circle cx="12" cy="12" r="10"></circle><polygon points="10 8 16 12 10 16 10 8"
					></polygon></svg
				>
				<h3 class="font-bold text-xs uppercase tracking-widest text-muted-foreground">
					Quick Actions
				</h3>
			</div>
			<div class="grid grid-cols-2 gap-3">
				<button
					type="button"
					class="btn btn-ghost btn-sm h-auto py-3 flex-col gap-2 border border-border hover:border-accent/30 hover:bg-accent/5 rounded-xl"
					onclick={onRefresh}
				>
					<svg
						class="w-5 h-5 text-foreground"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path
							d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8M3 3v5h5M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16M16 16h5v5"
						/>
					</svg>
					<span class="text-xs font-medium">Refresh</span>
				</button>
				<button
					type="button"
					class="btn btn-ghost btn-sm h-auto py-3 flex-col gap-2 border border-border hover:border-accent/30 hover:bg-accent/5 rounded-xl"
					onclick={onSearch}
				>
					<svg
						class="w-5 h-5 text-foreground"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<circle cx="11" cy="11" r="8" />
						<path d="m21 21-4.35-4.35" />
					</svg>
					<span class="text-xs font-medium">Search</span>
				</button>
				<button
					type="button"
					class="btn btn-ghost btn-sm h-auto py-3 flex-col gap-2 border border-border hover:border-accent/30 hover:bg-accent/5 rounded-xl"
					onclick={onScrollToTop}
				>
					<svg
						class="w-5 h-5 text-foreground"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="M12 5v14M5 12l7-7 7 7" />
					</svg>
					<span class="text-xs font-medium">Top</span>
				</button>
				<button
					type="button"
					class="btn btn-ghost btn-sm h-auto py-3 flex-col gap-2 border border-border hover:border-accent/30 hover:bg-accent/5 rounded-xl"
					onclick={onClearSearch}
				>
					<svg
						class="w-5 h-5 text-foreground"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="M18 6L6 18M6 6l12 12" />
					</svg>
					<span class="text-xs font-medium">Clear</span>
				</button>
			</div>
		</div>
	</div>

	<!-- Crafting CPUs -->
	<div
		class="card bg-card border border-border rounded-xl flex-1 flex flex-col overflow-hidden shadow-sm"
	>
		<div class="px-4 py-3 border-b border-border bg-muted/30 flex items-center justify-between">
			<div class="flex items-center gap-2">
				<svg
					class="w-4 h-4 text-accent"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					><rect x="4" y="4" width="16" height="16" rx="2" /><rect
						x="9"
						y="9"
						width="6"
						height="6"
					/><path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" /></svg
				>
				<h3 class="font-bold text-xs uppercase tracking-widest text-muted-foreground">
					Processors
				</h3>
			</div>
			<span class="badge badge-outline">{cpuCount}</span>
		</div>

		<div class="flex-1 overflow-y-auto p-3 space-y-2 bg-muted/10">
			{#if cpus.length === 0}
				<div
					class="flex flex-col items-center justify-center py-12 text-muted-foreground/50 text-sm"
				>
					<span class="text-xs">No CPUs detected</span>
				</div>
			{:else}
				{#each cpus as cpu, i (cpu.cpuId || (cpu.name ?? `cpu-${i}`))}
					{@const canOpenProcess = cpu.busy && Boolean(cpu.jobStatus) && Boolean(cpu.cpuId)}

					<div class="relative group">
						<button
							class="cpu-card w-full text-left bg-card border border-border rounded-xl p-3 transition-all hover:shadow-md hover:border-accent/50 {canOpenProcess
								? 'cursor-pointer'
								: 'cursor-default'}"
							disabled={!canOpenProcess}
							onclick={() => onCpuClick(cpu)}
						>
							<div class="flex items-center justify-between mb-2">
								<span class="font-bold text-sm text-foreground">{cpu.name || `CPU ${i + 1}`}</span>
								<span class="badge badge-sm font-bold {cpu.busy ? 'badge-warning' : 'badge-ghost'}">
									{cpu.busy ? 'BUSY' : 'IDLE'}
								</span>
							</div>

							{#if cpu.busy && cpu.jobStatus}
								{@const job = cpu.jobStatus}
								{@const crafting = job.crafting}
								{@const outputTotal = job.outputTotal ?? 0}
								{@const outputDone = job.outputDone ?? 0}
								{@const hasOutputProgress = outputTotal > 0}
								{@const progressPct = hasOutputProgress
									? Math.round((outputDone / outputTotal) * 100)
									: job.totalItems > 0
										? Math.round((job.progress / job.totalItems) * 100)
										: 0}

								<div class="flex items-center gap-2 mb-2 p-1.5 bg-muted/30 rounded-lg">
									{#if crafting?.iconKey}
										<IconImage
											iconKey={crafting.iconKey}
											alt={crafting.name}
											className="w-5 h-5"
											lazy={false}
											hideOnError={true}
										/>
									{:else}
										<img class="w-5 h-5 opacity-50" src={PLACEHOLDER_ICON} alt="" />
									{/if}
									<span class="text-xs font-medium truncate flex-1"
										>{crafting?.name ?? 'Unknown'}</span
									>
								</div>

								<div class="flex items-center gap-2">
									<progress
										class="progress progress-primary h-1.5 flex-1"
										value={progressPct}
										max="100"
									></progress>
									<span class="text-[10px] font-mono text-muted-foreground w-12 text-right">
										{progressPct}%
									</span>
								</div>
							{:else}
								<div
									class="flex gap-4 mt-2 text-[10px] text-muted-foreground uppercase tracking-wider font-medium"
								>
									<span
										>Storage: <span class="font-mono text-foreground"
											>{formatAmount(Number(cpu.availableStorage))}</span
										></span
									>
									<span
										>Co-Proc: <span class="font-mono text-foreground">{cpu.coProcessors}</span
										></span
									>
								</div>
							{/if}
						</button>
					</div>
				{/each}
			{/if}
		</div>
	</div>
</div>
