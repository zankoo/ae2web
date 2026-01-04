<script lang="ts">
	import type { TerminalInfo } from '$lib/api/types';
	import Button from '$lib/components/Button.svelte';

	export let online: boolean = false;
	export let displayName: string = 'Loading...';
	export let terminalId: string = '';
	export let connectedTerminals: TerminalInfo[] = [];
	export let dropdownOpen: boolean = false;
	export let onToggleDropdown: () => void;
	export let onSelectTerminal: (terminal: TerminalInfo) => void;

	let containerEl: HTMLDivElement | null = null;

	export function getContainerElement(): HTMLDivElement | null {
		return containerEl;
	}
</script>

<div class="dropdown dropdown-end" bind:this={containerEl}>
	<Button variant="outline" size="sm" className="gap-2 font-mono" onClick={onToggleDropdown}>
		<span
			class="w-2 h-2 rounded-full {online
				? 'bg-success shadow-[0_0_8px_var(--color-success)]'
				: 'bg-error'}"
		></span>
		{displayName}
		<svg
			class="w-3 h-3 opacity-50 block transition-transform {dropdownOpen ? 'rotate-180' : ''}"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="2"><polyline points="6 9 12 15 18 9" /></svg
		>
	</Button>

	{#if dropdownOpen}
		<ul
			class="dropdown-content menu bg-card rounded-xl z-50 w-64 p-2 shadow-xl border border-border mt-2 animate-slide-up"
		>
			{#if connectedTerminals.length === 0}
				<li class="disabled"><span class="text-muted-foreground">No terminals found</span></li>
			{:else}
				{#each connectedTerminals as t (t.id)}
					<li>
						<button
							class="font-mono text-sm rounded-lg hover:bg-muted {t.id === terminalId
								? 'bg-muted text-accent font-bold'
								: ''}"
							on:click={() => onSelectTerminal(t)}
						>
							{t.displayName}
						</button>
					</li>
				{/each}
			{/if}
		</ul>
	{/if}
</div>
