import type { CpuDto, CpuProcessResponse } from '$lib/api/types';

type CpuProcessStateAccess = {
	get open(): boolean;
	set open(value: boolean);

	get cpuId(): string | null;
	set cpuId(value: string | null);

	get cpuName(): string | null;
	set cpuName(value: string | null);

	get autoRefresh(): boolean;
	set autoRefresh(value: boolean);

	get completionNotified(): boolean;
	set completionNotified(value: boolean);

	get loading(): boolean;
	set loading(value: boolean);

	get error(): string | null;
	set error(value: string | null);

	get data(): CpuProcessResponse | null;
	set data(value: CpuProcessResponse | null);
};

export type CpuProcessController = {
	openModal(cpu: CpuDto): void;
	closeModal(): void;
	refresh(): Promise<void>;
	setAutoRefresh(value: boolean): void;
	destroy(): void;
};

export function createCpuProcessController({
	state,
	getTerminalId,
	fetchCpuProcess,
	showToast,
	intervalMs = 2000
}: {
	state: CpuProcessStateAccess;
	getTerminalId(): string | null | undefined;
	fetchCpuProcess(terminalId: string, cpuId: string): Promise<CpuProcessResponse>;
	showToast(message: string, kind: 'success' | 'error'): void;
	intervalMs?: number;
}): CpuProcessController {
	let timer: number | null = null;

	function stopTimer(): void {
		if (timer != null) {
			window.clearInterval(timer);
			timer = null;
		}
	}

	function syncTimer(): void {
		stopTimer();
		if (!state.open) return;

		void refresh();
		if (!state.autoRefresh) return;

		timer = window.setInterval(() => void refresh(), intervalMs);
	}

	async function refresh(): Promise<void> {
		if (!state.open) return;

		const terminalId = getTerminalId();
		if (!terminalId) return;
		if (!state.cpuId) return;
		if (state.loading) return;

		state.loading = true;
		try {
			const data = await fetchCpuProcess(terminalId, state.cpuId);
			state.data = data;
			state.cpuName = data?.name ?? state.cpuName;
			state.error = null;

			if (data && data.busy === false) {
				state.autoRefresh = false;
				stopTimer();
				if (!state.completionNotified) {
					const outputName = data?.summary?.finalOutput?.name;
					showToast(outputName ? `Crafting complete: ${outputName}` : 'Crafting complete', 'success');
					state.completionNotified = true;
				}
			}
		} catch (e) {
			const message = e instanceof Error ? e.message : 'Failed to load CPU process';
			if (message.toLowerCase().includes('not busy')) {
				state.autoRefresh = false;
				stopTimer();
				state.error = null;

				const lastSummary: CpuProcessResponse['summary'] = state.data?.summary ?? {};
				const outputTotal = Number(lastSummary.outputTotal ?? 0);
				const nextSummary =
					outputTotal > 0
						? { ...lastSummary, outputDone: outputTotal, outputRemaining: 0 }
						: lastSummary;

				state.data = {
					cpuId: state.cpuId,
					busy: false,
					name: state.cpuName ?? null,
					summary: nextSummary,
					entries: [],
					patterns: []
				};

				if (!state.completionNotified) {
					const outputName = state.data?.summary?.finalOutput?.name;
					showToast(outputName ? `Crafting complete: ${outputName}` : 'Crafting complete', 'success');
					state.completionNotified = true;
				}
				return;
			}
			state.error = message;
		} finally {
			state.loading = false;
		}
	}

	function openModal(cpu: CpuDto): void {
		if (!cpu?.busy) return;
		if (!cpu.cpuId) return;
		if (!getTerminalId()) return;

		state.open = true;
		state.cpuId = cpu.cpuId;
		state.cpuName = cpu.name;
		state.autoRefresh = true;
		state.completionNotified = false;
		state.loading = false;
		state.error = null;
		state.data = null;

		syncTimer();
	}

	function closeModal(): void {
		state.open = false;
		stopTimer();
	}

	function setAutoRefresh(value: boolean): void {
		state.autoRefresh = value;
		syncTimer();
	}

	function destroy(): void {
		stopTimer();
	}

	return { openModal, closeModal, refresh, setAutoRefresh, destroy };
}

