import { fireEvent, render } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

function setTestUrl(path: string): void {
	window.history.pushState({}, '', path);
}

vi.mock('$lib/stores/toast', async () => {
	const actual = await vi.importActual<typeof import('$lib/stores/toast')>('$lib/stores/toast');
	return { ...actual, showToast: vi.fn() };
});

vi.mock('$lib/api/terminals', () => ({
	fetchTerminals: vi.fn(async () => ({
		terminals: [{ id: 't1', displayName: 'Terminal 1', connected: true }]
	}))
}));

vi.mock('$lib/api/status', () => ({
	fetchStatus: vi.fn(async () => ({ running: true, terminals: 1, version: 'test' }))
}));

vi.mock('$lib/api/cpus', () => ({
	fetchCpus: vi.fn(async () => ({
		cpus: [
			{
				cpuId: 'cpu-1',
				busy: false,
				availableStorage: 0,
				coProcessors: 0,
				selectionMode: 'ANY',
				name: 'CPU Alpha'
			}
		],
		total: 1
	}))
}));

vi.mock('$lib/api/cpuProcess', () => ({
	fetchCpuProcess: vi.fn(async () => ({
		cpuId: 'cpu-1',
		busy: true,
		name: 'CPU Alpha',
		summary: {},
		entries: [],
		patterns: []
	}))
}));

vi.mock('$lib/api/userConfig', () => ({
	fetchInventoryViewConfig: vi.fn(async () => ({
		whitelist: [],
		blacklist: [],
		categories: [],
		collapsed: {}
	})),
	saveInventoryViewConfig: vi.fn(async (_terminalId: string, config: any) => config)
}));

vi.mock('$lib/api/inventory', () => ({
	openInventorySse: vi.fn((terminalId: string, handlers: any) => {
		queueMicrotask(() => {
			handlers?.onSnapshot?.({
				terminalId,
				version: 1,
				items: [
					{
						type: 'fluid',
						id: 'minecraft:water',
						uniqueId: 'fluid-water',
						iconKey: 'minecraft:water@h1_test',
						name: 'Water',
						translationKey: 'fluid.minecraft.water',
						namespace: 'minecraft',
						amount: 1000,
						craftable: false
					}
				],
				total: 1
			});
		});
		return { close: vi.fn() } as unknown as EventSource;
	})
}));

vi.mock('$lib/api/prerender', () => ({
	iconUrl: vi.fn((iconKey: string) => `/mock/icon/${encodeURIComponent(iconKey)}`),
	fetchTooltip: vi.fn(async () => ({ iconKey: 'x', tooltip: ['t'] }))
}));

vi.mock('$lib/api/craft', () => ({
	submitCraft: vi.fn(async () => ({ status: 'submitted' }))
}));

describe('+page CPU interactions', () => {
	it('shows login overlay when token missing', async () => {
		window.localStorage.clear();
		setTestUrl('/');

		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('');

		const Page = (await import('../routes/+page.svelte')).default;
		const { findByText, unmount } = render(Page);

		try {
			await findByText('Enter Token');
		} finally {
			unmount();
		}
	});

	it('renders fluid amounts in buckets (B)', async () => {
		setTestUrl('/?token=test-token');
		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('test-token');

		const Page = (await import('../routes/+page.svelte')).default;
		const { container, findByText, unmount } = render(Page);

		try {
			await findByText('Water');

			const slot = container.querySelector('[data-item-id="fluid-water"]') as HTMLElement | null;
			expect(slot).not.toBeNull();
			expect(slot?.querySelector('.item-amount')).toHaveTextContent('1B');
		} finally {
			unmount();
		}
	});

	it('does not show a toast when clicking a CPU card', async () => {
		setTestUrl('/?token=test-token');
		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('test-token');

		const Page = (await import('../routes/+page.svelte')).default;
		const { container, findAllByText, unmount } = render(Page);

		try {
			await findAllByText('CPU Alpha');

			const cpuCard = container.querySelector('.cpu-card') as HTMLElement | null;
			expect(cpuCard).not.toBeNull();

			expect(container.querySelectorAll('.toast')).toHaveLength(0);
			await fireEvent.click(cpuCard!);
			expect(container.querySelectorAll('.toast')).toHaveLength(0);
		} finally {
			unmount();
		}
	});

	it('polls CPU process frequently and shows completion toast', async () => {
		vi.useFakeTimers();
		setTestUrl('/?token=test-token');
		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('test-token');

		const { fetchCpus } = await import('$lib/api/cpus');
		const { fetchCpuProcess } = await import('$lib/api/cpuProcess');
		const { showToast } = await import('$lib/stores/toast');

		vi.mocked(fetchCpus).mockResolvedValueOnce({
			cpus: [
				{
					cpuId: 'cpu-1',
					busy: true,
					availableStorage: 0,
					coProcessors: 0,
					selectionMode: 'ANY',
					name: 'CPU Alpha',
					jobStatus: {
						crafting: { id: 'minecraft:stone', iconKey: 'minecraft:stone@h1_test', name: 'Stone', translationKey: '', amount: 64 },
						totalItems: 10,
						progress: 9,
						elapsedTimeNanos: 1,
						outputTotal: 64,
						outputDone: 63,
						outputRemaining: 1
					}
				}
			],
			total: 1
		});

		vi.mocked(fetchCpuProcess)
			.mockResolvedValueOnce({
				cpuId: 'cpu-1',
				busy: true,
				name: 'CPU Alpha',
				summary: { outputTotal: 64, outputDone: 63, outputRemaining: 1 },
				entries: [{ stack: { id: 'minecraft:stick', iconKey: 'minecraft:stick@h1_test', name: 'Stick', translationKey: '', amount: 1 }, stored: 0, waiting: 1, active: 0 }],
				patterns: []
			})
			.mockResolvedValueOnce({
				cpuId: 'cpu-1',
				busy: false,
				name: 'CPU Alpha',
				summary: { outputTotal: 64, outputDone: 64, outputRemaining: 0 },
				entries: [],
				patterns: []
			});

		const Page = (await import('../routes/+page.svelte')).default;
		const { container, findByText, unmount } = render(Page);

		try {
			await findByText('CPU Alpha');

			const cpuCard = container.querySelector('.cpu-card') as HTMLButtonElement | null;
			expect(cpuCard).not.toBeNull();
			expect(cpuCard?.disabled).toBe(false);

			await fireEvent.click(cpuCard!);

			await findByText('Crafting Process');
			expect(vi.mocked(fetchCpuProcess)).toHaveBeenCalledTimes(1);

			// Auto-refresh interval is 2000ms
			await vi.advanceTimersByTimeAsync(2100);
			expect(vi.mocked(fetchCpuProcess)).toHaveBeenCalledTimes(2);

			expect(vi.mocked(showToast)).toHaveBeenCalled();
		} finally {
			unmount();
			vi.useRealTimers();
		}
	});

	it('renders grouped inventory sections and supports collapsing categories', async () => {
		setTestUrl('/?token=test-token');
		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('test-token');

		const { openInventorySse } = await import('$lib/api/inventory');
		const { fetchInventoryViewConfig } = await import('$lib/api/userConfig');

		vi.mocked(fetchInventoryViewConfig).mockResolvedValueOnce({
			whitelist: [],
			blacklist: [],
			categories: [
				{ name: 'Ores', tagRegex: '^forge:ores/' },
				{ name: 'Dusts', tagRegex: '^forge:dusts/' }
			],
			collapsed: {}
		});

		vi.mocked(openInventorySse).mockImplementationOnce((terminalId: string, handlers: any) => {
			queueMicrotask(() => {
				handlers?.onSnapshot?.({
					terminalId,
					version: 1,
					items: [
						{
							type: 'item',
							id: 'mod:copper_ore',
							uniqueId: 'item:mod:copper_ore',
							iconKey: 'mod:copper_ore@h1_test',
							name: 'Copper Ore',
							translationKey: '',
							namespace: 'mod',
							amount: 10,
							craftable: false,
							tags: ['forge:ores/copper']
						},
						{
							type: 'item',
							id: 'mod:iron_dust',
							uniqueId: 'item:mod:iron_dust',
							iconKey: 'mod:iron_dust@h1_test',
							name: 'Iron Dust',
							translationKey: '',
							namespace: 'mod',
							amount: 5,
							craftable: false,
							tags: ['forge:dusts/iron']
						},
						{
							type: 'fluid',
							id: 'minecraft:water',
							uniqueId: 'fluid-water',
							iconKey: 'minecraft:water@h1_test',
							name: 'Water',
							translationKey: 'fluid.minecraft.water',
							namespace: 'minecraft',
							amount: 1000,
							craftable: false
						}
					],
					total: 3
				});
			});
			return { close: vi.fn() } as unknown as EventSource;
		});

		const Page = (await import('../routes/+page.svelte')).default;
		const { findByText, getByLabelText, queryByText, unmount } = render(Page);

		try {
			await findByText('Ores');
			await findByText('Dusts');
			await findByText('Fluids', { selector: '.inventory-group-title' });

			await findByText('Copper Ore');
			await findByText('Iron Dust');
			await findByText('Water');

			const collapseOres = getByLabelText('Collapse Ores');
			await fireEvent.click(collapseOres);
			expect(queryByText('Copper Ore')).toBeNull();
		} finally {
			unmount();
		}
	});

	it('opens inventory settings modal from the terminal selector', async () => {
		setTestUrl('/?token=test-token');
		const { setAuthToken } = await import('$lib/stores/session');
		setAuthToken('test-token');

		const { fetchInventoryViewConfig } = await import('$lib/api/userConfig');
		vi.mocked(fetchInventoryViewConfig).mockResolvedValueOnce({
			whitelist: [],
			blacklist: [],
			categories: [],
			collapsed: {}
		});

		const Page = (await import('../routes/+page.svelte')).default;
		const { findByLabelText, findByText, unmount } = render(Page);

		try {
			const btn = await findByLabelText('Inventory Settings');
			await fireEvent.click(btn);
			await findByText('Tag Filter');
		} finally {
			unmount();
		}
	});
});
