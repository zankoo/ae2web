/**
 * Mock data for testing the AE2 Web Terminal UI
 * Enable by setting VITE_USE_MOCK=true in .env or environment
 */

import type {
	StatusResponse,
	TerminalInfo,
	InventoryItem,
	CpuDto,
	CpuProcessResponse,
	InventoryViewConfig,
	TooltipResponse
} from '$lib/api/types';

// ============================================================================
// Status
// ============================================================================
export const mockStatus: StatusResponse = {
	running: true,
	terminals: 3,
	version: '1.0.0-mock'
};

// ============================================================================
// Terminals
// ============================================================================
export const mockTerminals: TerminalInfo[] = [
	{
		id: 'terminal-main',
		name: 'Main Base',
		displayName: 'Main Base',
		position: { x: 100, y: 64, z: -200 },
		dimension: 'minecraft:overworld',
		connected: true
	},
	{
		id: 'terminal-nether',
		name: 'Nether Hub',
		displayName: 'Nether Hub',
		position: { x: 0, y: 80, z: 0 },
		dimension: 'minecraft:the_nether',
		connected: true
	},
	{
		id: 'terminal-end',
		name: 'End Storage',
		displayName: 'End Storage',
		position: { x: 50, y: 100, z: 50 },
		dimension: 'minecraft:the_end',
		connected: false
	}
];

// ============================================================================
// Inventory Items
// ============================================================================
export const mockInventoryItems: InventoryItem[] = [
	// Ores
	{
		type: 'item',
		id: 'minecraft:iron_ore',
		uniqueId: 'minecraft:iron_ore',
		iconKey: 'minecraft:iron_ore',
		name: 'Iron Ore',
		translationKey: 'block.minecraft.iron_ore',
		namespace: 'minecraft',
		amount: 2048,
		craftable: false,
		tags: ['forge:ores', 'forge:ores/iron', 'minecraft:mineable/pickaxe']
	},
	{
		type: 'item',
		id: 'minecraft:gold_ore',
		uniqueId: 'minecraft:gold_ore',
		iconKey: 'minecraft:gold_ore',
		name: 'Gold Ore',
		translationKey: 'block.minecraft.gold_ore',
		namespace: 'minecraft',
		amount: 512,
		craftable: false,
		tags: ['forge:ores', 'forge:ores/gold', 'minecraft:mineable/pickaxe']
	},
	{
		type: 'item',
		id: 'minecraft:copper_ore',
		uniqueId: 'minecraft:copper_ore',
		iconKey: 'minecraft:copper_ore',
		name: 'Copper Ore',
		translationKey: 'block.minecraft.copper_ore',
		namespace: 'minecraft',
		amount: 4096,
		craftable: false,
		tags: ['forge:ores', 'forge:ores/copper', 'minecraft:mineable/pickaxe']
	},
	{
		type: 'item',
		id: 'minecraft:diamond_ore',
		uniqueId: 'minecraft:diamond_ore',
		iconKey: 'minecraft:diamond_ore',
		name: 'Diamond Ore',
		translationKey: 'block.minecraft.diamond_ore',
		namespace: 'minecraft',
		amount: 128,
		craftable: false,
		tags: ['forge:ores', 'forge:ores/diamond', 'minecraft:mineable/pickaxe']
	},

	// Ingots
	{
		type: 'item',
		id: 'minecraft:iron_ingot',
		uniqueId: 'minecraft:iron_ingot',
		iconKey: 'minecraft:iron_ingot',
		name: 'Iron Ingot',
		translationKey: 'item.minecraft.iron_ingot',
		namespace: 'minecraft',
		amount: 8192,
		craftable: true,
		tags: ['forge:ingots', 'forge:ingots/iron']
	},
	{
		type: 'item',
		id: 'minecraft:gold_ingot',
		uniqueId: 'minecraft:gold_ingot',
		iconKey: 'minecraft:gold_ingot',
		name: 'Gold Ingot',
		translationKey: 'item.minecraft.gold_ingot',
		namespace: 'minecraft',
		amount: 2048,
		craftable: true,
		tags: ['forge:ingots', 'forge:ingots/gold']
	},
	{
		type: 'item',
		id: 'minecraft:copper_ingot',
		uniqueId: 'minecraft:copper_ingot',
		iconKey: 'minecraft:copper_ingot',
		name: 'Copper Ingot',
		translationKey: 'item.minecraft.copper_ingot',
		namespace: 'minecraft',
		amount: 16384,
		craftable: true,
		tags: ['forge:ingots', 'forge:ingots/copper']
	},
	{
		type: 'item',
		id: 'minecraft:netherite_ingot',
		uniqueId: 'minecraft:netherite_ingot',
		iconKey: 'minecraft:netherite_ingot',
		name: 'Netherite Ingot',
		translationKey: 'item.minecraft.netherite_ingot',
		namespace: 'minecraft',
		amount: 16,
		craftable: true,
		tags: ['forge:ingots', 'forge:ingots/netherite']
	},

	// Dusts
	{
		type: 'item',
		id: 'ae2:fluix_dust',
		uniqueId: 'ae2:fluix_dust',
		iconKey: 'ae2:fluix_dust',
		name: 'Fluix Dust',
		translationKey: 'item.ae2.fluix_dust',
		namespace: 'ae2',
		amount: 1024,
		craftable: true,
		tags: ['ae2:dusts', 'forge:dusts']
	},
	{
		type: 'item',
		id: 'ae2:certus_quartz_dust',
		uniqueId: 'ae2:certus_quartz_dust',
		iconKey: 'ae2:certus_quartz_dust',
		name: 'Certus Quartz Dust',
		translationKey: 'item.ae2.certus_quartz_dust',
		namespace: 'ae2',
		amount: 2048,
		craftable: true,
		tags: ['ae2:dusts', 'forge:dusts']
	},

	// Gems
	{
		type: 'item',
		id: 'minecraft:diamond',
		uniqueId: 'minecraft:diamond',
		iconKey: 'minecraft:diamond',
		name: 'Diamond',
		translationKey: 'item.minecraft.diamond',
		namespace: 'minecraft',
		amount: 256,
		craftable: false,
		tags: ['forge:gems', 'forge:gems/diamond']
	},
	{
		type: 'item',
		id: 'minecraft:emerald',
		uniqueId: 'minecraft:emerald',
		iconKey: 'minecraft:emerald',
		name: 'Emerald',
		translationKey: 'item.minecraft.emerald',
		namespace: 'minecraft',
		amount: 512,
		craftable: false,
		tags: ['forge:gems', 'forge:gems/emerald']
	},
	{
		type: 'item',
		id: 'ae2:certus_quartz_crystal',
		uniqueId: 'ae2:certus_quartz_crystal',
		iconKey: 'ae2:certus_quartz_crystal',
		name: 'Certus Quartz Crystal',
		translationKey: 'item.ae2.certus_quartz_crystal',
		namespace: 'ae2',
		amount: 4096,
		craftable: true,
		tags: ['ae2:crystals', 'forge:gems']
	},
	{
		type: 'item',
		id: 'ae2:fluix_crystal',
		uniqueId: 'ae2:fluix_crystal',
		iconKey: 'ae2:fluix_crystal',
		name: 'Fluix Crystal',
		translationKey: 'item.ae2.fluix_crystal',
		namespace: 'ae2',
		amount: 2048,
		craftable: true,
		tags: ['ae2:crystals', 'forge:gems']
	},

	// AE2 Components
	{
		type: 'item',
		id: 'ae2:logic_processor',
		uniqueId: 'ae2:logic_processor',
		iconKey: 'ae2:logic_processor',
		name: 'Logic Processor',
		translationKey: 'item.ae2.logic_processor',
		namespace: 'ae2',
		amount: 512,
		craftable: true,
		tags: ['ae2:processors']
	},
	{
		type: 'item',
		id: 'ae2:calculation_processor',
		uniqueId: 'ae2:calculation_processor',
		iconKey: 'ae2:calculation_processor',
		name: 'Calculation Processor',
		translationKey: 'item.ae2.calculation_processor',
		namespace: 'ae2',
		amount: 256,
		craftable: true,
		tags: ['ae2:processors']
	},
	{
		type: 'item',
		id: 'ae2:engineering_processor',
		uniqueId: 'ae2:engineering_processor',
		iconKey: 'ae2:engineering_processor',
		name: 'Engineering Processor',
		translationKey: 'item.ae2.engineering_processor',
		namespace: 'ae2',
		amount: 128,
		craftable: true,
		tags: ['ae2:processors']
	},

	// Storage
	{
		type: 'item',
		id: 'ae2:1k_cell_component',
		uniqueId: 'ae2:1k_cell_component',
		iconKey: 'ae2:1k_cell_component',
		name: '1k ME Storage Component',
		translationKey: 'item.ae2.1k_cell_component',
		namespace: 'ae2',
		amount: 64,
		craftable: true,
		tags: ['ae2:storage_components']
	},
	{
		type: 'item',
		id: 'ae2:4k_cell_component',
		uniqueId: 'ae2:4k_cell_component',
		iconKey: 'ae2:4k_cell_component',
		name: '4k ME Storage Component',
		translationKey: 'item.ae2.4k_cell_component',
		namespace: 'ae2',
		amount: 32,
		craftable: true,
		tags: ['ae2:storage_components']
	},
	{
		type: 'item',
		id: 'ae2:16k_cell_component',
		uniqueId: 'ae2:16k_cell_component',
		iconKey: 'ae2:16k_cell_component',
		name: '16k ME Storage Component',
		translationKey: 'item.ae2.16k_cell_component',
		namespace: 'ae2',
		amount: 16,
		craftable: true,
		tags: ['ae2:storage_components']
	},
	{
		type: 'item',
		id: 'ae2:64k_cell_component',
		uniqueId: 'ae2:64k_cell_component',
		iconKey: 'ae2:64k_cell_component',
		name: '64k ME Storage Component',
		translationKey: 'item.ae2.64k_cell_component',
		namespace: 'ae2',
		amount: 8,
		craftable: true,
		tags: ['ae2:storage_components']
	},

	// Tools
	{
		type: 'item',
		id: 'minecraft:diamond_pickaxe',
		uniqueId: 'minecraft:diamond_pickaxe',
		iconKey: 'minecraft:diamond_pickaxe',
		name: 'Diamond Pickaxe',
		translationKey: 'item.minecraft.diamond_pickaxe',
		namespace: 'minecraft',
		amount: 5,
		craftable: true,
		tags: ['forge:tools', 'forge:tools/pickaxes', 'minecraft:tools']
	},
	{
		type: 'item',
		id: 'minecraft:netherite_pickaxe',
		uniqueId: 'minecraft:netherite_pickaxe',
		iconKey: 'minecraft:netherite_pickaxe',
		name: 'Netherite Pickaxe',
		translationKey: 'item.minecraft.netherite_pickaxe',
		namespace: 'minecraft',
		amount: 2,
		craftable: true,
		tags: ['forge:tools', 'forge:tools/pickaxes', 'minecraft:tools']
	},

	// Building Blocks
	{
		type: 'item',
		id: 'minecraft:stone',
		uniqueId: 'minecraft:stone',
		iconKey: 'minecraft:stone',
		name: 'Stone',
		translationKey: 'block.minecraft.stone',
		namespace: 'minecraft',
		amount: 999999,
		craftable: true,
		tags: ['forge:stone', 'minecraft:base_stone_overworld']
	},
	{
		type: 'item',
		id: 'minecraft:cobblestone',
		uniqueId: 'minecraft:cobblestone',
		iconKey: 'minecraft:cobblestone',
		name: 'Cobblestone',
		translationKey: 'block.minecraft.cobblestone',
		namespace: 'minecraft',
		amount: 500000,
		craftable: true,
		tags: ['forge:cobblestone']
	},
	{
		type: 'item',
		id: 'minecraft:glass',
		uniqueId: 'minecraft:glass',
		iconKey: 'minecraft:glass',
		name: 'Glass',
		translationKey: 'block.minecraft.glass',
		namespace: 'minecraft',
		amount: 10000,
		craftable: true,
		tags: ['forge:glass']
	},

	// Redstone
	{
		type: 'item',
		id: 'minecraft:redstone',
		uniqueId: 'minecraft:redstone',
		iconKey: 'minecraft:redstone',
		name: 'Redstone Dust',
		translationKey: 'item.minecraft.redstone',
		namespace: 'minecraft',
		amount: 32768,
		craftable: false,
		tags: ['forge:dusts/redstone', 'forge:redstone_dusts']
	},
	{
		type: 'item',
		id: 'minecraft:glowstone_dust',
		uniqueId: 'minecraft:glowstone_dust',
		iconKey: 'minecraft:glowstone_dust',
		name: 'Glowstone Dust',
		translationKey: 'item.minecraft.glowstone_dust',
		namespace: 'minecraft',
		amount: 8192,
		craftable: false,
		tags: ['forge:dusts/glowstone']
	}
];

// Fluids
export const mockFluids: InventoryItem[] = [
	{
		type: 'fluid',
		id: 'minecraft:water',
		uniqueId: 'minecraft:water',
		iconKey: 'minecraft:water',
		name: 'Water',
		translationKey: 'fluid.minecraft.water',
		namespace: 'minecraft',
		amount: 1000000000, // 1000 buckets in mB
		craftable: false,
		tags: ['minecraft:water', 'forge:water']
	},
	{
		type: 'fluid',
		id: 'minecraft:lava',
		uniqueId: 'minecraft:lava',
		iconKey: 'minecraft:lava',
		name: 'Lava',
		translationKey: 'fluid.minecraft.lava',
		namespace: 'minecraft',
		amount: 500000000, // 500 buckets
		craftable: false,
		tags: ['minecraft:lava', 'forge:lava']
	},
	{
		type: 'fluid',
		id: 'minecraft:milk',
		uniqueId: 'minecraft:milk',
		iconKey: 'minecraft:milk_bucket',
		name: 'Milk',
		translationKey: 'fluid.minecraft.milk',
		namespace: 'minecraft',
		amount: 10000000, // 10 buckets
		craftable: false,
		tags: ['forge:milk']
	}
];

// ============================================================================
// CPUs
// ============================================================================
export const mockCpus: CpuDto[] = [
	{
		cpuId: 'cpu-1',
		busy: true,
		availableStorage: 1024,
		coProcessors: 4,
		selectionMode: 'ANY',
		name: 'Crafting CPU Alpha',
		jobStatus: {
			crafting: {
				id: 'ae2:64k_cell_component',
				iconKey: 'ae2:64k_cell_component',
				name: '64k ME Storage Component',
				translationKey: 'item.ae2.64k_cell_component',
				amount: 16
			},
			totalItems: 128,
			progress: 96,
			outputTotal: 16,
			outputDone: 12,
			outputRemaining: 4,
			elapsedTimeNanos: 45000000000 // 45 seconds
		}
	},
	{
		cpuId: 'cpu-2',
		busy: true,
		availableStorage: 2048,
		coProcessors: 8,
		selectionMode: 'ANY',
		name: 'Crafting CPU Beta',
		jobStatus: {
			crafting: {
				id: 'minecraft:diamond_pickaxe',
				iconKey: 'minecraft:diamond_pickaxe',
				name: 'Diamond Pickaxe',
				translationKey: 'item.minecraft.diamond_pickaxe',
				amount: 10
			},
			totalItems: 50,
			progress: 25,
			outputTotal: 10,
			outputDone: 5,
			outputRemaining: 5,
			elapsedTimeNanos: 12000000000 // 12 seconds
		}
	},
	{
		cpuId: 'cpu-3',
		busy: false,
		availableStorage: 4096,
		coProcessors: 16,
		selectionMode: 'ANY',
		name: 'Crafting CPU Gamma'
	},
	{
		cpuId: 'cpu-4',
		busy: false,
		availableStorage: 512,
		coProcessors: 2,
		selectionMode: 'ANY',
		name: null // Unnamed CPU
	}
];

// ============================================================================
// CPU Process Details
// ============================================================================
export const mockCpuProcess: Record<string, CpuProcessResponse> = {
	'cpu-1': {
		cpuId: 'cpu-1',
		busy: true,
		name: 'Crafting CPU Alpha',
		summary: {
			finalOutput: {
				id: 'ae2:64k_cell_component',
				iconKey: 'ae2:64k_cell_component',
				name: '64k ME Storage Component',
				translationKey: 'item.ae2.64k_cell_component',
				amount: 16
			},
			outputTotal: 16,
			outputDone: 12,
			outputRemaining: 4,
			totalItems: 128,
			progress: 96,
			elapsedTimeNanos: 45000000000
		},
		entries: [
			{
				stack: {
					id: 'ae2:64k_cell_component',
					iconKey: 'ae2:64k_cell_component',
					name: '64k ME Storage Component',
					translationKey: 'item.ae2.64k_cell_component',
					amount: 16
				},
				stored: 12,
				waiting: 4,
				active: 0
			},
			{
				stack: {
					id: 'ae2:16k_cell_component',
					iconKey: 'ae2:16k_cell_component',
					name: '16k ME Storage Component',
					translationKey: 'item.ae2.16k_cell_component',
					amount: 48
				},
				stored: 48,
				waiting: 0,
				active: 0
			},
			{
				stack: {
					id: 'ae2:engineering_processor',
					iconKey: 'ae2:engineering_processor',
					name: 'Engineering Processor',
					translationKey: 'item.ae2.engineering_processor',
					amount: 64
				},
				stored: 32,
				waiting: 16,
				active: 16
			},
			{
				stack: {
					id: 'ae2:fluix_crystal',
					iconKey: 'ae2:fluix_crystal',
					name: 'Fluix Crystal',
					translationKey: 'item.ae2.fluix_crystal',
					amount: 256
				},
				stored: 256,
				waiting: 0,
				active: 0
			},
			{
				stack: {
					id: 'minecraft:redstone',
					iconKey: 'minecraft:redstone',
					name: 'Redstone Dust',
					translationKey: 'item.minecraft.redstone',
					amount: 512
				},
				stored: 512,
				waiting: 0,
				active: 0
			}
		],
		patterns: [
			{
				patternId: 'pattern-1',
				remainingRuns: 4,
				outputs: [
					{
						id: 'ae2:64k_cell_component',
						iconKey: 'ae2:64k_cell_component',
						name: '64k ME Storage Component',
						translationKey: 'item.ae2.64k_cell_component',
						amount: 1
					}
				],
				inputs: [
					{
						multiplier: 3,
						choices: [
							{
								id: 'ae2:16k_cell_component',
								iconKey: 'ae2:16k_cell_component',
								name: '16k ME Storage Component',
								translationKey: 'item.ae2.16k_cell_component',
								amount: 1
							}
						]
					},
					{
						multiplier: 4,
						choices: [
							{
								id: 'ae2:engineering_processor',
								iconKey: 'ae2:engineering_processor',
								name: 'Engineering Processor',
								translationKey: 'item.ae2.engineering_processor',
								amount: 1
							}
						]
					}
				]
			}
		]
	},
	'cpu-2': {
		cpuId: 'cpu-2',
		busy: true,
		name: 'Crafting CPU Beta',
		summary: {
			finalOutput: {
				id: 'minecraft:diamond_pickaxe',
				iconKey: 'minecraft:diamond_pickaxe',
				name: 'Diamond Pickaxe',
				translationKey: 'item.minecraft.diamond_pickaxe',
				amount: 10
			},
			outputTotal: 10,
			outputDone: 5,
			outputRemaining: 5,
			totalItems: 50,
			progress: 25,
			elapsedTimeNanos: 12000000000
		},
		entries: [
			{
				stack: {
					id: 'minecraft:diamond_pickaxe',
					iconKey: 'minecraft:diamond_pickaxe',
					name: 'Diamond Pickaxe',
					translationKey: 'item.minecraft.diamond_pickaxe',
					amount: 10
				},
				stored: 5,
				waiting: 5,
				active: 0
			},
			{
				stack: {
					id: 'minecraft:diamond',
					iconKey: 'minecraft:diamond',
					name: 'Diamond',
					translationKey: 'item.minecraft.diamond',
					amount: 30
				},
				stored: 15,
				waiting: 10,
				active: 5
			},
			{
				stack: {
					id: 'minecraft:stick',
					iconKey: 'minecraft:stick',
					name: 'Stick',
					translationKey: 'item.minecraft.stick',
					amount: 20
				},
				stored: 20,
				waiting: 0,
				active: 0
			}
		],
		patterns: [
			{
				patternId: 'pattern-pickaxe',
				remainingRuns: 5,
				outputs: [
					{
						id: 'minecraft:diamond_pickaxe',
						iconKey: 'minecraft:diamond_pickaxe',
						name: 'Diamond Pickaxe',
						translationKey: 'item.minecraft.diamond_pickaxe',
						amount: 1
					}
				],
				inputs: [
					{
						multiplier: 3,
						choices: [
							{
								id: 'minecraft:diamond',
								iconKey: 'minecraft:diamond',
								name: 'Diamond',
								translationKey: 'item.minecraft.diamond',
								amount: 1
							}
						]
					},
					{
						multiplier: 2,
						choices: [
							{
								id: 'minecraft:stick',
								iconKey: 'minecraft:stick',
								name: 'Stick',
								translationKey: 'item.minecraft.stick',
								amount: 1
							}
						]
					}
				]
			}
		]
	}
};

// ============================================================================
// User Config
// ============================================================================
export const mockUserConfig: InventoryViewConfig = {
	whitelist: [],
	blacklist: [],
	categories: [
		{ name: 'Ores', tagRegex: 'forge:ores' },
		{ name: 'Ingots', tagRegex: 'forge:ingots' },
		{ name: 'Gems', tagRegex: 'forge:gems' },
		{ name: 'Dusts', tagRegex: 'forge:dusts|ae2:dusts' },
		{ name: 'AE2 Components', tagRegex: 'ae2:processors|ae2:storage_components|ae2:crystals' },
		{ name: 'Tools', tagRegex: 'forge:tools|minecraft:tools' },
		{ name: 'Building', tagRegex: 'forge:stone|forge:cobblestone|forge:glass' },
		{ name: 'Fluids', tagRegex: '.*' }
	],
	collapsed: {}
};

// ============================================================================
// Tooltips
// ============================================================================
export const mockTooltips: Record<string, TooltipResponse> = {
	'minecraft:iron_ore': {
		iconKey: 'minecraft:iron_ore',
		tooltip: ['§fIron Ore', '§7Can be smelted into Iron Ingot', '§8minecraft:iron_ore']
	},
	'minecraft:diamond': {
		iconKey: 'minecraft:diamond',
		tooltip: ['§bDiamond', '§7A valuable gem', '§8minecraft:diamond']
	},
	'ae2:64k_cell_component': {
		iconKey: 'ae2:64k_cell_component',
		tooltip: [
			'§564k ME Storage Component',
			'§7Stores up to 65,536 item types',
			'§7Bytes: 524,288',
			'§8ae2:64k_cell_component'
		]
	}
};

// ============================================================================
// Helper to get all items (items + fluids)
// ============================================================================
export function getAllMockItems(): InventoryItem[] {
	return [...mockInventoryItems, ...mockFluids];
}
