<script lang="ts">
	let {
		open = false,
		error = null,
		onLogin,
		onClear
	}: {
		open?: boolean;
		error?: string | null;
		onLogin: (token: string) => void;
		onClear: () => void;
	} = $props();

	let tokenValue = $state('');

	function handleKeydown(e: KeyboardEvent): void {
		if (e.key === 'Enter') {
			e.preventDefault();
			onLogin(tokenValue);
		}
	}

	function handleLogin(): void {
		onLogin(tokenValue);
	}
</script>

<dialog class="modal" class:modal-open={open}>
	<div class="modal-box max-w-md p-0 bg-card border border-border/50 shadow-2xl rounded-2xl">
		<div class="flex items-center gap-3 px-6 py-4 border-b border-border bg-muted/40">
			<div class="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
				<svg class="w-6 h-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<path
						d="M12 2a7 7 0 0 0-7 7v4a2 2 0 0 0 2 2h1v3a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-3h1a2 2 0 0 0 2-2V9a7 7 0 0 0-7-7z"
					/>
					<path d="M9 11h6" />
				</svg>
			</div>
			<h3 class="font-bold text-lg">Enter Token</h3>
		</div>

		<div class="p-6 space-y-6">
			<p class="text-sm text-muted-foreground leading-relaxed">
				To access this terminal, please obtain your personal security token from the game server.
				<br /><br />
				<span class="block text-xs font-bold uppercase tracking-wider text-muted-foreground mb-1"
					>How to get token:</span
				>
				<span class="block p-2 bg-muted/50 rounded border border-border text-xs">
					Right-click the Web Terminal block<br />OR run
					<code class="bg-black/10 px-1 rounded font-mono">/ae2web token</code>
				</span>
			</p>

			<input
				type="password"
				class="input input-bordered w-full font-mono bg-card focus:border-accent"
				placeholder="Paste token here..."
				autocomplete="off"
				autocapitalize="off"
				spellcheck="false"
				bind:value={tokenValue}
				onkeydown={handleKeydown}
			/>

			{#if error}
				<div class="alert alert-error py-2 rounded-lg text-sm">
					<span>{error}</span>
				</div>
			{/if}
		</div>

		<div class="flex justify-end gap-3 px-6 py-4 border-t border-border bg-muted/20">
			<button type="button" class="btn btn-ghost" onclick={onClear}>Clear</button>
			<button type="button" class="btn btn-primary" onclick={handleLogin}>
				Connect
				<svg
					class="w-4 h-4 ml-2"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
				>
					<path d="M5 12h14M12 5l7 7-7 7" />
				</svg>
			</button>
		</div>
	</div>
</dialog>
