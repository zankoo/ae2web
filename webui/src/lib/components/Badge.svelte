<script lang="ts">
	export let label: string;
	export let pulse: boolean = false;
	export let className: string = '';
</script>

<div
	class="inline-flex items-center gap-2 rounded-full border border-accent/20 bg-accent/5 px-3 py-1 {className}"
>
	{#if pulse}
		<span class="relative flex h-2 w-2">
			<span
				class="animate-ping absolute inline-flex h-full w-full rounded-full bg-accent opacity-75"
			></span>
			<span class="relative inline-flex rounded-full h-2 w-2 bg-accent"></span>
		</span>
	{:else}
		<span class="h-2 w-2 rounded-full bg-accent"></span>
	{/if}
	<span class="font-mono text-xs uppercase tracking-widest text-accent font-semibold">
		{label}
	</span>
</div>
