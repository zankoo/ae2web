import type { CpuProcessEntryDto, CpuProcessPatternDto, GenericStackDto } from '$lib/api/types';

export type CpuCounts = { stored: number; waiting: number; active: number; completed?: boolean };

export type CpuDependencyGraphNode = {
	id: string;
	stack: GenericStackDto;
	counts: CpuCounts | null;
	isRoot: boolean;
};

export type CpuDependencyGraphEdgeKind = 'primary' | 'substitute';

export type CpuDependencyGraphEdge = {
	id: string;
	source: string;
	target: string;
	kind: CpuDependencyGraphEdgeKind;
	multiplier: number;
	patternId: string;
	inputIndex: number;
};

export type CpuDependencyGraph = {
	nodes: CpuDependencyGraphNode[];
	edges: CpuDependencyGraphEdge[];
	truncated: boolean;
};

export type BuildCpuDependencyGraphOptions = {
	maxNodes?: number;
	maxDepth?: number;
	maxPatternsPerOutput?: number;
	showSubstitutes?: boolean;
	inferChoiceFromEntries?: boolean;
};

type BuildArgs = {
	root: GenericStackDto | undefined;
	patterns?: CpuProcessPatternDto[];
	entries?: CpuProcessEntryDto[];
	options?: BuildCpuDependencyGraphOptions;
};

type ChoicePick = { choice: GenericStackDto; index: number } | null;

function normalizeCounts(entry: CpuProcessEntryDto): CpuCounts {
	return {
		stored: Number(entry.stored ?? 0),
		waiting: Number(entry.waiting ?? 0),
		active: Number(entry.active ?? 0),
		completed: entry.completed
	};
}

function pickChoice(
	choices: GenericStackDto[],
	countsByIconKey: Map<string, CpuCounts>,
	inferChoiceFromEntries: boolean
): ChoicePick {
	const usable: GenericStackDto[] = [];
	for (const c of choices ?? []) {
		if (c?.iconKey) usable.push(c);
	}
	if (usable.length === 0) return null;
	if (!inferChoiceFromEntries) return { choice: usable[0], index: 0 };

	let bestIndex = -1;
	let bestScore = -1;
	for (let i = 0; i < usable.length; i++) {
		const key = usable[i]?.iconKey;
		if (!key) continue;
		const counts = countsByIconKey.get(key);
		if (!counts) continue;
		const hasWork = counts.waiting > 0 || counts.active > 0;
		const score =
			(hasWork ? 1_000_000_000 : 0) +
			counts.waiting * 1_000_000 +
			counts.active * 1_000 +
			counts.stored;
		if (score > bestScore) {
			bestScore = score;
			bestIndex = i;
		}
	}

	if (bestIndex >= 0) return { choice: usable[bestIndex], index: bestIndex };
	return { choice: usable[0], index: 0 };
}

function ensureStack(map: Map<string, GenericStackDto>, stack: GenericStackDto | undefined): void {
	if (!stack?.iconKey) return;
	if (!map.has(stack.iconKey)) {
		map.set(stack.iconKey, stack);
	}
}

function stackFallback(iconKey: string): GenericStackDto {
	return {
		id: iconKey,
		iconKey,
		name: iconKey,
		translationKey: '',
		amount: 1
	};
}

export function buildCpuDependencyGraph({
	root,
	patterns,
	entries,
	options
}: BuildArgs): CpuDependencyGraph {
	const {
		maxNodes = 500,
		maxDepth = 10,
		maxPatternsPerOutput = 2,
		showSubstitutes = false,
		inferChoiceFromEntries = false
	} = options ?? {};

	const rootKey = root?.iconKey;
	if (!rootKey) return { nodes: [], edges: [], truncated: false };

	const patternsByOutput = new Map<string, CpuProcessPatternDto[]>();
	for (const p of patterns ?? []) {
		for (const out of p?.outputs ?? []) {
			if (!out?.iconKey) continue;
			const list = patternsByOutput.get(out.iconKey) ?? [];
			list.push(p);
			patternsByOutput.set(out.iconKey, list);
		}
	}

	const countsByIconKey = new Map<string, CpuCounts>();
	const stackByIconKey = new Map<string, GenericStackDto>();
	ensureStack(stackByIconKey, root);

	for (const e of entries ?? []) {
		const key = e?.stack?.iconKey;
		if (!key) continue;
		countsByIconKey.set(key, normalizeCounts(e));
		ensureStack(stackByIconKey, e.stack);
	}

	for (const p of patterns ?? []) {
		for (const out of p?.outputs ?? []) ensureStack(stackByIconKey, out);
		for (const input of p?.inputs ?? []) {
			for (const choice of input?.choices ?? []) ensureStack(stackByIconKey, choice);
		}
	}

	const nodes = new Map<string, CpuDependencyGraphNode>();
	const edges = new Map<string, CpuDependencyGraphEdge>();
	const expanded = new Set<string>();
	const inPath = new Set<string>();
	let truncated = false;

	function addNode(iconKey: string): void {
		if (!iconKey) return;
		if (nodes.has(iconKey)) return;
		if (nodes.size >= maxNodes) {
			truncated = true;
			return;
		}
		nodes.set(iconKey, {
			id: iconKey,
			stack: stackByIconKey.get(iconKey) ?? stackFallback(iconKey),
			counts: countsByIconKey.get(iconKey) ?? null,
			isRoot: iconKey === rootKey
		});
	}

	function addEdge(
		source: string,
		target: string,
		kind: CpuDependencyGraphEdgeKind,
		multiplier: number,
		patternId: string,
		inputIndex: number
	): void {
		// Skip if already truncated
		if (truncated) return;

		addNode(source);
		addNode(target);

		// Verify both nodes exist (they may have failed to add due to truncation)
		if (!nodes.has(source) || !nodes.has(target)) return;

		const id = `e:${source}->${target}:${patternId}:${inputIndex}:${kind}`;
		if (edges.has(id)) return;
		edges.set(id, {
			id,
			source,
			target,
			kind,
			multiplier: Number(multiplier ?? 1),
			patternId,
			inputIndex
		});
	}

	function visit(outputKey: string, depth: number): void {
		if (truncated) return;
		addNode(outputKey);

		if (depth >= maxDepth) {
			truncated = true;
			return;
		}

		if (expanded.has(outputKey)) return;
		if (inPath.has(outputKey)) return;
		inPath.add(outputKey);

		const producing = patternsByOutput.get(outputKey) ?? [];
		const patternsToUse = producing.slice(0, maxPatternsPerOutput);

		for (const p of patternsToUse) {
			const patternId = p?.patternId ?? 'pattern';
			const inputs = Array.isArray(p?.inputs) ? p.inputs : [];
			for (let inputIndex = 0; inputIndex < inputs.length; inputIndex++) {
				const input = inputs[inputIndex];
				const multiplier = Number(input?.multiplier ?? 1);
				const choices = Array.isArray(input?.choices) ? input.choices : [];

				if (showSubstitutes) {
					for (let i = 0; i < choices.length; i++) {
						const choice = choices[i];
						const key = choice?.iconKey;
						if (!key) continue;
						addEdge(
							key,
							outputKey,
							i === 0 ? 'primary' : 'substitute',
							multiplier,
							patternId,
							inputIndex
						);
						visit(key, depth + 1);
						if (truncated) break;
					}
					continue;
				}

				const picked = pickChoice(choices, countsByIconKey, inferChoiceFromEntries);
				if (!picked?.choice?.iconKey) continue;
				const kind = picked.index === 0 ? 'primary' : 'substitute';
				addEdge(picked.choice.iconKey, outputKey, kind, multiplier, patternId, inputIndex);
				visit(picked.choice.iconKey, depth + 1);
				if (truncated) break;
			}
			if (truncated) break;
		}

		inPath.delete(outputKey);
		expanded.add(outputKey);
	}

	visit(rootKey, 0);

	return {
		nodes: Array.from(nodes.values()),
		edges: Array.from(edges.values()),
		truncated
	};
}
