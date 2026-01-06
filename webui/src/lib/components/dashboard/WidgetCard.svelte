<script lang="ts">
	import type { WidgetConfig, WidgetType } from '$lib/api/types';

	interface Props {
		config: WidgetConfig;
		onEdit?: () => void;
		onDelete?: () => void;
		children?: import('svelte').Snippet;
	}

	let { config, onEdit, onDelete, children }: Props = $props();

	function getDefaultTitle(type: WidgetType): string {
		switch (type) {
			case 'network-overview':
				return 'Network Overview';
			case 'top-items':
				return 'Top Items';
			case 'tracked-items':
				return 'Tracked Items';
			case 'history-trend':
				return 'History Trend';
			default:
				return 'Widget';
		}
	}

	function getWidgetIcon(type: WidgetType): string {
		switch (type) {
			case 'network-overview':
				return 'M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z';
			case 'top-items':
				return 'M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941';
			case 'tracked-items':
				return 'M11.48 3.499a.562.562 0 011.04 0l2.125 5.111a.563.563 0 00.475.345l5.518.442c.499.04.701.663.321.988l-4.204 3.602a.563.563 0 00-.182.557l1.285 5.385a.562.562 0 01-.84.61l-4.725-2.885a.563.563 0 00-.586 0L6.982 20.54a.562.562 0 01-.84-.61l1.285-5.386a.562.562 0 00-.182-.557l-4.204-3.602a.563.563 0 01.321-.988l5.518-.442a.563.563 0 00.475-.345L11.48 3.5z';
			case 'history-trend':
				return 'M3.75 3v11.25A2.25 2.25 0 006 16.5h2.25M3.75 3h-1.5m1.5 0h16.5m0 0h1.5m-1.5 0v11.25A2.25 2.25 0 0118 16.5h-2.25m-7.5 0h7.5m-7.5 0l-1 3m8.5-3l1 3m0 0l.5 1.5m-.5-1.5h-9.5m0 0l-.5 1.5M9 11.25v1.5M12 9v3.75m3-6v6';
			default:
				return '';
		}
	}
</script>

<div class="widget-card group relative h-full flex flex-col">
	<!-- Header -->
	<div class="widget-header flex items-center gap-2 px-4 py-3 border-b border-border/30">
		<svg
			class="w-4 h-4 text-accent"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="1.5"
		>
			<path stroke-linecap="round" stroke-linejoin="round" d={getWidgetIcon(config.type)} />
		</svg>
		<h3 class="font-display text-sm font-normal text-foreground truncate flex-1">
			{config.title || getDefaultTitle(config.type)}
		</h3>

		<!-- Actions (visible on hover) -->
		<div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
			{#if onEdit}
				<button
					type="button"
					class="btn btn-ghost btn-xs btn-square rounded-lg"
					title="Edit widget"
					onclick={onEdit}
				>
					<svg
						class="w-3.5 h-3.5"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path
							stroke-linecap="round"
							stroke-linejoin="round"
							d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125"
						/>
					</svg>
				</button>
			{/if}
			{#if onDelete}
				<button
					type="button"
					class="btn btn-ghost btn-xs btn-square rounded-lg text-error hover:bg-error/10"
					title="Delete widget"
					onclick={onDelete}
				>
					<svg
						class="w-3.5 h-3.5"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path
							stroke-linecap="round"
							stroke-linejoin="round"
							d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"
						/>
					</svg>
				</button>
			{/if}
		</div>
	</div>

	<!-- Content -->
	<div class="widget-content flex-1 p-4 overflow-auto">
		{#if children}
			{@render children()}
		{/if}
	</div>
</div>

<style>
	.widget-card {
		background: var(--color-card);
		border: 1px solid color-mix(in srgb, var(--color-border) 50%, transparent);
		border-radius: 1rem;
		box-shadow: var(--shadow-md);
		backdrop-filter: blur(8px);
		transition:
			box-shadow 0.2s ease,
			border-color 0.2s ease;
		overflow: hidden;
	}

	.widget-card:hover {
		box-shadow: var(--shadow-lg);
		border-color: var(--color-border);
	}

	.widget-header {
		background: color-mix(in srgb, var(--color-muted) 30%, transparent);
	}
</style>
