<script lang="ts">
	export let as: string = 'span';
	export let className: string = '';
</script>

<svelte:element this={as} class="gradient-text {className}">
	<slot />
</svelte:element>
