import { describe, expect, it } from 'vitest';

import type { InventoryItem, InventoryViewConfig } from '$lib/api/types';
import { buildInventoryRenderNodes } from '$lib/utils/inventoryGrouping';

function item(id: string, tags: string[] = []): InventoryItem {
	return {
		type: 'item',
		id,
		uniqueId: `item:${id}`,
		iconKey: `${id}@h1_test`,
		name: id,
		translationKey: '',
		namespace: id.split(':')[0] ?? 'minecraft',
		amount: 1,
		craftable: false,
		tags
	};
}

function fluid(id: string): InventoryItem {
	return {
		type: 'fluid',
		id,
		uniqueId: `fluid:${id}`,
		iconKey: `${id}@h1_test`,
		name: id,
		translationKey: '',
		namespace: id.split(':')[0] ?? 'minecraft',
		amount: 1000,
		craftable: false
	};
}

function cfg(partial: Partial<InventoryViewConfig> = {}): InventoryViewConfig {
	return {
		whitelist: [],
		blacklist: [],
		categories: [],
		collapsed: {},
		...partial
	};
}

describe('inventoryGrouping', () => {
	it('does not apply grouping in fluids view', () => {
		const items = [item('minecraft:stone', ['forge:ores/iron']), fluid('minecraft:water')];
		const nodes = buildInventoryRenderNodes(
			items,
			'fluids',
			cfg({ categories: [{ name: 'Ores', tagRegex: '^forge:ores/' }] })
		);

		expect(nodes.some((n) => n.kind === 'header')).toBe(false);
		expect(nodes.filter((n) => n.kind === 'item').map((n: any) => n.item.type)).toEqual(['fluid']);
	});

	it('assigns items to first matching category (scheme 1)', () => {
		const items = [
			item('mod:copper_ore', ['forge:ores/copper']),
			item('mod:iron_dust', ['forge:dusts/iron']),
			item('mod:multi', ['forge:dusts/iron', 'forge:ores/iron']),
			item('mod:other', [])
		];
		const nodes = buildInventoryRenderNodes(
			items,
			'items',
			cfg({
				categories: [
					{ name: 'Ores', tagRegex: '^forge:ores/' },
					{ name: 'Dusts', tagRegex: '^forge:dusts/' }
				]
			})
		);

		const headers = nodes.filter((n) => n.kind === 'header').map((n: any) => n.title);
		expect(headers).toEqual(['Ores', 'Dusts', 'Other']);
	});

	it('keeps category headers even when no matching items exist', () => {
		const items = [item('mod:iron_dust', ['forge:dusts/iron'])];
		const nodes = buildInventoryRenderNodes(
			items,
			'items',
			cfg({
				categories: [
					{ name: 'Ores', tagRegex: '^forge:ores/' },
					{ name: 'Dusts', tagRegex: '^forge:dusts/' }
				]
			})
		);

		const headers = nodes
			.filter((n) => n.kind === 'header')
			.map((n: any) => ({ title: n.title, count: n.count }));
		expect(headers).toEqual([
			{ title: 'Ores', count: 0 },
			{ title: 'Dusts', count: 1 }
		]);
	});

	it('includes a Fluids section in All view when tag filter is inactive', () => {
		const items = [item('minecraft:stone', []), fluid('minecraft:water')];
		const nodes = buildInventoryRenderNodes(
			items,
			'all',
			cfg({ categories: [{ name: 'Stuff', tagRegex: '.*' }] })
		);
		const headers = nodes.filter((n) => n.kind === 'header').map((n: any) => n.title);
		expect(headers).toContain('Fluids');
	});

	it('hides fluids in All view when whitelist is active (fluids have no tags)', () => {
		const items = [item('minecraft:stone', ['forge:ores/iron']), fluid('minecraft:water')];
		const nodes = buildInventoryRenderNodes(items, 'all', cfg({ whitelist: ['^forge:ores/'] }));
		const fluidItems = nodes
			.filter((n) => n.kind === 'item')
			.map((n: any) => n.item)
			.filter((x: any) => x.type === 'fluid');
		expect(fluidItems).toHaveLength(0);
	});

	it('shows fluids in All view when only blacklist is active', () => {
		const items = [item('minecraft:stone', ['forge:ores/iron']), fluid('minecraft:water')];
		const nodes = buildInventoryRenderNodes(items, 'all', cfg({ blacklist: ['^forge:ores/'] }));
		const fluidItems = nodes
			.filter((n) => n.kind === 'item')
			.map((n: any) => n.item)
			.filter((x: any) => x.type === 'fluid');
		expect(fluidItems).toHaveLength(1);
	});

	it('applies whitelist/blacklist to items by tag', () => {
		const items = [
			item('mod:copper_ore', ['forge:ores/copper']),
			item('mod:iron_ore', ['forge:ores/iron'])
		];
		const nodes = buildInventoryRenderNodes(
			items,
			'items',
			cfg({ whitelist: ['^forge:ores/'], blacklist: ['copper'] })
		);
		const renderedIds = nodes.filter((n) => n.kind === 'item').map((n: any) => n.item.id);
		expect(renderedIds).toEqual(['mod:iron_ore']);
	});

	it('does not hide items with no tags when only blacklist is set', () => {
		const items = [
			item('minecraft:lily_pad', []),
			item('minecraft:iron_ingot', ['forge:ingots/iron'])
		];
		const nodes = buildInventoryRenderNodes(items, 'items', cfg({ blacklist: ['forge:ingots'] }));
		const renderedIds = nodes.filter((n) => n.kind === 'item').map((n: any) => n.item.id);
		expect(renderedIds).toEqual(['minecraft:lily_pad']);
	});

	it('respects collapsed sections by omitting item nodes', () => {
		const items = [item('mod:iron_ore', ['forge:ores/iron']), item('mod:other', [])];
		const nodes = buildInventoryRenderNodes(
			items,
			'items',
			cfg({
				categories: [{ name: 'Ores', tagRegex: '^forge:ores/' }],
				collapsed: { 'Ores|^forge:ores/': true }
			})
		);

		const oreItem = nodes.find((n: any) => n.kind === 'item' && n.item.id === 'mod:iron_ore');
		expect(oreItem).toBeUndefined();
	});
});
