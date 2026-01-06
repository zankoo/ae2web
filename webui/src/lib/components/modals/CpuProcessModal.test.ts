import { render } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

import type { CpuProcessResponse } from '$lib/api/types';

import CpuProcessModal from './CpuProcessModal.svelte';

describe('CpuProcessModal', () => {
	it('shows a success alert when crafting completes', async () => {
		const dataBusy: CpuProcessResponse = {
			cpuId: 'cpu-1',
			busy: true,
			name: 'CPU Alpha',
			summary: { elapsedTimeNanos: 1, outputTotal: 64, outputDone: 32, outputRemaining: 32 },
			entries: [],
			patterns: []
		};

		const { queryByText, getByText, rerender, unmount } = render(CpuProcessModal, {
			props: {
				open: true,
				cpuName: 'CPU Alpha',
				autoRefresh: true,
				loading: false,
				error: null,
				data: dataBusy,
				onClose: vi.fn(),
				onRefresh: vi.fn(),
				onAutoRefreshChange: vi.fn()
			}
		});

		try {
			expect(queryByText('CPU is no longer busy.')).toBeNull();

			await rerender({
				data: {
					...dataBusy,
					busy: false,
					summary: { elapsedTimeNanos: 1, outputTotal: 64, outputDone: 64, outputRemaining: 0 }
				}
			});

			const msg = getByText('Crafting completed.');
			const alert = msg.closest('.alert');
			expect(alert).not.toBeNull();
			expect(alert).toHaveClass('alert-success');
			expect(queryByText('CPU is no longer busy.')).toBeNull();
		} finally {
			unmount();
		}
	});

	it('renders fluid progress amounts in buckets (B)', () => {
		const dataBusy: CpuProcessResponse = {
			cpuId: 'cpu-1',
			busy: true,
			name: 'CPU Alpha',
			summary: {
				finalOutput: {
					type: 'fluid',
					id: 'minecraft:water',
					iconKey: 'minecraft:water@h1_test',
					name: 'Water',
					translationKey: 'fluid.minecraft.water',
					amount: 2000
				},
				elapsedTimeNanos: 1,
				outputTotal: 2000,
				outputDone: 1000,
				outputRemaining: 1000
			},
			entries: [],
			patterns: []
		};

		const { getByText, unmount } = render(CpuProcessModal, {
			props: {
				open: true,
				cpuName: 'CPU Alpha',
				autoRefresh: true,
				loading: false,
				error: null,
				data: dataBusy,
				onClose: vi.fn(),
				onRefresh: vi.fn(),
				onAutoRefreshChange: vi.fn()
			}
		});

		try {
			getByText('1B / 2B');
		} finally {
			unmount();
		}
	});
});
