import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CpuDto, CpuProcessEntryDto, CpuProcessResponse } from '$lib/api/types';
import { createCpuProcessController } from './useCpuProcess';

type Deferred<T> = { promise: Promise<T>; resolve(value: T): void };

function deferred<T>(): Deferred<T> {
	let resolve!: (value: T) => void;
	const promise = new Promise<T>((r) => (resolve = r));
	return { promise, resolve };
}

function cpu(cpuId: string): CpuDto {
	return {
		cpuId,
		busy: true,
		availableStorage: 0,
		coProcessors: 0,
		selectionMode: 'ANY',
		name: cpuId
	};
}

function entry(iconKey: string): CpuProcessEntryDto {
	return {
		stack: { id: iconKey, iconKey, name: iconKey, translationKey: iconKey, amount: 1 },
		stored: 0,
		waiting: 1,
		active: 0
	};
}

function response(cpuId: string, entries: CpuProcessEntryDto[]): CpuProcessResponse {
	return { cpuId, busy: true, name: cpuId, summary: {}, entries, patterns: [] };
}

function createState() {
	return {
		open: false,
		cpuId: null as string | null,
		cpuName: null as string | null,
		autoRefresh: false,
		completionNotified: false,
		loading: false,
		error: null as string | null,
		data: null as CpuProcessResponse | null
	};
}

describe('createCpuProcessController', () => {
	let destroy: (() => void) | null = null;

	afterEach(() => {
		destroy?.();
		destroy = null;
	});

	it('ignores a late response for a previously opened CPU', async () => {
		const pending = new Map<string, Array<Deferred<CpuProcessResponse>>>();
		const fetchCpuProcess = vi.fn((_terminalId: string, cpuId: string) => {
			const d = deferred<CpuProcessResponse>();
			pending.set(cpuId, [...(pending.get(cpuId) ?? []), d]);
			return d.promise;
		});

		const state = createState();
		const controller = createCpuProcessController({
			state,
			getTerminalId: () => 't1',
			fetchCpuProcess,
			showToast: vi.fn(),
			intervalMs: 1_000_000
		});
		destroy = controller.destroy;

		controller.openModal(cpu('A'));
		controller.closeModal();
		controller.openModal(cpu('B'));
		expect(fetchCpuProcess).toHaveBeenCalledTimes(2);

		pending.get('B')![0].resolve(response('B', [entry('b1')]));
		await vi.waitFor(() => expect(state.data?.cpuId).toBe('B'));

		pending.get('A')![0].resolve(response('A', [entry('a1')]));
		await Promise.resolve();
		await Promise.resolve();

		expect(state.data?.cpuId).toBe('B');
		expect(state.loading).toBe(false);

		// A's entries must not leak into B's merged history as completed ghosts.
		const refresh = controller.refresh();
		pending.get('B')![1].resolve(response('B', []));
		await refresh;

		const keys = state.data!.entries.map((e) => e.stack.iconKey);
		expect(keys).toEqual(['b1']);
	});

	it('keeps loading owned by the current request when a stale one finishes', async () => {
		const pending: Array<Deferred<CpuProcessResponse>> = [];
		const state = createState();
		const controller = createCpuProcessController({
			state,
			getTerminalId: () => 't1',
			fetchCpuProcess: () => {
				const d = deferred<CpuProcessResponse>();
				pending.push(d);
				return d.promise;
			},
			showToast: vi.fn(),
			intervalMs: 1_000_000
		});
		destroy = controller.destroy;

		controller.openModal(cpu('A'));
		controller.openModal(cpu('B'));
		expect(state.loading).toBe(true);

		pending[0].resolve(response('A', []));
		await Promise.resolve();
		await Promise.resolve();

		expect(state.loading).toBe(true);
		expect(state.data).toBeNull();
	});
});
