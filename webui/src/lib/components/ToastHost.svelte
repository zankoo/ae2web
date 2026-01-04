<script lang="ts">
	import { toastStore } from '$lib/stores/toast';
</script>

<div class="fixed top-4 right-4 z-50 flex flex-col gap-2">
	{#each $toastStore as toast (toast.id)}
		<div class="toast alert {toast.type === 'error' ? 'alert-error' : toast.type === 'success' ? 'alert-success' : 'alert-info'} shadow-lg animate-slide-up">
			<span>{toast.message}</span>
		</div>
	{/each}
</div>

