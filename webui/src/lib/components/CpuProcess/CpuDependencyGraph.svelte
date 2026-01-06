<script lang="ts">
	import { onMount } from 'svelte';
	import { SvelteMap } from 'svelte/reactivity';

	import type { CpuProcessEntryDto, CpuProcessPatternDto, GenericStackDto } from '$lib/api/types';

	import {
		Background,
		BackgroundVariant,
		Controls,
		SvelteFlow,
		type Edge,
		type Node,
		type NodeTypes,
		type EdgeTypes
	} from '@xyflow/svelte';
	import '@xyflow/svelte/dist/style.css';
	import ELK from 'elkjs/lib/elk.bundled.js';

	import CpuDependencyGraphAutoFit from '$lib/components/CpuProcess/CpuDependencyGraphAutoFit.svelte';
	import CpuDependencyGraphNode from '$lib/components/CpuProcess/CpuDependencyGraphNode.svelte';
	import CpuDependencyGraphEdge from '$lib/components/CpuProcess/CpuDependencyGraphEdge.svelte';
	import { buildCpuDependencyGraph } from '$lib/components/CpuProcess/cpuDependencyGraphModel';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';

	let {
		root = undefined,
		patterns = [],
		entries = []
	}: {
		root?: GenericStackDto;
		patterns?: CpuProcessPatternDto[];
		entries?: CpuProcessEntryDto[];
	} = $props();

	const elk = new ELK();
	const NODE_W = 260;
	const NODE_H = 82;

	let mounted = $state(false);
	let showSubstitutes = $state(false);
	let selectedNodeId = $state<string | null>(null);
	let layouting = $state(false);
	let lastLayoutKey = '';
	let nodePositions = $state(new Map<string, { x: number; y: number }>());
	let layoutToken = 0;
	let fitSignal = $state(-1);

	const nodeTypes: NodeTypes = {
		item: CpuDependencyGraphNode
	};

	const edgeTypes: EdgeTypes = {
		flow: CpuDependencyGraphEdge
	};

	let graph = $derived(
		buildCpuDependencyGraph({
			root,
			patterns,
			entries,
			options: {
				maxNodes: 600,
				maxDepth: 12,
				maxPatternsPerOutput: 3,
				showSubstitutes,
				inferChoiceFromEntries: true
			}
		})
	);

	function fmtAmount(amount: number, type?: string): string {
		return type === 'fluid' ? formatFluidAmount(amount) : formatAmount(amount);
	}

	let nodes = $derived(
		graph.nodes.map((n): Node => {
			const pos = nodePositions.get(n.id) ?? { x: 0, y: 0 };
			return {
				id: n.id,
				type: 'item',
				position: pos,
				data: { stack: n.stack, counts: n.counts, isRoot: n.isRoot },
				style: `width:${NODE_W}px;height:${NODE_H}px;`
			};
		})
	);

	const nodeById = $derived(new SvelteMap(graph.nodes.map((n) => [n.id, n] as const)));

	function edgeEmphasis(sourceId: string): number {
		const c = nodeById.get(sourceId)?.counts;
		const waiting = Number(c?.waiting ?? 0);
		const active = Number(c?.active ?? 0);
		if (waiting <= 0 && active <= 0) return 0;
		return Math.max(0, Math.min(1, (waiting > 0 ? 0.7 : 0) + (active > 0 ? 0.35 : 0)));
	}

	let edges = $derived(
		graph.edges.map((e): Edge => {
			return {
				id: e.id,
				source: e.source,
				target: e.target,
				type: 'flow',
				data: { kind: e.kind, multiplier: e.multiplier, emphasis: edgeEmphasis(e.source) },
				animated: true
			};
		})
	);

	let selected = $derived(selectedNodeId ? (nodeById.get(selectedNodeId) ?? null) : null);
	let selectedPatterns = $derived(
		selectedNodeId && selectedNodeId.length > 0
			? (patterns ?? []).filter((p) => (p.outputs ?? []).some((o) => o?.iconKey === selectedNodeId))
			: []
	);

	const entryByIconKey = $derived.by(() => {
		const map = new SvelteMap<string, CpuProcessEntryDto>();
		for (const e of entries ?? []) {
			const key = e?.stack?.iconKey;
			if (key) map.set(key, e);
		}
		return map;
	});

	let layoutKey = $derived(
		graph.nodes
			.map((n) => n.id)
			.sort()
			.join('|') +
			'||' +
			graph.edges
				.map((e) => e.id)
				.sort()
				.join('|')
	);

	async function layoutIfNeeded(): Promise<void> {
		if (!mounted) return;
		if (!layoutKey || layoutKey === lastLayoutKey) return;
		lastLayoutKey = layoutKey;
		layouting = true;
		const token = ++layoutToken;

		const elkGraph = {
			id: 'root',
			layoutOptions: {
				'elk.algorithm': 'layered',
				'elk.direction': 'LEFT',
				'elk.spacing.nodeNode': '20',
				'elk.layered.spacing.nodeNodeBetweenLayers': '60',
				'elk.layered.nodePlacement.strategy': 'NETWORK_SIMPLEX',
				'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP'
			},
			children: graph.nodes.map((n) => ({ id: n.id, width: NODE_W, height: NODE_H })),
			edges: graph.edges.map((e) => ({ id: e.id, sources: [e.source], targets: [e.target] }))
		};

		try {
			const layouted = await elk.layout(elkGraph as any);
			if (token !== layoutToken) return;

			// eslint-disable-next-line svelte/prefer-svelte-reactivity -- temp variable in async function
			const next = new Map<string, { x: number; y: number }>();
			for (const c of layouted.children ?? []) {
				const x = Number(c?.x ?? 0);
				const y = Number(c?.y ?? 0);
				if (c?.id) next.set(String(c.id), { x, y });
			}
			nodePositions = next;
			fitSignal = token;
		} finally {
			if (token === layoutToken) layouting = false;
		}
	}

	function handleNodeClick({ node }: { node: Node }): void {
		selectedNodeId = node?.id ?? null;
	}

	function handlePaneClick(): void {
		selectedNodeId = null;
	}

	function closeDetails(): void {
		selectedNodeId = null;
	}

	function entryFor(iconKey: string): CpuProcessEntryDto | null {
		return entryByIconKey.get(iconKey) ?? null;
	}

	onMount(() => {
		mounted = true;
		void layoutIfNeeded();
		return () => {
			mounted = false;
		};
	});

	// Trigger layout when layoutKey changes
	$effect(() => {
		if (layoutKey) void layoutIfNeeded();
	});
</script>

<div class="space-y-3">
	<div class="flex items-center justify-between gap-3">
		<label class="flex items-center gap-2 text-sm">
			<input
				type="checkbox"
				class="toggle toggle-sm toggle-primary"
				bind:checked={showSubstitutes}
			/>
			<span>Show substitutes</span>
		</label>

		<div class="flex items-center gap-2">
			{#if graph.truncated}
				<span class="badge badge-warning badge-sm">truncated</span>
			{/if}
			{#if layouting}
				<span class="loading loading-spinner loading-xs text-primary"></span>
			{/if}
		</div>
	</div>

	<div class="relative h-[520px] rounded-xl border border-base-300 bg-base-200 overflow-hidden">
		{#if !root?.iconKey || (patterns?.length ?? 0) === 0}
			<div class="h-full grid place-items-center text-base-content/60 font-mono">
				No dependency data.
			</div>
		{:else}
			<SvelteFlow
				{nodes}
				{edges}
				{nodeTypes}
				{edgeTypes}
				fitView
				nodesDraggable={false}
				nodesConnectable={false}
				panOnScroll={true}
				zoomOnScroll={true}
				zoomOnPinch={true}
				preventScrolling={false}
				onnodeclick={handleNodeClick}
				onpaneclick={handlePaneClick}
			>
				<CpuDependencyGraphAutoFit signal={fitSignal} />
				<Background variant={BackgroundVariant.Dots} gap={18} size={1} />
				<Controls />
			</SvelteFlow>

			{#if selected}
				<div
					class="absolute top-3 right-3 w-[360px] max-w-[calc(100%-1.5rem)] max-h-[calc(100%-1.5rem)] overflow-auto rounded-xl border border-base-300 bg-base-100/95 backdrop-blur shadow-xl"
				>
					<div class="p-3 border-b border-base-300 flex items-start gap-2">
						<div class="flex-1 min-w-0">
							<div class="font-bold truncate">
								{selected.stack?.name ?? selected.stack?.id ?? selected.id}
							</div>
							<div class="text-xs text-base-content/60 font-mono truncate">
								{selected.stack?.id ?? ''}
							</div>
						</div>
						<button
							class="btn btn-ghost btn-xs btn-circle"
							aria-label="Close details"
							onclick={closeDetails}
						>
							<svg
								class="w-3.5 h-3.5"
								viewBox="0 0 24 24"
								fill="none"
								stroke="currentColor"
								stroke-width="2"
							>
								<path d="M18 6L6 18M6 6l12 12" />
							</svg>
						</button>
					</div>

					<div class="p-3 space-y-3">
						{#if selected.counts}
							<div class="flex flex-wrap gap-1.5">
								{#if selected.counts.active > 0}
									<span class="badge badge-sm badge-info font-mono"
										>A {fmtAmount(selected.counts.active, selected.stack?.type)}</span
									>
								{/if}
								{#if selected.counts.waiting > 0}
									<span class="badge badge-sm badge-warning font-mono"
										>W {fmtAmount(selected.counts.waiting, selected.stack?.type)}</span
									>
								{/if}
								{#if selected.counts.stored > 0}
									<span class="badge badge-sm badge-secondary font-mono"
										>S {fmtAmount(selected.counts.stored, selected.stack?.type)}</span
									>
								{/if}
							</div>
						{/if}

						<div class="text-xs text-base-content/60">
							`possibleInputs` are primary + substitutes; substitutes may be used opportunistically
							and won’t be autocrafted.
						</div>

						<div class="space-y-2">
							<div class="text-xs font-bold uppercase tracking-wider text-base-content/60">
								Producing patterns ({selectedPatterns.length})
							</div>

							{#if selectedPatterns.length === 0}
								<div class="text-sm text-base-content/60">
									No patterns in this job produce this item.
								</div>
							{:else}
								{#each selectedPatterns as p (p.patternId)}
									<div
										class="collapse collapse-arrow bg-base-200 border border-base-300 rounded-lg"
									>
										<input type="checkbox" checked />
										<div class="collapse-title text-sm font-medium">
											<div class="flex items-center gap-2 min-w-0">
												<span class="min-w-0 flex-1 truncate font-mono">{p.patternId}</span>
												<span class="badge badge-sm whitespace-nowrap flex-shrink-0"
													>runs: {p.remainingRuns}</span
												>
											</div>
										</div>
										<div class="collapse-content">
											<div class="space-y-2 pt-2">
												{#each p.inputs as input, i (i)}
													<div class="p-2 rounded-lg border border-base-300 bg-base-100">
														<div class="flex items-center justify-between gap-2">
															<div
																class="text-xs font-bold uppercase tracking-wider text-base-content/60"
															>
																Input #{i + 1}
															</div>
															<div class="badge badge-xs font-mono">×{input.multiplier}</div>
														</div>

														<div class="mt-2 grid gap-1.5">
															{#each input.choices as choice, cIndex (choice.iconKey)}
																{@const entry = entryFor(choice.iconKey)}
																{@const isPrimary = cIndex === 0}
																<div
																	data-cpu-graph-choice={choice.iconKey}
																	class="flex flex-wrap items-center gap-2 p-1.5 rounded-md border border-base-300 bg-base-200"
																>
																	<div class="min-w-0 flex-1 flex items-center gap-2">
																		<span
																			class={`badge badge-xs ${isPrimary ? 'badge-primary' : 'badge-ghost'} font-mono flex-shrink-0 whitespace-nowrap`}
																		>
																			{isPrimary ? 'PRIMARY' : 'ALT'}
																		</span>
																		<div class="min-w-0 flex-1">
																			<div class="text-sm truncate">{choice.name ?? choice.id}</div>
																			<div
																				class="text-[0.7rem] font-mono text-base-content/60 truncate"
																			>
																				{choice.id}
																			</div>
																		</div>
																	</div>

																	{#if entry}
																		<div class="flex flex-wrap gap-1.5 justify-end ml-auto">
																			{#if entry.active > 0}
																				<span
																					class="badge badge-xs badge-info font-mono whitespace-nowrap"
																				>
																					A {fmtAmount(entry.active, entry.stack?.type)}
																				</span>
																			{/if}
																			{#if entry.waiting > 0}
																				<span
																					class="badge badge-xs badge-warning font-mono whitespace-nowrap"
																				>
																					W {fmtAmount(entry.waiting, entry.stack?.type)}
																				</span>
																			{/if}
																			{#if entry.stored > 0}
																				<span
																					class="badge badge-xs badge-secondary font-mono whitespace-nowrap"
																				>
																					S {fmtAmount(entry.stored, entry.stack?.type)}
																				</span>
																			{/if}
																		</div>
																	{/if}
																</div>
															{/each}
														</div>
													</div>
												{/each}
											</div>
										</div>
									</div>
								{/each}
							{/if}
						</div>
					</div>
				</div>
			{/if}
		{/if}
	</div>
</div>
