import { fireEvent, render } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import InventorySettingsModal, { type CategoryDraft } from './InventorySettingsModal.svelte';

describe('InventorySettingsModal', () => {
	it('adds category rules with unique ids', async () => {
		let categories: CategoryDraft[] = [
			{ id: 'cat_1', name: 'Ores', tagRegex: '^forge:ores/' },
			{ id: 'cat_2', name: 'Dusts', tagRegex: '^forge:dusts/' }
		];

		const onCategoriesChange = (next: CategoryDraft[]) => {
			categories = next;
		};

		const { getByText, unmount } = render(InventorySettingsModal, {
			props: {
				open: true,
				terminalName: 'Terminal 1',
				terminalId: 't1',
				saving: false,
				error: null,
				whitelistText: '',
				blacklistText: '',
				categories,
				onClose: vi.fn(),
				onSave: vi.fn(),
				onWhitelistChange: vi.fn(),
				onBlacklistChange: vi.fn(),
				onCategoriesChange
			}
		});

		try {
			await fireEvent.click(getByText('+ Add Category'));

			expect(categories).toHaveLength(3);
			expect(categories[2]?.id).toBe('cat_3');
		} finally {
			unmount();
		}
	});
});
