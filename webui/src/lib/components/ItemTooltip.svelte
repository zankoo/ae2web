<script lang="ts">
	let {
		visible = false,
		htmlLines = []
	}: {
		visible?: boolean;
		htmlLines?: string[];
	} = $props();

	let tooltipEl: HTMLDivElement | null = $state(null);

	export function getElement(): HTMLDivElement | null {
		return tooltipEl;
	}

	export function setPosition(left: number, top: number): void {
		if (tooltipEl) {
			tooltipEl.style.transform = `translate3d(${left}px, ${top}px, 0)`;
		}
	}
</script>

<div class="ae2web-tooltip" style:display={visible ? 'block' : 'none'} bind:this={tooltipEl}>
	{#each htmlLines as line, i (i)}
		<!-- eslint-disable-next-line svelte/no-at-html-tags -- sanitized Minecraft text from server -->
		<div>{@html line}</div>
	{/each}
</div>
