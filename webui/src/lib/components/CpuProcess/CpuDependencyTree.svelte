<script lang="ts">
	import { SvelteMap } from 'svelte/reactivity';

	import type { CpuProcessEntryDto, CpuProcessPatternDto, GenericStackDto } from '$lib/api/types';
	import CpuDependencyTreeNode from '$lib/components/CpuProcess/CpuDependencyTreeNode.svelte';

	type Counts = { stored: number; waiting: number; active: number };
	type NodeKind = 'stack' | 'pattern' | 'input';

	type DepNode = {
		kind: NodeKind;
		id: string;
		stack?: GenericStackDto;
		remainingRuns?: number;
		multiplier?: number;
		cycle?: boolean;
		truncated?: boolean;
		children: DepNode[];
	};

	let {
		root = undefined,
		patterns = [],
		entries = [],
		maxDepth = 6,
		maxNodes = 500,
		maxPatternsPerOutput = 2,
		maxChoicesPerInput = 4
	}: {
		root?: GenericStackDto;
		patterns?: CpuProcessPatternDto[];
		entries?: CpuProcessEntryDto[];
		maxDepth?: number;
		maxNodes?: number;
		maxPatternsPerOutput?: number;
		maxChoicesPerInput?: number;
	} = $props();

	let patternsByOutput = $derived.by(() => {
		const map = new SvelteMap<string, CpuProcessPatternDto[]>();
		for (const p of patterns ?? []) {
			for (const out of p?.outputs ?? []) {
				const key = out?.iconKey;
				if (!key) continue;
				const list = map.get(key) ?? [];
				list.push(p);
				map.set(key, list);
			}
		}
		return map;
	});

	let entryByIconKey = $derived.by(() => {
		const map = new SvelteMap<string, Counts>();
		for (const e of entries ?? []) {
			const key = e?.stack?.iconKey;
			if (!key) continue;
			map.set(key, {
				stored: Number(e.stored ?? 0),
				waiting: Number(e.waiting ?? 0),
				active: Number(e.active ?? 0)
			});
		}
		return map;
	});

	function buildTree(): DepNode | null {
		if (!root?.iconKey) return null;
		const budget = { remaining: maxNodes };
		return buildStackNode(root, 0, new Set<string>(), budget);
	}

	function buildStackNode(
		stack: GenericStackDto,
		stackDepth: number,
		path: Set<string>,
		budget: { remaining: number }
	): DepNode {
		const id = `stack:${stack.iconKey}:${stackDepth}:${budget.remaining}`;

		if (budget.remaining <= 0) {
			return { kind: 'stack', id, stack, truncated: true, children: [] };
		}
		budget.remaining -= 1;

		if (path.has(stack.iconKey)) {
			return { kind: 'stack', id, stack, cycle: true, children: [] };
		}
		if (stackDepth >= maxDepth) {
			return { kind: 'stack', id, stack, truncated: true, children: [] };
		}

		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- temp variable in build function
		const nextPath = new Set(path);
		nextPath.add(stack.iconKey);

		const producing = patternsByOutput.get(stack.iconKey) ?? [];
		const patternsToUse = producing.slice(0, maxPatternsPerOutput);
		const children: DepNode[] = [];

		for (let pIndex = 0; pIndex < patternsToUse.length; pIndex++) {
			const pattern = patternsToUse[pIndex];
			const patternId = pattern?.patternId ?? `p-${pIndex}`;

			const pNode: DepNode = {
				kind: 'pattern',
				id: `pattern:${patternId}:${stack.iconKey}:${stackDepth}:${pIndex}`,
				remainingRuns: Number(pattern?.remainingRuns ?? 0),
				children: []
			};

			const inputs = Array.isArray(pattern?.inputs) ? pattern.inputs : [];
			for (let i = 0; i < inputs.length; i++) {
				const input = inputs[i];
				const multiplier = Number(input?.multiplier ?? 1);

				const iNode: DepNode = {
					kind: 'input',
					id: `input:${patternId}:${i}:${stackDepth}`,
					multiplier,
					children: []
				};

				const choices = Array.isArray(input?.choices) ? input.choices : [];
				// eslint-disable-next-line svelte/prefer-svelte-reactivity -- temp variable in build function
				const seen = new Set<string>();
				for (const choice of choices) {
					if (!choice?.iconKey) continue;
					if (seen.has(choice.iconKey)) continue;
					seen.add(choice.iconKey);
					if (iNode.children.length >= maxChoicesPerInput) break;
					iNode.children.push(buildStackNode(choice, stackDepth + 1, nextPath, budget));
					if (budget.remaining <= 0) break;
				}

				if (iNode.children.length > 0) {
					pNode.children.push(iNode);
				}
				if (budget.remaining <= 0) break;
			}

			if (pNode.children.length > 0) {
				children.push(pNode);
			}
			if (budget.remaining <= 0) break;
		}

		return { kind: 'stack', id, stack, children };
	}

	// Rebuild tree when root, patterns, or entries change
	let tree = $derived(root && patterns ? buildTree() : null);
</script>

{#if !tree}
	<div
		class="text-base-content/60 p-3.5 border border-dashed border-base-300 rounded-xl bg-base-200 font-mono text-center"
	>
		No dependency data.
	</div>
{:else}
	<CpuDependencyTreeNode node={tree} depth={0} {entryByIconKey} />
{/if}
