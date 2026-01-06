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
	CraftRequest,
	GenericStackDto,
	InventoryItem
} from '$lib/api/types';
import { CraftMissingError } from '$lib/api/client';
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

// Mock missing items data for shortage test items
const mockMissingItems: Record<string, GenericStackDto[]> = {
	'test:shortage_basic': [
		{
			id: 'minecraft:diamond',
			iconKey: 'minecraft:diamond',
			name: 'Diamond',
			translationKey: 'item.minecraft.diamond',
			amount: 64,
			type: 'item'
		}
	],
	'test:shortage_multiple': [
		{
			id: 'minecraft:diamond',
			iconKey: 'minecraft:diamond',
			name: 'Diamond',
			translationKey: 'item.minecraft.diamond',
			amount: 128,
			type: 'item'
		},
		{
			id: 'minecraft:emerald',
			iconKey: 'minecraft:emerald',
			name: 'Emerald',
			translationKey: 'item.minecraft.emerald',
			amount: 64,
			type: 'item'
		},
		{
			id: 'ae2:fluix_crystal',
			iconKey: 'ae2:fluix_crystal',
			name: 'Fluix Crystal',
			translationKey: 'item.ae2.fluix_crystal',
			amount: 256,
			type: 'item'
		},
		{
			id: 'minecraft:netherite_ingot',
			iconKey: 'minecraft:netherite_ingot',
			name: 'Netherite Ingot',
			translationKey: 'item.minecraft.netherite_ingot',
			amount: 16,
			type: 'item'
		},
		{
			id: 'ae2:engineering_processor',
			iconKey: 'ae2:engineering_processor',
			name: 'Engineering Processor',
			translationKey: 'item.ae2.engineering_processor',
			amount: 32,
			type: 'item'
		}
	],
	'test:shortage_many': [
		{
			id: 'minecraft:diamond',
			iconKey: 'minecraft:diamond',
			name: 'Diamond',
			translationKey: 'item.minecraft.diamond',
			amount: 512,
			type: 'item'
		},
		{
			id: 'minecraft:emerald',
			iconKey: 'minecraft:emerald',
			name: 'Emerald',
			translationKey: 'item.minecraft.emerald',
			amount: 256,
			type: 'item'
		},
		{
			id: 'minecraft:gold_ingot',
			iconKey: 'minecraft:gold_ingot',
			name: 'Gold Ingot',
			translationKey: 'item.minecraft.gold_ingot',
			amount: 1024,
			type: 'item'
		},
		{
			id: 'minecraft:iron_ingot',
			iconKey: 'minecraft:iron_ingot',
			name: 'Iron Ingot',
			translationKey: 'item.minecraft.iron_ingot',
			amount: 2048,
			type: 'item'
		},
		{
			id: 'ae2:fluix_crystal',
			iconKey: 'ae2:fluix_crystal',
			name: 'Fluix Crystal',
			translationKey: 'item.ae2.fluix_crystal',
			amount: 512,
			type: 'item'
		},
		{
			id: 'ae2:certus_quartz_crystal',
			iconKey: 'ae2:certus_quartz_crystal',
			name: 'Certus Quartz Crystal',
			translationKey: 'item.ae2.certus_quartz_crystal',
			amount: 256,
			type: 'item'
		},
		{
			id: 'minecraft:netherite_ingot',
			iconKey: 'minecraft:netherite_ingot',
			name: 'Netherite Ingot',
			translationKey: 'item.minecraft.netherite_ingot',
			amount: 64,
			type: 'item'
		},
		{
			id: 'ae2:engineering_processor',
			iconKey: 'ae2:engineering_processor',
			name: 'Engineering Processor',
			translationKey: 'item.ae2.engineering_processor',
			amount: 128,
			type: 'item'
		},
		{
			id: 'ae2:calculation_processor',
			iconKey: 'ae2:calculation_processor',
			name: 'Calculation Processor',
			translationKey: 'item.ae2.calculation_processor',
			amount: 64,
			type: 'item'
		},
		{
			id: 'ae2:logic_processor',
			iconKey: 'ae2:logic_processor',
			name: 'Logic Processor',
			translationKey: 'item.ae2.logic_processor',
			amount: 64,
			type: 'item'
		},
		{
			id: 'minecraft:redstone',
			iconKey: 'minecraft:redstone',
			name: 'Redstone Dust',
			translationKey: 'item.minecraft.redstone',
			amount: 4096,
			type: 'item'
		},
		{
			id: 'minecraft:glowstone_dust',
			iconKey: 'minecraft:glowstone_dust',
			name: 'Glowstone Dust',
			translationKey: 'item.minecraft.glowstone_dust',
			amount: 1024,
			type: 'item'
		}
	]
};

export async function mockSubmitCraft(request: CraftRequest): Promise<CraftOkResponse> {
	await delay(300);
	const missing = mockMissingItems[request.id];
	if (missing) {
		throw new CraftMissingError('Missing items for crafting', missing);
	}
	return { status: 'submitted' };
}

// Mock EventSource for SSE
class MockEventSource {
	private handlers: InventorySseHandlers;
	private closed = false;
	private intervalId: ReturnType<typeof setInterval> | null = null;
	private items: InventoryItem[];
	private terminalId: string;
	private version = 1;

	constructor(terminalId: string, handlers: InventorySseHandlers) {
		this.handlers = handlers;
		this.terminalId = terminalId;
		this.items = getAllMockItems();

		// Simulate connection
		setTimeout(() => {
			if (this.closed) return;

			// Send hello
			this.handlers.onHello?.({ terminalId: this.terminalId, version: this.version });

			// Send initial snapshot
			setTimeout(() => {
				if (this.closed) return;
				this.handlers.onSnapshot?.({
					terminalId: this.terminalId,
					version: this.version,
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
			this.version += 1;
			const nextItem = { ...item, amount: newAmount };
			this.items[randomIndex] = nextItem;

			this.handlers.onDelta?.({
				terminalId: this.terminalId,
				version: this.version,
				added: [],
				removed: [],
				updated: [
					{
						uniqueId: nextItem.uniqueId,
						amount: newAmount,
						craftable: nextItem.craftable,
						name: nextItem.name
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
