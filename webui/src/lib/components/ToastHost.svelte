<script lang="ts">
	import { toastStore } from '$lib/stores/toast';
</script>

{#if $toastStore.length > 0}
	<div class="toast toast-top toast-end z-50 gap-2">
		{#each $toastStore as toast (toast.id)}
			<div
				class="alert {toast.type === 'error'
					? 'alert-error'
					: toast.type === 'success'
						? 'alert-success'
						: 'alert-info'} shadow-lg animate-slide-up"
			>
				<span>{toast.message}</span>
			</div>
		{/each}
	</div>
{/if}
