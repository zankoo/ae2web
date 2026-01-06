<script lang="ts">
	import { Handle, Position } from '@xyflow/svelte';

	import type { GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';

	import type { CpuCounts } from './cpuDependencyGraphModel';

	type NodeData = {
		stack: GenericStackDto;
		counts: CpuCounts | null;
		isRoot: boolean;
	};

	let {
		id,
		data,
		selected = false
	}: {
		id: string;
		data: NodeData;
		selected?: boolean;
	} = $props();

	let stack = $derived(data?.stack);
	let counts = $derived(data?.counts);
	let isRoot = $derived(Boolean(data?.isRoot));
	let isFluid = $derived(stack?.type === 'fluid');

	function fmt(n: number): string {
		return isFluid ? formatFluidAmount(n) : formatAmount(n);
	}

	let active = $derived(Number(counts?.active ?? 0));
	let waiting = $derived(Number(counts?.waiting ?? 0));
	let stored = $derived(Number(counts?.stored ?? 0));

	let stateClass = $derived(
		active > 0
			? 'border-info/40 bg-info/5'
			: waiting > 0
				? 'border-warning/40 bg-warning/5'
				: 'border-base-300 bg-base-200'
	);

	let selectClass = $derived(
		selected ? 'ring-2 ring-primary/40 ring-offset-2 ring-offset-base-200' : ''
	);
</script>

<div
	class={`relative px-2.5 py-2 rounded-xl border shadow-sm ${stateClass} ${selectClass}`}
	data-cpu-graph-node={id}
	data-cpu-graph-root={isRoot ? 'true' : undefined}
>
	<!-- Hidden handles so Svelte Flow can attach edges -->
	<Handle type="target" position={Position.Right} class="opacity-0 pointer-events-none" />
	<Handle type="source" position={Position.Left} class="opacity-0 pointer-events-none" />

	<div class="flex items-start gap-2.5">
		<div
			class="w-9 h-9 rounded-lg bg-base-100 border border-base-300/70 flex items-center justify-center flex-shrink-0"
		>
			{#if stack?.iconKey}
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
		</div>

		<div class="min-w-0 flex-1">
			<div class="flex items-center gap-2">
				<div class="font-medium text-sm truncate">{stack?.name ?? stack?.id ?? 'Unknown'}</div>
				{#if isRoot}
					<span class="badge badge-xs badge-primary flex-shrink-0">OUTPUT</span>
				{/if}
			</div>
			<div class="text-[0.7rem] text-base-content/60 font-mono truncate">{stack?.id ?? ''}</div>
			{#if counts}
				<div class="flex flex-wrap gap-1.5 mt-1">
					{#if active > 0}
						<span class="badge badge-xs badge-info font-mono">A {fmt(active)}</span>
					{/if}
					{#if waiting > 0}
						<span class="badge badge-xs badge-warning font-mono">W {fmt(waiting)}</span>
					{/if}
					{#if stored > 0}
						<span class="badge badge-xs badge-secondary font-mono">S {fmt(stored)}</span>
					{/if}
				</div>
			{/if}
		</div>
	</div>
</div>
