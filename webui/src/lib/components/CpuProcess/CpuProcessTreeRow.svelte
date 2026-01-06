<script lang="ts">
	import type { GenericStackDto } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatFluidAmount } from '$lib/utils/format';

	let {
		stack,
		value,
		label
	}: {
		stack: GenericStackDto;
		value: number;
		label: string;
	} = $props();

	let isFluid = $derived(stack?.type === 'fluid');
	let formattedValue = $derived(
		isFluid ? formatFluidAmount(Number(value ?? 0)) : formatAmount(Number(value ?? 0))
	);
</script>

<div
	class="flex items-center justify-between gap-3 p-2 bg-base-200 border border-base-300 rounded-lg"
>
	<div class="flex items-center gap-2.5 min-w-0">
		{#if stack?.iconKey}
			<IconImage
				iconKey={stack.iconKey}
				alt={stack.name}
				className="w-5 h-5 flex-shrink-0"
				lazy={true}
				hideOnError={true}
			/>
		{:else}
			<img class="w-5 h-5 flex-shrink-0 opacity-50" src={PLACEHOLDER_ICON} alt="" />
		{/if}

		<div class="min-w-0">
			<div class="font-medium text-sm text-base-content truncate">{stack?.name ?? 'Unknown'}</div>
			<div class="text-xs text-base-content/60 font-mono truncate max-w-[54ch]">
				{stack?.id ?? ''}
			</div>
		</div>
	</div>

	<div class="flex items-baseline gap-2 flex-shrink-0">
		<span class="text-xs font-bold uppercase tracking-wider text-base-content/60">{label}</span>
		<span class="font-mono font-bold text-base-content">{formattedValue}</span>
	</div>
</div>
