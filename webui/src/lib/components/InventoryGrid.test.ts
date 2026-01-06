import { render } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import type { InventoryItem } from '$lib/api/types';
import type { InventoryRenderNode } from '$lib/utils/inventoryGrouping';

import InventoryGrid from './InventoryGrid.svelte';

describe('InventoryGrid', () => {
	it('renders craftable indicator as blue dot', () => {
		const item: InventoryItem = {
			type: 'item',
			id: 'minecraft:stone',
			uniqueId: 'item:minecraft:stone',
			iconKey: 'minecraft:stone@h1_test',
			name: 'Stone',
			translationKey: '',
			namespace: 'minecraft',
			amount: 1,
			craftable: true,
			tags: []
		};

		const renderNodes: InventoryRenderNode[] = [{ kind: 'item', key: item.uniqueId, item }];

		const { container, unmount } = render(InventoryGrid, {
			props: {
				renderNodes,
				onItemClick: vi.fn(),
				onToggleSection: vi.fn(),
				onMouseOver: vi.fn(),
				onMouseMove: vi.fn(),
				onMouseOut: vi.fn(),
				onScroll: vi.fn()
			}
		});

		try {
			const slot = container.querySelector(
				`[data-item-id="${item.uniqueId}"]`
			) as HTMLElement | null;
			expect(slot).not.toBeNull();

			const indicator = slot?.querySelector('.indicator-item') as HTMLElement | null;
			expect(indicator).not.toBeNull();

			expect(indicator?.classList.contains('status-info')).toBe(true);
		} finally {
			unmount();
		}
	});
});
