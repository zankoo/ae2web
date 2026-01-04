<script lang="ts">
	import type { CpuProcessEntryDto, CpuProcessResponse, GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import CpuDependencyTree from '$lib/components/CpuProcess/CpuDependencyTree.svelte';
	import CpuProcessTreeRow from '$lib/components/CpuProcess/CpuProcessTreeRow.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount } from '$lib/utils/format';

	export let open: boolean = false;
	export let cpuName: string | null = null;
	export let autoRefresh: boolean = true;
	export let loading: boolean = false;
	export let error: string | null = null;
	export let data: CpuProcessResponse | null = null;
	export let onClose: () => void;
	export let onRefresh: () => void;
	export let onAutoRefreshChange: (value: boolean) => void;

	type CpuProcessView = 'list' | 'statusTree' | 'dependencyTree';
	let view: CpuProcessView = 'list';
	let filter = '';
	let lastOpen = false;

	$: entries = Array.isArray(data?.entries) ? data.entries : [];
	$: filterTerm = filter.trim().toLowerCase();
	$: entriesFiltered = filterTerm
		? entries.filter((e) => {
				const stack = e?.stack;
				const name = String(stack?.name ?? '').toLowerCase();
				const id = String(stack?.id ?? '').toLowerCase();
				return name.includes(filterTerm) || id.includes(filterTerm);
			})
		: entries;

	$: entriesActive = entriesFiltered.filter((e) => Number(e?.active ?? 0) > 0);
	$: entriesWaiting = entriesFiltered.filter((e) => Number(e?.waiting ?? 0) > 0);
	$: entriesStored = entriesFiltered.filter((e) => Number(e?.stored ?? 0) > 0);

	function formatElapsedNanos(elapsedTimeNanos: number | undefined): string {
		const nanos = Number(elapsedTimeNanos);
		if (!Number.isFinite(nanos) || nanos <= 0) return '0:00';

		const totalSeconds = Math.floor(nanos / 1_000_000_000);
		const hours = Math.floor(totalSeconds / 3600);
		const minutes = Math.floor((totalSeconds % 3600) / 60);
		const seconds = totalSeconds % 60;

		const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
		const ss = String(seconds).padStart(2, '0');
		return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
	}

	function handleAutoRefreshChange(e: Event): void {
		const target = e.target as HTMLInputElement;
		onAutoRefreshChange(target.checked);
	}

	// Reset view and filter only when modal first opens (not on data refresh)
	$: if (open && !lastOpen) {
		view = 'list';
		filter = '';
	}
	$: lastOpen = open;
</script>

<dialog class="modal" class:modal-open={open}>
	<div class="modal-box max-w-4xl max-h-[85vh] flex flex-col p-0">
		<!-- Header -->
		<div class="flex items-center gap-3 px-6 py-4 border-b border-base-300">
			<svg
				class="w-6 h-6 text-primary"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="1.5"
			>
				<rect x="4" y="4" width="16" height="16" rx="2" />
				<rect x="9" y="9" width="6" height="6" />
				<path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" />
			</svg>
			<div class="flex-1">
				<h3 class="font-bold">Crafting Process</h3>
				<p class="text-sm text-base-content/60 font-mono">{cpuName || 'Crafting CPU'}</p>
			</div>
			<div class="flex items-center gap-3">
				<label class="flex items-center gap-2 text-sm">
					<input
						type="checkbox"
						class="checkbox checkbox-sm checkbox-primary"
						checked={autoRefresh}
						on:change={handleAutoRefreshChange}
					/>
					<span>Auto-refresh</span>
				</label>
				<button class="btn btn-sm btn-ghost" on:click={onRefresh}>
					{#if loading}
						<span class="loading loading-spinner loading-xs"></span>
					{/if}
					Refresh
				</button>
				<button class="btn btn-sm btn-ghost btn-circle" aria-label="Close" on:click={onClose}>
					<svg
						class="w-4 h-4"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path d="M18 6L6 18M6 6l12 12" />
					</svg>
				</button>
			</div>
		</div>

		<!-- Tabs -->
		<div class="tabs tabs-boxed bg-base-200 mx-4 mt-4">
			<button class="tab" class:tab-active={view === 'list'} on:click={() => (view = 'list')}
				>List</button
			>
			<button
				class="tab"
				class:tab-active={view === 'statusTree'}
				on:click={() => (view = 'statusTree')}>Status Tree</button
			>
			<button
				class="tab"
				class:tab-active={view === 'dependencyTree'}
				on:click={() => (view = 'dependencyTree')}>Dependency Tree</button
			>
		</div>

		<!-- Content -->
		<div class="flex-1 overflow-auto p-4 min-h-[400px]">
			{#if error}
				<div class="alert alert-error mb-4">
					<span>{error}</span>
				</div>
			{/if}

			{#if loading && !data}
				<div class="flex items-center justify-center h-32">
					<span class="loading loading-spinner loading-lg text-primary"></span>
				</div>
			{:else if data}
				{@const summary = data.summary}
				{@const output = summary.finalOutput}
				{@const outputTotal = Number(summary.outputTotal ?? 0)}
				{@const outputDone = Number(summary.outputDone ?? 0)}
				{@const totalItems = Number(summary.totalItems ?? 0)}
				{@const progress = Number(summary.progress ?? 0)}
				{@const hasOutput = outputTotal > 0}
				{@const progressPct = hasOutput
					? Math.round((outputDone / outputTotal) * 100)
					: totalItems > 0
						? Math.round((progress / totalItems) * 100)
						: 0}

				{#if data.busy === false}
					<div class="alert alert-warning mb-4">
						<span>CPU is no longer busy.</span>
					</div>
				{/if}

				<!-- Summary Card -->
				<div class="card bg-base-300 mb-4">
					<div class="card-body p-4 flex-row items-center gap-4">
						<div class="w-12 h-12 bg-base-200 rounded-lg flex items-center justify-center">
							{#if output?.iconKey}
								<IconImage
									iconKey={output.iconKey}
									alt={output.name}
									className="w-8 h-8"
									lazy={false}
									hideOnError={true}
								/>
							{:else}
								<img class="w-8 h-8 opacity-50" src={PLACEHOLDER_ICON} alt="" />
							{/if}
						</div>
						<div class="flex-1">
							<div class="font-bold">{output?.name ?? 'Unknown'}</div>
							<div class="text-xs text-base-content/60 font-mono">{output?.id ?? ''}</div>
							<div class="badge badge-sm mt-1">
								Elapsed: {formatElapsedNanos(summary.elapsedTimeNanos)}
							</div>
						</div>
						<div class="w-48">
							<div class="flex justify-between text-sm mb-1">
								<span class="font-mono text-base-content/60">
									{#if hasOutput}
										{formatAmount(outputDone)} / {formatAmount(outputTotal)}
									{:else}
										{formatAmount(progress)} / {formatAmount(totalItems)}
									{/if}
								</span>
								<span class="text-primary">{progressPct}%</span>
							</div>
							<progress class="progress progress-primary w-full" value={progressPct} max="100"
							></progress>
						</div>
					</div>
				</div>

				<!-- Views -->
				{#if view === 'list'}
					<div class="flex items-center gap-3 mb-3">
						<input
							type="text"
							class="input input-sm input-bordered flex-1 font-mono"
							placeholder="Filter items..."
							bind:value={filter}
						/>
						<span class="text-xs font-bold uppercase tracking-wider text-base-content/60"
							>{entriesFiltered.length} entries</span
						>
					</div>

					<div class="overflow-x-auto rounded-lg border border-base-300">
						<table class="table table-sm">
							<thead class="bg-base-300">
								<tr>
									<th>Item</th>
									<th class="text-right">Stored</th>
									<th class="text-right">Waiting</th>
									<th class="text-right">Active</th>
								</tr>
							</thead>
							<tbody>
								{#each entriesFiltered as row (row.stack.iconKey)}
									{@const stack = row.stack}
									<tr class="hover:bg-base-200">
										<td>
											<div class="flex items-center gap-2">
												{#if stack.iconKey}
													<IconImage
														iconKey={stack.iconKey}
														alt={stack.name}
														className="w-6 h-6"
														lazy={true}
														hideOnError={true}
													/>
												{:else}
													<img class="w-6 h-6 opacity-50" src={PLACEHOLDER_ICON} alt="" />
												{/if}
												<div>
													<div class="font-medium text-sm">{stack.name}</div>
													<div class="text-xs text-base-content/60 font-mono">{stack.id}</div>
												</div>
											</div>
										</td>
										<td class="text-right font-mono">{formatAmount(Number(row.stored ?? 0))}</td>
										<td class="text-right font-mono">{formatAmount(Number(row.waiting ?? 0))}</td>
										<td class="text-right font-mono">{formatAmount(Number(row.active ?? 0))}</td>
									</tr>
								{/each}
							</tbody>
						</table>
					</div>
				{:else if view === 'statusTree'}
					<div class="space-y-3">
						{#each [
							{ title: 'Active', list: entriesActive, key: 'active' },
							{ title: 'Waiting', list: entriesWaiting, key: 'waiting' },
							{ title: 'Stored', list: entriesStored, key: 'stored' }
						] as section}
							<div class="collapse collapse-arrow bg-base-300 rounded-lg">
								<input type="checkbox" checked={section.title !== 'Stored'} />
								<div class="collapse-title font-medium flex items-center justify-between">
									<span>{section.title}</span>
									<span class="badge badge-sm">{section.list.length}</span>
								</div>
								<div class="collapse-content">
									<div class="space-y-2 pt-2">
										{#each section.list as row (row.stack.iconKey)}
											{@const val = row[section.key]}
											<CpuProcessTreeRow
												stack={row.stack}
												value={typeof val === 'string' ? val : String(val)}
												label={section.title}
											/>
										{/each}
									</div>
								</div>
							</div>
						{/each}
					</div>
				{:else if view === 'dependencyTree'}
					{#if data.patterns.length === 0}
						<div class="text-center py-8 text-base-content/50">
							No pattern/task data available for this job.
						</div>
					{:else}
						<div class="bg-base-300 rounded-lg p-4 overflow-auto">
							<CpuDependencyTree
								root={data.summary.finalOutput}
								patterns={data.patterns}
								entries={data.entries}
							/>
						</div>
					{/if}
				{/if}
			{:else}
				<div class="text-center py-12 text-base-content/50">
					Select a busy CPU to view its process.
				</div>
			{/if}
		</div>
	</div>
	<form method="dialog" class="modal-backdrop bg-black/60">
		<button on:click={onClose}>close</button>
	</form>
</dialog>
