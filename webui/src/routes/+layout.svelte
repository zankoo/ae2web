<script lang="ts">
	import '../app.css';

	// Keep JetBrains Mono for monospace (local font)
	import '@fontsource/jetbrains-mono/latin-500.css';

	import { onMount, onDestroy } from 'svelte';
	import favicon from '$lib/assets/favicon.svg';
	import { authToken } from '$lib/stores/session';
	import {
		startPolling,
		stopPolling,
		resetTerminalState,
		closeTerminalDropdown,
		terminalDropdownOpen
	} from '$lib/stores/terminal';

	const { children } = $props();

	let unsubscribeAuth: (() => void) | null = null;

	onMount(() => {
		// Start/stop polling based on auth token
		unsubscribeAuth = authToken.subscribe((token) => {
			const hasToken = Boolean(String(token ?? '').trim());
			if (hasToken) {
				startPolling();
			} else {
				resetTerminalState();
			}
		});

		// Close dropdown on click outside or Escape
		const handleDocClick = () => {
			if ($terminalDropdownOpen) {
				closeTerminalDropdown();
			}
		};

		const handleKeyDown = (e: KeyboardEvent) => {
			if (e.key === 'Escape') {
				closeTerminalDropdown();
			}
		};

		document.addEventListener('click', handleDocClick);
		document.addEventListener('keydown', handleKeyDown);

		return () => {
			document.removeEventListener('click', handleDocClick);
			document.removeEventListener('keydown', handleKeyDown);
		};
	});

	onDestroy(() => {
		unsubscribeAuth?.();
		stopPolling();
	});
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>AE2 Web Terminal</title>
</svelte:head>

<div class="min-h-screen bg-base-100">
	{@render children()}
</div>
