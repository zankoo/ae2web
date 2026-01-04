<script lang="ts">
	export let visible: boolean = false;
	export let htmlLines: string[] = [];

	let tooltipEl: HTMLDivElement | null = null;

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
		<div>{@html line}</div>
	{/each}
</div>
