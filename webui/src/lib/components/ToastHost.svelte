<script lang="ts">
	import { toastStore } from '$lib/stores/toast';
</script>

<div id="toast-container">
	{#each $toastStore as toast (toast.id)}
		<div class={`toast ${toast.type}`}>{toast.message}</div>
	{/each}
</div>

