<script lang="ts">
	import type { GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount } from '$lib/utils/format';

	type Counts = { stored: number; waiting: number; active: number };
	type NodeKind = 'stack' | 'pattern' | 'input';

	type DepNode = {
		kind: NodeKind;
		id: string;
		stack?: GenericStackDto;
		remainingRuns?: number;
		multiplier?: number;
		cycle?: boolean;
		truncated?: boolean;
		children: DepNode[];
	};

	export let node: DepNode;
	export let depth: number;
	export let entryByIconKey: Map<string, Counts>;

	const isOpen = depth < 2;
	$: isStack = node.kind === 'stack';
	$: isPattern = node.kind === 'pattern';
	$: isInput = node.kind === 'input';
	$: hasChildren = node.children.length > 0;
	$: counts = isStack && node.stack?.iconKey ? (entryByIconKey.get(node.stack.iconKey) ?? null) : null;
</script>

{#if hasChildren}
	<details class="cpu-dep-details" open={isOpen} style={`--depth:${depth}`}>
		<summary class="cpu-dep-summary">
			<div class={`cpu-dep-row ${node.kind}`}>
				{#if isStack}
					{#if node.stack?.iconKey}
						<IconImage iconKey={node.stack.iconKey} alt={node.stack.name} className="cpu-dep-icon" lazy={true} hideOnError={true} />
					{:else}
						<img class="cpu-dep-icon" src={PLACEHOLDER_ICON} alt="" />
					{/if}
					<div class="cpu-dep-text">
						<div class="cpu-dep-name">{node.stack?.name ?? node.stack?.id ?? 'Unknown'}</div>
						<div class="cpu-dep-id">{node.stack?.id ?? ''}</div>
					</div>
					{#if counts}
						<div class="cpu-dep-badges">
							{#if counts.active > 0}
								<span class="badge active">A {formatAmount(counts.active)}</span>
							{/if}
							{#if counts.waiting > 0}
								<span class="badge waiting">W {formatAmount(counts.waiting)}</span>
							{/if}
							{#if counts.stored > 0}
								<span class="badge stored">S {formatAmount(counts.stored)}</span>
							{/if}
						</div>
					{/if}
					{#if node.cycle}
						<span class="cpu-dep-flag">cycle</span>
					{:else if node.truncated}
						<span class="cpu-dep-flag">truncated</span>
					{/if}
				{:else if isPattern}
					<div class="cpu-dep-chip">PATTERN</div>
					<div class="cpu-dep-text">
						<div class="cpu-dep-name">Pattern • {formatAmount(Number(node.remainingRuns ?? 0))} runs</div>
					</div>
				{:else if isInput}
					<div class="cpu-dep-chip input">INPUT</div>
					<div class="cpu-dep-text">
						<div class="cpu-dep-name">Input ×{formatAmount(Number(node.multiplier ?? 1))}</div>
					</div>
				{/if}
			</div>
		</summary>
		<div class="cpu-dep-children">
			{#each node.children as child (child.id)}
				<svelte:self node={child} depth={depth + 1} {entryByIconKey} />
			{/each}
		</div>
	</details>
{:else}
	<div class="cpu-dep-leaf" style={`--depth:${depth}`}>
		<div class={`cpu-dep-row ${node.kind}`}>
			{#if isStack}
				{#if node.stack?.iconKey}
					<IconImage iconKey={node.stack.iconKey} alt={node.stack.name} className="cpu-dep-icon" lazy={true} hideOnError={true} />
				{:else}
					<img class="cpu-dep-icon" src={PLACEHOLDER_ICON} alt="" />
				{/if}
				<div class="cpu-dep-text">
					<div class="cpu-dep-name">{node.stack?.name ?? node.stack?.id ?? 'Unknown'}</div>
					<div class="cpu-dep-id">{node.stack?.id ?? ''}</div>
				</div>
				{#if counts}
					<div class="cpu-dep-badges">
						{#if counts.active > 0}
							<span class="badge active">A {formatAmount(counts.active)}</span>
						{/if}
						{#if counts.waiting > 0}
							<span class="badge waiting">W {formatAmount(counts.waiting)}</span>
						{/if}
						{#if counts.stored > 0}
							<span class="badge stored">S {formatAmount(counts.stored)}</span>
						{/if}
					</div>
				{/if}
				{#if node.cycle}
					<span class="cpu-dep-flag">cycle</span>
				{:else if node.truncated}
					<span class="cpu-dep-flag">truncated</span>
				{/if}
			{:else if isPattern}
				<div class="cpu-dep-chip">PATTERN</div>
				<div class="cpu-dep-text">
					<div class="cpu-dep-name">Pattern • {formatAmount(Number(node.remainingRuns ?? 0))} runs</div>
				</div>
			{:else if isInput}
				<div class="cpu-dep-chip input">INPUT</div>
				<div class="cpu-dep-text">
					<div class="cpu-dep-name">Input ×{formatAmount(Number(node.multiplier ?? 1))}</div>
				</div>
			{/if}
		</div>
	</div>
{/if}

<style>
	.cpu-dep-details,
	.cpu-dep-leaf {
		margin-left: calc(var(--depth) * 10px);
	}

	.cpu-dep-summary {
		list-style: none;
		cursor: pointer;
	}

	.cpu-dep-summary::-webkit-details-marker {
		display: none;
	}

	.cpu-dep-row {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 12px;
		border: 1px solid rgba(255, 255, 255, 0.16);
		border-radius: 10px;
		background: linear-gradient(180deg, rgba(0, 0, 0, 0.24), rgba(0, 0, 0, 0.12));
	}

	.cpu-dep-row.stack:hover {
		border-color: rgba(34, 211, 238, 0.45);
		box-shadow: 0 0 0 2px rgba(34, 211, 238, 0.08);
	}

	.cpu-dep-row :global(.cpu-dep-icon) {
		width: 20px;
		height: 20px;
		image-rendering: pixelated;
		border-radius: 2px;
		flex: 0 0 auto;
	}

	.cpu-dep-text {
		min-width: 0;
	}

	.cpu-dep-name {
		font-weight: 800;
		color: var(--ae2-text);
		font-size: 0.92rem;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cpu-dep-id {
		margin-top: 2px;
		font-family: var(--font-mono);
		font-size: 0.72rem;
		color: var(--ae2-text-dim);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cpu-dep-chip {
		padding: 4px 8px;
		border-radius: 999px;
		border: 1px solid rgba(255, 255, 255, 0.18);
		font-weight: 900;
		letter-spacing: 0.08em;
		font-size: 0.65rem;
		color: rgba(255, 255, 255, 0.85);
		background: rgba(34, 211, 238, 0.16);
	}

	.cpu-dep-chip.input {
		background: rgba(147, 51, 234, 0.14);
	}

	.cpu-dep-badges {
		margin-left: auto;
		display: flex;
		gap: 6px;
		flex: 0 0 auto;
	}

	.badge {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		font-weight: 900;
		padding: 3px 8px;
		border-radius: 999px;
		border: 1px solid rgba(255, 255, 255, 0.16);
		background: rgba(0, 0, 0, 0.2);
	}

	.badge.active {
		color: #eaffff;
		border-color: rgba(34, 211, 238, 0.35);
	}

	.badge.waiting {
		color: #fff6ea;
		border-color: rgba(245, 158, 11, 0.35);
	}

	.badge.stored {
		color: #f3e8ff;
		border-color: rgba(147, 51, 234, 0.35);
	}

	.cpu-dep-flag {
		margin-left: 10px;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		padding: 2px 8px;
		border-radius: 999px;
		border: 1px solid rgba(255, 255, 255, 0.18);
		color: var(--ae2-text-dim);
	}

	.cpu-dep-children {
		margin-top: 8px;
		display: grid;
		gap: 8px;
	}

	.cpu-dep-leaf {
		margin-top: 8px;
	}
</style>
