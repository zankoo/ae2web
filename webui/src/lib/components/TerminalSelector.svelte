<script lang="ts">
	import type { TerminalInfo } from '$lib/api/types';

	let {
		online = false,
		displayName = 'Loading...',
		terminalId = '',
		connectedTerminals = [],
		dropdownOpen = false,
		onToggleDropdown,
		onSelectTerminal
	}: {
		online?: boolean;
		displayName?: string;
		terminalId?: string;
		connectedTerminals?: TerminalInfo[];
		dropdownOpen?: boolean;
		onToggleDropdown: () => void;
		onSelectTerminal: (terminal: TerminalInfo) => void;
	} = $props();

	let containerEl: HTMLDivElement | null = $state(null);

	export function getContainerElement(): HTMLDivElement | null {
		return containerEl;
	}
</script>

<div class="dropdown dropdown-end" bind:this={containerEl}>
	<button
		type="button"
		class="btn btn-outline btn-sm gap-2 font-sans"
		onclick={(e) => {
			e.stopPropagation();
			onToggleDropdown();
		}}
	>
		<span
			class="status status-sm {online
				? 'status-success shadow-[0_0_8px_var(--color-success)]'
				: 'status-error'}"
		></span>
		{displayName}
		<svg
			class="w-3 h-3 opacity-50 block transition-transform {dropdownOpen ? 'rotate-180' : ''}"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="2"><polyline points="6 9 12 15 18 9" /></svg
		>
	</button>

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
							class="font-sans text-sm rounded-lg hover:bg-muted {t.id === terminalId
								? 'bg-muted text-accent font-bold'
								: ''}"
							onclick={(e) => {
								e.stopPropagation();
								onSelectTerminal(t);
							}}
						>
							{t.displayName}
						</button>
					</li>
				{/each}
			{/if}
		</ul>
	{/if}
</div>
