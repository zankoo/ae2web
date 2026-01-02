<script lang="ts">
	import '../app.css';

	import '@fontsource/inter/latin-400.css';
	import '@fontsource/inter/latin-600.css';
	import '@fontsource/inter/latin-700.css';
	import '@fontsource/jetbrains-mono/latin-500.css';

	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>AE2 Web Terminal</title>
</svelte:head>

{@render children()}
