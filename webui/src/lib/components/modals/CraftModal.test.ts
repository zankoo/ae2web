import { fireEvent, render } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import type { GenericStackDto, InventoryItem } from '$lib/api/types';

import CraftModal from './CraftModal.svelte';

describe('CraftModal (missing items)', () => {
	it('shows missing items with amounts and supports filtering', async () => {
		const item: InventoryItem = {
			type: 'item',
			id: 'minecraft:diamond_sword',
			uniqueId: 'item:minecraft:diamond_sword',
			iconKey: 'minecraft:diamond_sword@h1_test',
			name: 'Diamond Sword',
			translationKey: '',
			namespace: 'minecraft',
			amount: 1,
			craftable: true
		};

		const missingItems: GenericStackDto[] = [
			{
				type: 'item',
				id: 'minecraft:stick',
				iconKey: 'minecraft:stick@h1_test',
				name: 'Stick',
				translationKey: '',
				amount: 4
			},
			{
				type: 'fluid',
				id: 'minecraft:water',
				iconKey: 'minecraft:water@h1_test',
				name: 'Water',
				translationKey: '',
				amount: 1000
			},
			{
				type: 'item',
				id: 'minecraft:diamond',
				iconKey: 'minecraft:diamond@h1_test',
				name: 'Diamond',
				translationKey: '',
				amount: 2
			}
		];

		const { getByText, getByLabelText, queryByText, container, unmount } = render(CraftModal, {
			props: {
				open: true,
				item,
				cpus: [],
				missingItems,
				onCraft: vi.fn(),
				onRetry: vi.fn(),
				onClose: vi.fn()
			}
		});

		try {
			getByText('Missing Items');
			getByText('Stick');
			getByText('Diamond');
			getByText('Water');
			getByText('x4');
			getByText('x2');
			getByText('1B');

			const filter = getByLabelText('Filter missing items') as HTMLInputElement;
			await fireEvent.input(filter, { target: { value: 'stick' } });
			expect(queryByText('Stick')).not.toBeNull();
			expect(queryByText('Diamond')).toBeNull();
			expect(queryByText('Water')).toBeNull();

			const backdrop = container.querySelector('form.modal-backdrop');
			expect(backdrop).not.toBeNull();
			expect(backdrop).not.toHaveClass('backdrop-blur-sm');
		} finally {
			unmount();
		}
	});
});
