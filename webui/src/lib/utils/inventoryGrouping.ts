import type { InventoryFilter } from '$lib/api/inventory';
import type { InventoryItem, InventoryViewConfig } from '$lib/api/types';

export type InventoryHeaderNode = {
	kind: 'header';
	key: string;
	title: string;
	count: number;
	collapsed: boolean;
};

export type InventoryItemNode = {
	kind: 'item';
	key: string;
	item: InventoryItem;
};

export type InventoryRenderNode = InventoryHeaderNode | InventoryItemNode;

function normalizeTagList(item: InventoryItem): string[] {
	const tags = (item as any)?.tags;
	if (!Array.isArray(tags)) return [];
	return tags.filter((t) => typeof t === 'string' && t.trim());
}

function compileRegexList(patterns: string[]): RegExp[] {
	if (!Array.isArray(patterns) || patterns.length === 0) return [];
	const out: RegExp[] = [];
	for (const raw of patterns) {
		const pattern = typeof raw === 'string' ? raw.trim() : '';
		if (!pattern) continue;
		try {
			out.push(new RegExp(pattern, 'i'));
		} catch {
			// ignore invalid regex
		}
	}
	return out;
}

function isTagFilterActive(config: InventoryViewConfig | null): boolean {
	if (!config) return false;
	return (Array.isArray(config.whitelist) && config.whitelist.length > 0) || (Array.isArray(config.blacklist) && config.blacklist.length > 0);
}

function passesTagFilter(item: InventoryItem, whitelist: RegExp[], blacklist: RegExp[]): boolean {
	const tags = normalizeTagList(item);

	if (whitelist.length > 0) {
		if (tags.length === 0) return false;
		const ok = tags.some((tag) => whitelist.some((re) => re.test(tag)));
		if (!ok) return false;
	}

	if (blacklist.length > 0) {
		const blocked = tags.some((tag) => blacklist.some((re) => re.test(tag)));
		if (blocked) return false;
	}

	return true;
}

export function buildInventoryRenderNodes(
	items: InventoryItem[],
	filter: InventoryFilter,
	config: InventoryViewConfig | null
): InventoryRenderNode[] {
	const list = Array.isArray(items) ? items : [];
	const view = filter ?? 'all';

	let visible = list;
	if (view === 'items') {
		visible = visible.filter((x) => x.type === 'item');
	} else if (view === 'fluids') {
		visible = visible.filter((x) => x.type === 'fluid');
	}

	if (view !== 'fluids' && config) {
		const active = isTagFilterActive(config);
		if (active) {
			const whitelist = compileRegexList(config.whitelist);
			const blacklist = compileRegexList(config.blacklist);

			visible = visible.filter((x) => {
				if (x.type !== 'item') {
					// Fluids have no tags: hide only if whitelist is active (whitelist = show only matching)
					// Blacklist alone should not hide fluids (they can't match any blacklist pattern)
					return whitelist.length === 0;
				}
				return passesTagFilter(x, whitelist, blacklist);
			});
		}
	}

	const hasCategories = Boolean(config && Array.isArray(config.categories) && config.categories.length > 0);
	if (!hasCategories || view === 'fluids') {
		return visible.map((x) => ({ kind: 'item', key: x.uniqueId, item: x }));
	}

	const collapsed = config?.collapsed ?? {};
	const compiledCategories = (config?.categories ?? []).map((rule) => {
		const name = typeof rule?.name === 'string' ? rule.name.trim() : '';
		const tagRegex = typeof rule?.tagRegex === 'string' ? rule.tagRegex.trim() : '';
		if (!name || !tagRegex) return null;
		try {
			return { name, tagRegex, re: new RegExp(tagRegex, 'i') };
		} catch {
			return null;
		}
	});

	const itemsOnly = visible.filter((x) => x.type === 'item');
	const fluidsOnly = visible.filter((x) => x.type === 'fluid');

	const grouped: Record<string, InventoryItem[]> = {};
	const titles: Record<string, string> = {};

	for (const it of itemsOnly) {
		const tags = normalizeTagList(it);
		let bucketKey = 'Other';
		let bucketTitle = 'Other';

		for (const entry of compiledCategories) {
			if (!entry) continue;
			if (tags.some((tag) => entry.re.test(tag))) {
				bucketKey = `${entry.name}|${entry.tagRegex}`;
				bucketTitle = entry.name;
				break;
			}
		}

		(grouped[bucketKey] ??= []).push(it);
		titles[bucketKey] = bucketTitle;
	}

	const nodes: InventoryRenderNode[] = [];

	for (const entry of compiledCategories) {
		if (!entry) continue;
		const key = `${entry.name}|${entry.tagRegex}`;
		const bucket = grouped[key] ?? [];

		const isCollapsed = collapsed[key] === true;
		nodes.push({ kind: 'header', key, title: entry.name, count: bucket.length, collapsed: isCollapsed });
		if (!isCollapsed) {
			for (const it of bucket) {
				nodes.push({ kind: 'item', key: it.uniqueId, item: it });
			}
		}
	}

	const otherBucket = grouped['Other'] ?? [];
	if (otherBucket.length > 0) {
		const key = 'Other';
		const isCollapsed = collapsed[key] === true;
		nodes.push({ kind: 'header', key, title: titles[key] ?? 'Other', count: otherBucket.length, collapsed: isCollapsed });
		if (!isCollapsed) {
			for (const it of otherBucket) {
				nodes.push({ kind: 'item', key: it.uniqueId, item: it });
			}
		}
	}

	if (view === 'all' && fluidsOnly.length > 0) {
		const key = 'Fluids';
		const isCollapsed = collapsed[key] === true;
		nodes.push({ kind: 'header', key, title: 'Fluids', count: fluidsOnly.length, collapsed: isCollapsed });
		if (!isCollapsed) {
			for (const it of fluidsOnly) {
				nodes.push({ kind: 'item', key: it.uniqueId, item: it });
			}
		}
	}

	return nodes;
}
