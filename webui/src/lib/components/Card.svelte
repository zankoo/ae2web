<script lang="ts">
	export let variant: 'default' | 'elevated' | 'featured' = 'default';
	export let className: string = '';
	export let padding: 'none' | 'sm' | 'md' | 'lg' = 'md';

	const baseClasses = 'rounded-xl border border-border bg-card transition-all duration-300';

	const variantClasses = {
		default: 'shadow-sm hover:shadow-md',
		elevated: 'shadow-md hover:shadow-xl hover:-translate-y-1',
		featured: 'relative' // Handled via wrapper in slot usually, but here we can add specific styles
	};

	const paddingClasses = {
		none: '',
		sm: 'p-4',
		md: 'p-6',
		lg: 'p-8'
	};
</script>

{#if variant === 'featured'}
	<div
		class="rounded-xl bg-gradient-to-br from-[var(--color-accent)] via-[var(--color-accent-secondary)] to-[var(--color-accent)] p-[2px] {className}"
	>
		<div class="h-full w-full rounded-[calc(0.75rem-2px)] bg-card {paddingClasses[padding]}">
			<slot />
		</div>
	</div>
{:else}
	<div
		class="{baseClasses} {variantClasses[variant]} {paddingClasses[padding]} {className}"
		{...$$restProps}
	>
		<slot />
	</div>
{/if}
