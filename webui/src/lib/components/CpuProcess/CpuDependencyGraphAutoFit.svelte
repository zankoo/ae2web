<script lang="ts">
	import { tick } from 'svelte';
	import { useSvelteFlow } from '@xyflow/svelte';

	let {
		signal,
		enabled = true,
		padding = 0.12,
		duration = 150
	}: {
		signal: number;
		enabled?: boolean;
		padding?: number;
		duration?: number;
	} = $props();

	const { fitView } = useSvelteFlow();

	let lastSignal = -1;

	$effect(() => {
		if (enabled && signal !== lastSignal) {
			lastSignal = signal;
			void (async () => {
				await tick();
				await fitView({ padding, duration });
			})();
		}
	});
</script>
