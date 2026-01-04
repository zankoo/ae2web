<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import { arrayMove } from '$lib/utils/arrayMove';

	export let open: boolean = false;
	export let terminalName: string | null = null;
	export let terminalId: string | null = null;
	export let saving: boolean = false;
	export let error: string | null = null;
	export let whitelistText: string = '';
	export let blacklistText: string = '';
	export let categories: CategoryDraft[] = [];
	export let onClose: () => void;
	export let onSave: (whitelist: string, blacklist: string, categories: CategoryDraft[]) => void;
	export let onWhitelistChange: (value: string) => void;
	export let onBlacklistChange: (value: string) => void;
	export let onCategoriesChange: (categories: CategoryDraft[]) => void;

	export type CategoryDraft = { id: string; name: string; tagRegex: string };

	let categoryIdSeq = 0;
	let dragId: string | null = null;
	let dragOverId: string | null = null;

	function createCategoryDraft(rule?: { name?: string; tagRegex?: string }): CategoryDraft {
		categoryIdSeq += 1;
		return {
			id: `cat_${categoryIdSeq}`,
			name: typeof rule?.name === 'string' ? rule.name : '',
			tagRegex: typeof rule?.tagRegex === 'string' ? rule.tagRegex : ''
		};
	}

	function clearDrag(): void {
		dragId = null;
		dragOverId = null;
	}

	function onDragStart(e: DragEvent, id: string): void {
		if (!id || saving) return;
		dragId = id;
		dragOverId = id;

		try {
			e.dataTransfer?.setData('text/plain', id);
		} catch {
			// ignore
		}

		if (e.dataTransfer) {
			e.dataTransfer.effectAllowed = 'move';
		}
	}

	function onDragOver(e: DragEvent, overId: string): void {
		if (!dragId || saving) return;
		e.preventDefault();

		if (dragOverId !== overId) {
			dragOverId = overId;
		}

		if (e.dataTransfer) {
			e.dataTransfer.dropEffect = 'move';
		}
	}

	function onDrop(e: DragEvent, dropId: string): void {
		const fromId = dragId;
		if (!fromId || saving) return;
		e.preventDefault();

		const fromIndex = categories.findIndex((x) => x.id === fromId);
		const toIndex = categories.findIndex((x) => x.id === dropId);
		if (fromIndex === -1 || toIndex === -1) {
			clearDrag();
			return;
		}

		const newCategories = arrayMove(categories, fromIndex, toIndex);
		onCategoriesChange(newCategories);
		clearDrag();
	}

	function onDragEnd(): void {
		clearDrag();
	}

	function addCategoryRule(): void {
		onCategoriesChange([...categories, createCategoryDraft()]);
	}

	function removeCategoryRule(id: string): void {
		if (!id) return;
		onCategoriesChange(categories.filter((x) => x.id !== id));
	}

	function handleSave(): void {
		onSave(whitelistText, blacklistText, categories);
	}

	// Reset drag state when modal closes
	$: if (!open) {
		clearDrag();
	}
</script>

<dialog class="modal" class:modal-open={open}>
	<div
		class="modal-box max-w-4xl max-h-[85vh] flex flex-col p-0 bg-card border border-border/50 shadow-2xl rounded-2xl"
	>
		<div class="flex items-center gap-3 px-6 py-4 border-b border-border bg-muted/40">
			<div class="w-10 h-10 rounded-xl bg-accent/10 flex items-center justify-center text-accent">
				<svg
					class="w-5 h-5"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
				>
					<circle cx="12" cy="12" r="3" />
					<path
						d="M19.14 12.94a7.43 7.43 0 0 0 0-1.88l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.28 7.28 0 0 0-1.63-.94l-.36-2.54A.5.5 0 0 0 13.9 1h-3.8a.5.5 0 0 0-.49.42l-.36 2.54a7.28 7.28 0 0 0-1.63.94l-2.39-.96a.5.5 0 0 0-.6.22L2.71 7.48a.5.5 0 0 0 .12.64l2.03 1.58a7.43 7.43 0 0 0 0 1.88l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32a.5.5 0 0 0 .6.22l2.39-.96c.5.39 1.05.72 1.63.94l.36 2.54a.5.5 0 0 0 .49.42h3.8a.5.5 0 0 0 .49-.42l.36-2.54c.58-.22 1.13-.55 1.63-.94l2.39.96a.5.5 0 0 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64l-2.03-1.58z"
					/>
				</svg>
			</div>
			<div class="flex-1">
				<h3 class="font-bold text-lg">Inventory Settings</h3>
				<p class="text-sm text-muted-foreground font-mono">
					{terminalName || terminalId}
				</p>
			</div>
			<Button variant="ghost" size="sm" className="btn-circle" onClick={onClose}>
				<svg
					class="w-4 h-4"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
				>
					<path d="M18 6L6 18M6 6l12 12" />
				</svg>
			</Button>
		</div>

		<div class="flex-1 overflow-auto p-6 space-y-6">
			<!-- Tag Filter Section -->
			<div class="card bg-muted/20 border border-border rounded-xl">
				<div class="card-body p-6">
					<h4 class="font-bold text-xs uppercase tracking-widest text-muted-foreground mb-2">
						Tag Filter
					</h4>
					<p class="text-sm text-muted-foreground mb-4">
						One regex per line. Filters by item tags (e.g. <code
							class="text-xs bg-muted px-1.5 py-0.5 rounded border border-border font-mono"
							>forge:ores/iron</code
						>).
					</p>

					<div class="grid grid-cols-2 gap-6">
						<div>
							<label class="label" for="settings-whitelist"
								><span class="label-text text-sm font-bold">Whitelist (must match)</span></label
							>
							<textarea
								id="settings-whitelist"
								class="textarea textarea-bordered w-full h-40 font-mono text-sm bg-card transition-colors focus:border-accent"
								placeholder="^forge:ores/.*"
								value={whitelistText}
								on:input={(e) => onWhitelistChange((e.target as HTMLTextAreaElement).value)}
								disabled={saving}
							></textarea>
						</div>
						<div>
							<label class="label" for="settings-blacklist"
								><span class="label-text text-sm font-bold">Blacklist (exclude)</span></label
							>
							<textarea
								id="settings-blacklist"
								class="textarea textarea-bordered w-full h-40 font-mono text-sm bg-card transition-colors focus:border-accent"
								placeholder="uranium|plutonium"
								value={blacklistText}
								on:input={(e) => onBlacklistChange((e.target as HTMLTextAreaElement).value)}
								disabled={saving}
							></textarea>
						</div>
					</div>
				</div>
			</div>

			<!-- Categories Section -->
			<div class="card bg-muted/20 border border-border rounded-xl">
				<div class="card-body p-6">
					<h4 class="font-bold text-xs uppercase tracking-widest text-muted-foreground mb-2">
						Categories
					</h4>
					<p class="text-sm text-muted-foreground mb-4">First match wins (top to bottom).</p>

					{#if categories.length === 0}
						<div
							class="text-center py-8 text-muted-foreground/50 border-2 border-dashed border-border/50 rounded-xl"
						>
							No categories configured.
						</div>
					{:else}
						<div class="space-y-3" role="list">
							{#each categories as rule (rule.id)}
								<!-- svelte-ignore a11y_no_static_element_interactions -->
								<div
									class="flex items-center gap-3 p-3 bg-card border border-border rounded-lg shadow-sm group"
									class:ring-2={dragOverId === rule.id}
									class:ring-accent={dragOverId === rule.id}
									class:opacity-50={dragId === rule.id}
									role="listitem"
									on:dragover={(e) => onDragOver(e, rule.id)}
									on:drop={(e) => onDrop(e, rule.id)}
								>
									<button
										class="btn btn-ghost btn-sm btn-square cursor-grab opacity-50 hover:opacity-100"
										aria-label="Drag to reorder"
										draggable={!saving}
										disabled={saving}
										on:dragstart={(e) => onDragStart(e, rule.id)}
										on:dragend={onDragEnd}
									>
										<svg class="w-4 h-4" viewBox="0 0 12 12" fill="currentColor">
											<circle cx="3" cy="3" r="1" /><circle cx="9" cy="3" r="1" />
											<circle cx="3" cy="6" r="1" /><circle cx="9" cy="6" r="1" />
											<circle cx="3" cy="9" r="1" /><circle cx="9" cy="9" r="1" />
										</svg>
									</button>
									<input
										type="text"
										class="input input-sm input-bordered w-48 font-medium focus:border-accent"
										placeholder="Category name"
										bind:value={rule.name}
										disabled={saving}
									/>
									<input
										type="text"
										class="input input-sm input-bordered flex-1 font-mono text-sm focus:border-accent"
										placeholder="Tag regex"
										bind:value={rule.tagRegex}
										disabled={saving}
									/>
									<button
										class="btn btn-ghost btn-sm text-error opacity-0 group-hover:opacity-100 transition-opacity"
										disabled={saving}
										on:click={() => removeCategoryRule(rule.id)}
									>
										Remove
									</button>
								</div>
							{/each}
						</div>
					{/if}

					<Button
						variant="outline"
						size="sm"
						className="mt-4 border-dashed"
						onClick={addCategoryRule}
						disabled={saving}
					>
						+ Add Category
					</Button>
				</div>
			</div>

			{#if error}
				<div class="alert alert-error rounded-xl">
					<span>{error}</span>
				</div>
			{/if}
		</div>

		<div class="flex justify-end gap-3 px-6 py-4 border-t border-border bg-muted/20">
			<Button variant="ghost" onClick={onClose} disabled={saving}>Cancel</Button>
			<Button variant="primary" onClick={handleSave} disabled={saving}>
				{#if saving}
					<span class="loading loading-spinner loading-sm mr-2"></span>
				{/if}
				Save Settings
			</Button>
		</div>
	</div>
	<form method="dialog" class="modal-backdrop bg-black/80 backdrop-blur-sm">
		<button on:click={onClose}>close</button>
	</form>
</dialog>
