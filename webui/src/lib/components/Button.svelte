<script lang="ts">
	export let variant: 'primary' | 'secondary' | 'ghost' | 'outline' = 'primary';
	export let size: 'sm' | 'md' | 'lg' = 'md';
	export let className: string = '';
	export let disabled: boolean = false;
	export let type: 'button' | 'submit' | 'reset' = 'button';
	export let onClick: ((e: MouseEvent) => void) | undefined = undefined;

	const baseClasses =
		'inline-flex items-center justify-center rounded-xl font-medium transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none active:scale-[0.98]';

	const variantClasses = {
		primary:
			'bg-gradient-to-r from-[var(--color-accent)] to-[var(--color-accent-secondary)] text-white shadow-sm hover:shadow-accent hover:-translate-y-0.5 custom-hover-lift',
		secondary: 'bg-muted text-foreground hover:bg-muted/80',
		outline:
			'border border-border bg-transparent hover:bg-muted/10 hover:border-accent/30 text-foreground',
		ghost: 'bg-transparent text-muted-foreground hover:text-foreground hover:bg-muted/10'
	};

	const sizeClasses = {
		sm: 'h-9 px-3 text-xs',
		md: 'h-11 px-5 text-sm',
		lg: 'h-14 px-8 text-base'
	};
</script>

<button
	{type}
	class="{baseClasses} {variantClasses[variant]} {sizeClasses[size]} {className}"
	{disabled}
	on:click={onClick}
	{...$$restProps}
>
	<slot />
</button>

<style>
	/* Custom utility for the specific shadow on hover that isn't in tailwind config yet or needs specific values */
	.custom-hover-lift:hover {
		box-shadow: 0 8px 20px -4px rgba(0, 82, 255, 0.3);
	}
</style>
