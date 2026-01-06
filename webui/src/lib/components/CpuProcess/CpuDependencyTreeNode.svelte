<script lang="ts">
	import type { GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';
	import CpuDependencyTreeNode from './CpuDependencyTreeNode.svelte';

	type Counts = { stored: number; waiting: number; active: number };
	type NodeKind = 'stack' | 'pattern' | 'input';

	interface DepNode {
		kind: NodeKind;
		id: string;
		stack?: GenericStackDto;
		remainingRuns?: number;
		multiplier?: number;
		cycle?: boolean;
		truncated?: boolean;
		children: DepNode[];
	}

	interface Props {
		node: DepNode;
		depth: number;
		entryByIconKey: Map<string, Counts>;
	}

	let { node, depth, entryByIconKey }: Props = $props();

	let isOpen = $derived(depth < 2);
	let isStack = $derived(node.kind === 'stack');
	let isPattern = $derived(node.kind === 'pattern');
	let isInput = $derived(node.kind === 'input');
	let hasChildren = $derived(node.children.length > 0);
	let counts = $derived(
		isStack && node.stack?.iconKey ? (entryByIconKey.get(node.stack.iconKey) ?? null) : null
	);
	let isFluid = $derived(node.stack?.type === 'fluid');

	function fmt(n: number): string {
		return isFluid ? formatFluidAmount(n) : formatAmount(n);
	}
</script>

{#if hasChildren}
	<details class="dep-details" data-node-id={node.id} open={isOpen} style={`--depth:${depth}`}>
		<summary class="list-none cursor-pointer">
			<div
				class="flex items-center gap-2.5 p-2.5 bg-base-200 border border-base-300 rounded-lg hover:border-primary/30 transition-colors"
			>
				{#if isStack}
					{#if node.stack?.iconKey}
						<IconImage
							iconKey={node.stack.iconKey}
							alt={node.stack.name}
							className="w-5 h-5 flex-shrink-0"
							lazy={true}
							hideOnError={true}
						/>
					{:else}
						<img class="w-5 h-5 flex-shrink-0 opacity-50" src={PLACEHOLDER_ICON} alt="" />
					{/if}
					<div class="min-w-0 flex-1">
						<div class="font-medium text-sm text-base-content truncate">
							{node.stack?.name ?? node.stack?.id ?? 'Unknown'}
						</div>
						<div class="text-xs text-base-content/60 font-mono truncate">
							{node.stack?.id ?? ''}
						</div>
					</div>
					{#if counts}
						<div class="flex gap-1.5 flex-shrink-0">
							{#if counts.active > 0}
								<span class="badge badge-sm badge-info">A {fmt(counts.active)}</span>
							{/if}
							{#if counts.waiting > 0}
								<span class="badge badge-sm badge-warning">W {fmt(counts.waiting)}</span>
							{/if}
							{#if counts.stored > 0}
								<span class="badge badge-sm badge-secondary">S {fmt(counts.stored)}</span>
							{/if}
						</div>
					{/if}
					{#if node.cycle}
						<span class="badge badge-sm badge-outline">cycle</span>
					{:else if node.truncated}
						<span class="badge badge-sm badge-outline">truncated</span>
					{/if}
				{:else if isPattern}
					<span class="badge badge-sm badge-primary">PATTERN</span>
					<div class="min-w-0">
						<div class="font-medium text-sm text-base-content">
							Pattern • {formatAmount(Number(node.remainingRuns ?? 0))} runs
						</div>
					</div>
				{:else if isInput}
					<span class="badge badge-sm badge-accent">INPUT</span>
					<div class="min-w-0">
						<div class="font-medium text-sm text-base-content">
							Input ×{formatAmount(Number(node.multiplier ?? 1))}
						</div>
					</div>
				{/if}
			</div>
		</summary>
		<div class="grid gap-2 mt-2">
			{#each node.children as child (child.id)}
				<CpuDependencyTreeNode node={child} depth={depth + 1} {entryByIconKey} />
			{/each}
		</div>
	</details>
{:else}
	<div class="dep-leaf" data-node-id={node.id} style={`--depth:${depth}`}>
		<div class="flex items-center gap-2.5 p-2.5 bg-base-200 border border-base-300 rounded-lg">
			{#if isStack}
				{#if node.stack?.iconKey}
					<IconImage
						iconKey={node.stack.iconKey}
						alt={node.stack.name}
						className="w-5 h-5 flex-shrink-0"
						lazy={true}
						hideOnError={true}
					/>
				{:else}
					<img class="w-5 h-5 flex-shrink-0 opacity-50" src={PLACEHOLDER_ICON} alt="" />
				{/if}
				<div class="min-w-0 flex-1">
					<div class="font-medium text-sm text-base-content truncate">
						{node.stack?.name ?? node.stack?.id ?? 'Unknown'}
					</div>
					<div class="text-xs text-base-content/60 font-mono truncate">{node.stack?.id ?? ''}</div>
				</div>
				{#if counts}
					<div class="flex gap-1.5 flex-shrink-0">
						{#if counts.active > 0}
							<span class="badge badge-sm badge-info">A {fmt(counts.active)}</span>
						{/if}
						{#if counts.waiting > 0}
							<span class="badge badge-sm badge-warning">W {fmt(counts.waiting)}</span>
						{/if}
						{#if counts.stored > 0}
							<span class="badge badge-sm badge-secondary">S {fmt(counts.stored)}</span>
						{/if}
					</div>
				{/if}
				{#if node.cycle}
					<span class="badge badge-sm badge-outline">cycle</span>
				{:else if node.truncated}
					<span class="badge badge-sm badge-outline">truncated</span>
				{/if}
			{:else if isPattern}
				<span class="badge badge-sm badge-primary">PATTERN</span>
				<div class="min-w-0">
					<div class="font-medium text-sm text-base-content">
						Pattern • {formatAmount(Number(node.remainingRuns ?? 0))} runs
					</div>
				</div>
			{:else if isInput}
				<span class="badge badge-sm badge-accent">INPUT</span>
				<div class="min-w-0">
					<div class="font-medium text-sm text-base-content">
						Input ×{formatAmount(Number(node.multiplier ?? 1))}
					</div>
				</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.dep-details,
	.dep-leaf {
		margin-left: calc(var(--depth) * 10px);
	}

	.dep-leaf {
		margin-top: 0.5rem;
	}

	.dep-details summary::-webkit-details-marker {
		display: none;
	}
</style>
