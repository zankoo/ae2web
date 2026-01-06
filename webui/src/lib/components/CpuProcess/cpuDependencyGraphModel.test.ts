import { describe, expect, it } from 'vitest';

import type { CpuProcessEntryDto, CpuProcessPatternDto, GenericStackDto } from '$lib/api/types';

import { buildCpuDependencyGraph } from './cpuDependencyGraphModel';

function item(id: string, iconKey?: string): GenericStackDto {
	return {
		id,
		iconKey: iconKey ?? `${id}@h1_test`,
		name: id,
		translationKey: '',
		amount: 1,
		type: 'item'
	};
}

describe('buildCpuDependencyGraph', () => {
	it('builds a simple input → output DAG', () => {
		const output = item('minecraft:crafting_table', 'o');
		const plank = item('minecraft:oak_planks', 'p');
		const log = item('minecraft:oak_log', 'l');

		const patterns: CpuProcessPatternDto[] = [
			{
				patternId: 'pat-1',
				remainingRuns: 1,
				outputs: [output],
				inputs: [
					{ multiplier: 2, choices: [plank] },
					{ multiplier: 1, choices: [log] }
				]
			}
		];

		const graph = buildCpuDependencyGraph({
			root: output,
			patterns,
			entries: []
		});

		expect(graph.nodes.map((n) => n.id).sort()).toEqual(['l', 'o', 'p']);
		expect(graph.edges.map((e) => `${e.source}->${e.target}`).sort()).toEqual(['l->o', 'p->o']);
	});

	it('prefers a substitute choice when the primary has no job activity', () => {
		const output = item('minecraft:torch', 'torch');
		const primary = item('minecraft:coal', 'coal');
		const substitute = item('minecraft:charcoal', 'charcoal');

		const patterns: CpuProcessPatternDto[] = [
			{
				patternId: 'pat-1',
				remainingRuns: 1,
				outputs: [output],
				inputs: [{ multiplier: 1, choices: [primary, substitute] }]
			}
		];

		const entries: CpuProcessEntryDto[] = [{ stack: substitute, stored: 0, waiting: 1, active: 0 }];

		const graph = buildCpuDependencyGraph({
			root: output,
			patterns,
			entries,
			options: { inferChoiceFromEntries: true }
		});

		expect(graph.edges).toHaveLength(1);
		expect(graph.edges[0]?.source).toBe('charcoal');
		expect(graph.edges[0]?.target).toBe('torch');
		expect(graph.edges[0]?.kind).toBe('substitute');
	});

	it('includes all possible input choices when showSubstitutes=true', () => {
		const output = item('minecraft:torch', 'torch');
		const primary = item('minecraft:coal', 'coal');
		const substitute = item('minecraft:charcoal', 'charcoal');

		const patterns: CpuProcessPatternDto[] = [
			{
				patternId: 'pat-1',
				remainingRuns: 1,
				outputs: [output],
				inputs: [{ multiplier: 1, choices: [primary, substitute] }]
			}
		];

		const graph = buildCpuDependencyGraph({
			root: output,
			patterns,
			entries: [],
			options: { showSubstitutes: true }
		});

		expect(graph.edges.map((e) => `${e.kind}:${e.source}->${e.target}`).sort()).toEqual([
			'primary:coal->torch',
			'substitute:charcoal->torch'
		]);
	});
});
