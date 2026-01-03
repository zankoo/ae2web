<script lang="ts">
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

	export let root: GenericStackDto | undefined;
	export let patterns: CpuProcessPatternDto[] = [];
	export let entries: CpuProcessEntryDto[] = [];

	export let maxDepth = 6;
	export let maxNodes = 500;
	export let maxPatternsPerOutput = 2;
	export let maxChoicesPerInput = 4;

	let patternsByOutput = new Map<string, CpuProcessPatternDto[]>();
	let entryByIconKey = new Map<string, Counts>();
	let tree: DepNode | null = null;

	$: {
		patternsByOutput = new Map();
		for (const p of patterns ?? []) {
			for (const out of p?.outputs ?? []) {
				const key = out?.iconKey;
				if (!key) continue;
				const list = patternsByOutput.get(key) ?? [];
				list.push(p);
				patternsByOutput.set(key, list);
			}
		}
	}

	$: {
		entryByIconKey = new Map();
		for (const e of entries ?? []) {
			const key = e?.stack?.iconKey;
			if (!key) continue;
			entryByIconKey.set(key, {
				stored: Number(e.stored ?? 0),
				waiting: Number(e.waiting ?? 0),
				active: Number(e.active ?? 0)
			});
		}
	}

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

	$: tree = buildTree();
</script>

{#if !tree}
	<div class="cpu-dep-empty">No dependency data.</div>
{:else}
	<CpuDependencyTreeNode node={tree} depth={0} {entryByIconKey} />
{/if}

<style>
	.cpu-dep-empty {
		color: var(--ae2-text-dim);
		padding: 14px;
		border: 1px dashed rgba(255, 255, 255, 0.25);
		border-radius: 10px;
		background: rgba(0, 0, 0, 0.15);
		font-family: var(--font-mono);
	}
</style>
