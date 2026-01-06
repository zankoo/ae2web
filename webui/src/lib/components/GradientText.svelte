<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		as = 'span',
		className = '',
		children
	}: { as?: string; className?: string; children: Snippet } = $props();
</script>

<svelte:element this={as} class="gradient-text {className}">
	{@render children()}
</svelte:element>
