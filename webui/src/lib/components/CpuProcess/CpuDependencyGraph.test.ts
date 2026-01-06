import { fireEvent, render } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';

import type { CpuProcessEntryDto, CpuProcessPatternDto, GenericStackDto } from '$lib/api/types';

import CpuDependencyGraph from './CpuDependencyGraph.svelte';

function stack(
	id: string,
	iconKey: string,
	name: string,
	type: 'item' | 'fluid' = 'item'
): GenericStackDto {
	return { id, iconKey, name, translationKey: '', amount: 1, type };
}

describe('CpuDependencyGraph details panel layout', () => {
	it('keeps pattern runs badge on one line and prevents choice rows from overflowing', async () => {
		const root = stack('mod:target', 'root', 'Target');
		const longChoice = stack(
			'mod:this_is_a_very_long_item_id_that_should_truncate_in_the_details_panel',
			'long-choice',
			'Very Long Input Name'
		);

		const patterns: CpuProcessPatternDto[] = [
			{
				patternId: 'ae2:processing_pattern',
				remainingRuns: 1,
				outputs: [root],
				inputs: [{ multiplier: 1, choices: [longChoice] }]
			}
		];

		const entries: CpuProcessEntryDto[] = [
			{ stack: longChoice, stored: 123, waiting: 45, active: 6 }
		];

		const { container, findByText, getByText, unmount } = render(CpuDependencyGraph, {
			props: { root, patterns, entries }
		});

		try {
			// Ensure the node is rendered, then click it to open details.
			const nodeEl = await findByText('Target');
			await fireEvent.click(nodeEl);

			await findByText('Producing patterns (1)');

			// Pattern header should truncate the id and keep "runs: 1" as a non-wrapping badge.
			const runs = getByText('runs: 1');
			expect(runs.closest('.badge')).toHaveClass('whitespace-nowrap');

			// Choice rows should allow wrapping of the right-side SAW badges instead of overflowing.
			const choiceRow = container.querySelector('[data-cpu-graph-choice="long-choice"]');
			expect(choiceRow).not.toBeNull();
			expect(choiceRow).toHaveClass('flex-wrap');
		} finally {
			unmount();
		}
	});
});
