<script lang="ts">
	import { getBezierPath, type Position } from '@xyflow/svelte';

	import type { CpuDependencyGraphEdgeKind } from './cpuDependencyGraphModel';

	type EdgeData = {
		kind: CpuDependencyGraphEdgeKind;
		multiplier: number;
		emphasis: number; // 0..1
	};

	let {
		id,
		sourceX,
		sourceY,
		targetX,
		targetY,
		sourcePosition = undefined,
		targetPosition = undefined,
		markerStart = undefined,
		markerEnd = undefined,
		data = undefined
	}: {
		id: string;
		sourceX: number;
		sourceY: number;
		targetX: number;
		targetY: number;
		sourcePosition?: Position;
		targetPosition?: Position;
		markerStart?: string;
		markerEnd?: string;
		data?: EdgeData;
	} = $props();

	let pathData = $derived(
		getBezierPath({
			sourceX,
			sourceY,
			targetX,
			targetY,
			sourcePosition,
			targetPosition
		})
	);
	let path = $derived(pathData[0]);
	let labelX = $derived(pathData[1]);
	let labelY = $derived(pathData[2]);

	let kind = $derived(data?.kind ?? 'primary');
	let multiplier = $derived(Number(data?.multiplier ?? 1));
	let emphasis = $derived(Math.max(0, Math.min(1, Number(data?.emphasis ?? 0))));

	let label = $derived(multiplier !== 1 ? `×${multiplier}` : '');
	let isSubstitute = $derived(kind === 'substitute');

	let strokeClass = $derived(isSubstitute ? 'text-base-content/40' : 'text-primary/70');
	let dash = $derived(isSubstitute ? '3 14' : '7 10');
	let speedMs = $derived(isSubstitute ? 1600 : Math.round(1400 - emphasis * 700));
	let width = $derived(isSubstitute ? 1.2 : 1.6);
	let opacity = $derived(isSubstitute ? 0.7 : 1);
</script>

<g class="cpu-flow-edge" data-cpu-graph-edge={id}>
	<path
		d={path}
		fill="none"
		stroke="currentColor"
		stroke-linecap="round"
		stroke-width={width}
		stroke-dasharray={dash}
		marker-start={markerStart}
		marker-end={markerEnd}
		class={`cpu-flow-edge-path ${strokeClass}`}
		style={`opacity:${opacity};--dash-speed:${speedMs}ms;`}
	/>

	{#if label}
		<g transform={`translate(${labelX}, ${labelY})`}>
			<rect x="-16" y="-9" width="32" height="18" rx="9" class="fill-base-100/85 stroke-base-300" />
			<text
				text-anchor="middle"
				dominant-baseline="middle"
				class="fill-base-content/80 font-mono text-[10px]"
			>
				{label}
			</text>
		</g>
	{/if}
</g>

<style>
	.cpu-flow-edge-path {
		animation: cpuFlowDash var(--dash-speed) linear infinite;
	}

	@keyframes cpuFlowDash {
		to {
			stroke-dashoffset: -34;
		}
	}
</style>
