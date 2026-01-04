<script lang="ts">
	import '../app.css';

	// Keep JetBrains Mono for monospace (local font)
	import '@fontsource/jetbrains-mono/latin-500.css';

	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>AE2 Web Terminal</title>
</svelte:head>

<div class="min-h-screen bg-base-100">
	{@render children()}
</div>
