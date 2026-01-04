/**
 * Mock API handlers - intercepts API calls in mock mode
 */

import { browser } from '$app/environment';
import type {
	StatusResponse,
	TerminalsResponse,
	InventoryResponse,
	CpusResponse,
	CpuProcessResponse,
	InventoryViewConfig,
	TooltipResponse,
	CraftOkResponse,
	InventoryItem
} from '$lib/api/types';
import type { InventorySseHandlers } from '$lib/api/inventory';
import {
	mockStatus,
	mockTerminals,
	getAllMockItems,
	mockCpus,
	mockCpuProcess,
	mockUserConfig,
	mockTooltips
} from './data';

// Check if mock mode is enabled
export function isMockEnabled(): boolean {
	if (!browser) return false;
	// Enable mock mode via URL param or localStorage
	const urlParams = new URLSearchParams(window.location.search);
	if (urlParams.get('mock') === 'true') {
		localStorage.setItem('ae2web-mock', 'true');
		// Auto-set mock token so login is bypassed (matches session store key)
		localStorage.setItem('ae2web.authToken', 'mock-token');
		return true;
	}
	if (urlParams.get('mock') === 'false') {
		localStorage.removeItem('ae2web-mock');
		return false;
	}
	return localStorage.getItem('ae2web-mock') === 'true';
}

// Initialize mock mode - call this early to set up token before session store loads
export function initMockMode(): boolean {
	if (!browser) return false;
	const urlParams = new URLSearchParams(window.location.search);
	if (urlParams.get('mock') === 'true') {
		localStorage.setItem('ae2web-mock', 'true');
		localStorage.setItem('ae2web.authToken', 'mock-token');
		return true;
	}
	return localStorage.getItem('ae2web-mock') === 'true';
}

// Simulate network delay
async function delay(ms: number = 100): Promise<void> {
	return new Promise((resolve) => setTimeout(resolve, ms));
}

// Mock API implementations
export async function mockFetchStatus(): Promise<StatusResponse> {
	await delay(50);
	return mockStatus;
}

export async function mockFetchTerminals(): Promise<TerminalsResponse> {
	await delay(100);
	return { terminals: mockTerminals };
}

export async function mockFetchItems(params: {
	terminalId?: string;
	search?: string;
	page?: number;
	pageSize?: number;
}): Promise<InventoryResponse> {
	await delay(150);

	let items = getAllMockItems();

	// Filter by search
	if (params.search) {
		const searchLower = params.search.toLowerCase();
		items = items.filter(
			(item) =>
				item.name.toLowerCase().includes(searchLower) ||
				item.id.toLowerCase().includes(searchLower) ||
				item.tags?.some((tag) => tag.toLowerCase().includes(searchLower))
		);
	}

	// Pagination
	const page = params.page ?? 0;
	const pageSize = params.pageSize ?? 100;
	const start = page * pageSize;
	const paginatedItems = items.slice(start, start + pageSize);

	return {
		items: paginatedItems,
		total: items.length
	};
}

export async function mockFetchCpus(): Promise<CpusResponse> {
	await delay(100);
	return {
		cpus: mockCpus,
		total: mockCpus.length
	};
}

export async function mockFetchCpuProcess(cpuId: string): Promise<CpuProcessResponse> {
	await delay(100);
	const process = mockCpuProcess[cpuId];
	if (process) {
		return process;
	}
	// Return empty process for non-busy CPUs
	return {
		cpuId,
		busy: false,
		name: null,
		summary: {},
		entries: [],
		patterns: []
	};
}

export async function mockFetchInventoryViewConfig(
	_terminalId: string
): Promise<InventoryViewConfig> {
	await delay(50);
	return { ...mockUserConfig };
}

export async function mockSaveInventoryViewConfig(
	_terminalId: string,
	config: InventoryViewConfig
): Promise<InventoryViewConfig> {
	await delay(200);
	// In mock mode, just return the config as-is
	return config;
}

export async function mockFetchTooltip(iconKey: string): Promise<TooltipResponse> {
	await delay(30);
	const tooltip = mockTooltips[iconKey];
	if (tooltip) {
		return tooltip;
	}
	// Generate a default tooltip
	return {
		iconKey,
		tooltip: [`§f${iconKey.split(':')[1]?.replace(/_/g, ' ') ?? iconKey}`, `§8${iconKey}`]
	};
}

export async function mockSubmitCraft(): Promise<CraftOkResponse> {
	await delay(300);
	return { status: 'submitted' };
}

// Mock EventSource for SSE
class MockEventSource {
	private handlers: InventorySseHandlers;
	private closed = false;
	private intervalId: ReturnType<typeof setInterval> | null = null;
	private items: InventoryItem[];
	private terminalId: string;

	constructor(terminalId: string, handlers: InventorySseHandlers) {
		this.handlers = handlers;
		this.terminalId = terminalId;
		this.items = getAllMockItems();

		// Simulate connection
		setTimeout(() => {
			if (this.closed) return;

			// Send hello
			this.handlers.onHello?.({ terminalId: this.terminalId, version: 1 });

			// Send initial snapshot
			setTimeout(() => {
				if (this.closed) return;
				this.handlers.onSnapshot?.({
					terminalId: this.terminalId,
					version: 1,
					items: this.items,
					total: this.items.length
				});

				// Start sending periodic deltas (simulate inventory changes)
				this.startDeltas();
			}, 100);
		}, 50);
	}

	private startDeltas(): void {
		// Send random delta updates every 5 seconds
		this.intervalId = setInterval(() => {
			if (this.closed) return;

			// Pick a random item to update
			const randomIndex = Math.floor(Math.random() * this.items.length);
			const item = this.items[randomIndex];
			if (!item) return;

			// Random amount change
			const change = Math.floor(Math.random() * 100) - 50;
			const newAmount = Math.max(0, item.amount + change);

			this.handlers.onDelta?.({
				terminalId: this.terminalId,
				version: 1,
				updated: [
					{
						uniqueId: item.uniqueId,
						amount: newAmount,
						craftable: item.craftable,
						name: item.name
					}
				]
			});
		}, 5000);
	}

	close(): void {
		this.closed = true;
		if (this.intervalId) {
			clearInterval(this.intervalId);
			this.intervalId = null;
		}
	}
}

export function mockOpenInventorySse(
	terminalId: string,
	handlers: InventorySseHandlers
): { close: () => void } {
	const mockEs = new MockEventSource(terminalId, handlers);
	return {
		close: () => mockEs.close()
	};
}
