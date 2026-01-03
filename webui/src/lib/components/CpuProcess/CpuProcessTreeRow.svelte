<script lang="ts">
	import type { GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount } from '$lib/utils/format';

	export let stack: GenericStackDto;
	export let value: number;
	export let label: string;
</script>

<div class="cpu-process-tree-row">
	<div class="cpu-process-tree-item">
		{#if stack?.iconKey}
			<IconImage iconKey={stack.iconKey} alt={stack.name} className="cpu-process-tree-icon" lazy={true} hideOnError={true} />
		{:else}
			<img class="cpu-process-tree-icon" src={PLACEHOLDER_ICON} alt="" />
		{/if}

		<div class="cpu-process-tree-text">
			<div class="cpu-process-tree-name">{stack?.name ?? 'Unknown'}</div>
			<div class="cpu-process-tree-id">{stack?.id ?? ''}</div>
		</div>
	</div>

	<div class="cpu-process-tree-value">
		<span class="cpu-process-tree-label">{label}</span>
		<span class="cpu-process-tree-num">{formatAmount(Number(value ?? 0))}</span>
	</div>
</div>

<style>
	.cpu-process-tree-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 8px 10px;
		border: 1px solid rgba(255, 255, 255, 0.18);
		border-radius: 6px;
		background: rgba(0, 0, 0, 0.18);
	}

	.cpu-process-tree-item {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}

	.cpu-process-tree-icon {
		width: 20px;
		height: 20px;
		image-rendering: pixelated;
		flex: 0 0 auto;
		border-radius: 2px;
	}

	.cpu-process-tree-text {
		min-width: 0;
	}

	.cpu-process-tree-name {
		font-weight: 700;
		font-size: 0.9rem;
		color: var(--ae2-text);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.cpu-process-tree-id {
		margin-top: 2px;
		font-size: 0.75rem;
		color: var(--ae2-text-dim);
		font-family: var(--font-mono);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		max-width: 54ch;
	}

	.cpu-process-tree-value {
		display: flex;
		align-items: baseline;
		gap: 8px;
		flex: 0 0 auto;
	}

	.cpu-process-tree-label {
		font-size: 0.72rem;
		font-weight: 800;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--ae2-text-dim);
	}

	.cpu-process-tree-num {
		font-family: var(--font-mono);
		font-weight: 800;
		color: var(--ae2-text);
	}
</style>

