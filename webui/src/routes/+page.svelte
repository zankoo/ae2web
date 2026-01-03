<script lang="ts">
	import { onDestroy, onMount, tick } from 'svelte';

	import debounce from 'lodash-es/debounce';

	import { fetchCpus } from '$lib/api/cpus';
	import { fetchCpuProcess } from '$lib/api/cpuProcess';
	import { submitCraft } from '$lib/api/craft';
	import { openInventorySse, type InventoryFilter } from '$lib/api/inventory';
	import { fetchInventoryViewConfig, saveInventoryViewConfig } from '$lib/api/userConfig';
	import { fetchTooltip } from '$lib/api/prerender';
	import { fetchStatus } from '$lib/api/status';
	import { fetchTerminals } from '$lib/api/terminals';
	import type {
		CpuDto,
		CpuProcessEntryDto,
		CpuProcessResponse,
		GenericStackDto,
		InventoryItem,
		InventoryViewConfig,
		TerminalInfo
	} from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import CpuDependencyTree from '$lib/components/CpuProcess/CpuDependencyTree.svelte';
	import CpuProcessTreeRow from '$lib/components/CpuProcess/CpuProcessTreeRow.svelte';
	import ToastHost from '$lib/components/ToastHost.svelte';
	import { authToken, setAuthToken } from '$lib/stores/session';
	import { showToast } from '$lib/stores/toast';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatFluidAmount, formatNumber } from '$lib/utils/format';
	import { arrayMove } from '$lib/utils/arrayMove';
	import {
		buildInventoryRenderNodes,
		type InventoryRenderNode
	} from '$lib/utils/inventoryGrouping';
	import { sortInventoryItems } from '$lib/utils/inventorySort';
	import { legacyTextToHtml } from '$lib/utils/minecraftText';
	import { matchItem } from '$lib/utils/pinyin';
	import { createTooltipPositioner, type TooltipPositioner } from '$lib/utils/tooltipPositioner';

	type SortMode = 'amount' | 'name';

	let online = false;
	let loginTokenValue = '';
	let loginError: string | null = null;
	$: needsLogin = !$authToken || !$authToken.trim();
	let appCleanup: (() => void) | null = null;

	let terminals: TerminalInfo[] = [];
	let terminalId = '';
	let terminalDisplayName = 'Loading...';
	let terminalDropdownOpen = false;
	let terminalSelectEl: HTMLDivElement | null = null;

	let filter: InventoryFilter = 'all';
	let sort: SortMode = 'amount';
	let searchValue = '';
	let searchTerm = '';
	let inventoryItems: InventoryItem[] = [];
	let visibleItems: InventoryItem[] = [];
	let totalItemAmount = 0;
	let typeCount = 0;
	let connectedTerminals: TerminalInfo[] = [];
	let cpuCount = 0;
	let statusText = 'OFFLINE';

	let inventoryScrollEl: HTMLDivElement | null = null;
	let inventoryGridEl: HTMLDivElement | null = null;
	let inventoryEventSource: EventSource | null = null;
	let lastInventoryEventVersion: number | null = null;

	let inventoryState = new Map<string, InventoryItem>();
	let inventoryStateDirty = false;
	let inventoryStateFlushInFlight = false;
	let inventoryStateFlushPending = false;

	let inventoryScrollActive = false;
	let inventoryScrollTimer: ReturnType<typeof setTimeout> | null = null;

	let cpus: CpuDto[] = [];
	let inventoryViewConfig: InventoryViewConfig = {
		whitelist: [],
		blacklist: [],
		categories: [],
		collapsed: {}
	};
	let inventoryViewConfigTerminalId: string | null = null;
	let inventoryRenderNodes: InventoryRenderNode[] = [];

	let inventorySettingsOpen = false;
	let inventorySettingsOverlayEl: HTMLDivElement | null = null;
	let inventorySettingsTerminalId: string | null = null;
	let inventorySettingsTerminalName: string | null = null;
	let inventorySettingsWhitelistText = '';
	let inventorySettingsBlacklistText = '';
	type InventoryCategoryDraft = { id: string; name: string; tagRegex: string };
	let inventorySettingsCategories: InventoryCategoryDraft[] = [];
	let inventorySettingsCategoryIdSeq = 0;
	let inventorySettingsCategoryDragId: string | null = null;
	let inventorySettingsCategoryDragOverId: string | null = null;
	let inventorySettingsSaving = false;
	let inventorySettingsError: string | null = null;

	let craftOpen = false;
	let craftItem: InventoryItem | null = null;
	let craftAmount = 1;
	let craftCpuName = '';
	let craftOverlayEl: HTMLDivElement | null = null;

	type CpuProcessView = 'list' | 'statusTree' | 'dependencyTree';

	let cpuProcessOpen = false;
	let cpuProcessOverlayEl: HTMLDivElement | null = null;
	let cpuProcessCpuId: string | null = null;
	let cpuProcessCpuName: string | null = null;
	let cpuProcessView: CpuProcessView = 'list';
	let cpuProcessAutoRefresh = true;
	let cpuProcessCompletionNotified = false;
	let cpuProcessLoading = false;
	let cpuProcessError: string | null = null;
	let cpuProcessData: CpuProcessResponse | null = null;
	let cpuProcessFilter = '';
	let cpuProcessTimer: number | null = null;

	let cpuProcessEntries: CpuProcessEntryDto[] = [];
	let cpuProcessFilterTerm = '';
	let cpuProcessEntriesFiltered: CpuProcessEntryDto[] = [];
	let cpuProcessEntriesActive: CpuProcessEntryDto[] = [];
	let cpuProcessEntriesWaiting: CpuProcessEntryDto[] = [];
	let cpuProcessEntriesStored: CpuProcessEntryDto[] = [];

	const tooltipCache = new Map<string, string[]>();
	let tooltipEl: HTMLDivElement | null = null;
	let tooltipVisible = false;
	let tooltipHtmlLines: string[] = [];
	let tooltipHoverKey: string | null = null;
	let tooltipPositioner: TooltipPositioner | null = null;
	let tooltipLastX = 0;
	let tooltipLastY = 0;
	let tooltipHasLastPos = false;

	async function flushInventoryState(): Promise<void> {
		if (document.hidden) return;
		if (!inventoryStateDirty) return;
		if (inventoryScrollActive) {
			inventoryStateFlushPending = true;
			return;
		}
		if (inventoryStateFlushInFlight) {
			inventoryStateFlushPending = true;
			return;
		}

		inventoryStateFlushInFlight = true;
		try {
			const nextItems = Array.from(inventoryState.values());
			const nextVisible = computeVisible(nextItems);

			const positionChanged = sort === 'amount' && hasPositionChanged(visibleItems, nextVisible);
			const preserveAnchor = positionChanged;

			inventoryItems = nextItems;
			inventoryStateDirty = false;
			await applyVisibleItems(nextVisible, preserveAnchor);
		} finally {
			inventoryStateFlushInFlight = false;
			if (inventoryStateFlushPending && !inventoryScrollActive && !document.hidden) {
				inventoryStateFlushPending = false;
				void flushInventoryState();
			}
		}
	}

	function onInventoryScroll(): void {
		inventoryScrollActive = true;
		if (inventoryScrollTimer) {
			clearTimeout(inventoryScrollTimer);
		}
		inventoryScrollTimer = setTimeout(() => {
			inventoryScrollTimer = null;
			inventoryScrollActive = false;
			if (inventoryStateDirty && !document.hidden) {
				void flushInventoryState();
			}
		}, 150);
	}

	const applySearchDebounced = debounce(() => {
		searchTerm = searchValue.trim();
		void recomputeInventory({ preserveAnchor: false });
	}, 500);

	function displayNameFor(item: InventoryItem): string {
		const name = item?.name;
		if (typeof name === 'string' && name.trim()) {
			return name;
		}
		if (item.id.includes(':')) {
			return item.id.split(':', 2)[1] || item.id;
		}
		return item.id;
	}

	function stopInventoryEventStream(): void {
		lastInventoryEventVersion = null;
		inventoryStateDirty = false;
		inventoryStateFlushInFlight = false;
		inventoryStateFlushPending = false;
		if (inventoryScrollTimer) {
			clearTimeout(inventoryScrollTimer);
			inventoryScrollTimer = null;
		}
		if (inventoryEventSource) {
			try {
				inventoryEventSource.close();
			} catch {
				// ignore
			}
			inventoryEventSource = null;
		}
	}

	function startInventoryEventStream(): void {
		stopInventoryEventStream();
		if (!terminalId) return;

		inventoryEventSource = openInventorySse(terminalId, {
			onHello: (msg) => {
				lastInventoryEventVersion = msg.version ?? null;
			},
			onSnapshot: (msg) => {
				if (msg.terminalId && msg.terminalId !== terminalId) return;

				const items = Array.isArray(msg.items) ? msg.items : [];
				inventoryState = new Map(items.map((item) => [item.uniqueId, item]));
				inventoryStateDirty = true;
				lastInventoryEventVersion = msg.version ?? lastInventoryEventVersion;
				void flushInventoryState();
			},
			onDelta: (msg) => {
				if (msg.terminalId && msg.terminalId !== terminalId) return;
				if (msg.version == null) return;
				if (lastInventoryEventVersion == null) return;
				if (msg.version !== lastInventoryEventVersion + 1) {
					showToast('Inventory stream out of sync, resyncing...', 'error');
					startInventoryEventStream();
					return;
				}

				lastInventoryEventVersion = msg.version;

				const removed = Array.isArray(msg.removed) ? msg.removed : [];
				for (const id of removed) {
					inventoryState.delete(id);
				}

				const added = Array.isArray(msg.added) ? msg.added : [];
				for (const item of added) {
					if (!item?.uniqueId) continue;
					inventoryState.set(item.uniqueId, item);
				}

				const updated = Array.isArray(msg.updated) ? msg.updated : [];
				for (const patch of updated) {
					const prev = inventoryState.get(patch.uniqueId);
					if (!prev) continue;

					const nextName =
						typeof patch.name === 'string' && patch.name.trim() ? patch.name : prev.name;
					inventoryState.set(patch.uniqueId, {
						...prev,
						amount: patch.amount,
						craftable: patch.craftable,
						name: nextName
					});
				}

				inventoryStateDirty = true;
				void flushInventoryState();
			}
		});
	}

	async function checkConnection(): Promise<void> {
		try {
			await fetchStatus();
			online = true;
		} catch (e) {
			online = false;
			const message = e instanceof Error ? e.message : '';
			if (message.toLowerCase().includes('unauthorized') || message.includes('HTTP 401')) {
				showToast('Invalid token', 'error');
				setAuthToken('');
			}
		}
	}

	function getConnectedTerminals(list: TerminalInfo[]): TerminalInfo[] {
		return list.filter((t) => t.connected);
	}

	async function loadTerminalsPoll(): Promise<void> {
		try {
			const data = await fetchTerminals();
			terminals = Array.isArray(data.terminals) ? data.terminals : [];

			const connected = getConnectedTerminals(terminals);
			if (connected.length === 0) {
				terminalDisplayName = terminals.length === 0 ? 'No terminals' : 'No connected terminals';
				if (terminalId) {
					terminalId = '';
					stopInventoryEventStream();
				}
				inventoryItems = [];
				inventoryState = new Map();
				visibleItems = [];
				cpus = [];
				return;
			}

			const previous = terminalId;
			const stillConnected = previous && connected.some((t) => t.id === previous);
			const effective = stillConnected ? connected.find((t) => t.id === previous)! : connected[0];

			terminalId = effective.id;
			terminalDisplayName = effective.displayName;

			if (terminalId !== previous) {
				await loadCpus();
				startInventoryEventStream();
				await loadInventoryViewConfigForTerminal(terminalId);
			}
		} catch (e) {
			console.error('Failed to load terminals', e);
		}
	}

	function captureScrollAnchor(): { id: string; topOffset: number } | null {
		if (!inventoryScrollEl || !inventoryGridEl) return null;

		try {
			const scrollRect = inventoryScrollEl.getBoundingClientRect();
			const x = scrollRect.left + Math.min(16, scrollRect.width / 2);
			const y = scrollRect.top + 16;
			const hit = document.elementFromPoint(x, y) as Element | null;
			const slot = hit?.closest?.('.item-slot') as HTMLElement | null;
			if (slot && inventoryGridEl.contains(slot)) {
				const id = slot.dataset.itemId;
				if (!id) return null;
				const rect = slot.getBoundingClientRect();
				return { id, topOffset: rect.top - scrollRect.top };
			}
		} catch {
			// ignore
		}
		return null;
	}

	function escapeCssAttr(value: string): string {
		if (window.CSS && typeof window.CSS.escape === 'function') {
			return window.CSS.escape(value);
		}
		return String(value).replace(/["\\]/g, '\\$&');
	}

	function restoreScrollAnchor(anchor: { id: string; topOffset: number } | null): number {
		if (!anchor?.id || !inventoryScrollEl || !inventoryGridEl) return 0;

		try {
			const selector = `[data-item-id="${escapeCssAttr(anchor.id)}"]`;
			const el = inventoryGridEl.querySelector(selector) as HTMLElement | null;
			if (!el) return 0;

			const scrollRect = inventoryScrollEl.getBoundingClientRect();
			const rect = el.getBoundingClientRect();
			const newOffset = rect.top - scrollRect.top;
			const delta = newOffset - anchor.topOffset;
			if (Number.isFinite(delta) && Math.abs(delta) > 0.5) {
				inventoryScrollEl.scrollTop += delta;
				return delta;
			}
		} catch {
			// ignore
		}
		return 0;
	}

	function computeVisible(items: InventoryItem[]): InventoryItem[] {
		let out = Array.isArray(items) ? items : [];

		if (filter === 'items') {
			out = out.filter((item) => item.type === 'item');
		} else if (filter === 'fluids') {
			out = out.filter((item) => item.type === 'fluid');
		}

		if (searchTerm) {
			out = out.filter((item) => {
				const name = displayNameFor(item);
				return matchItem(name, searchTerm) || matchItem(item.id, searchTerm);
			});
		}

		return sortInventoryItems(out, sort);
	}

	function hasPositionChanged(previous: InventoryItem[], next: InventoryItem[]): boolean {
		if (!Array.isArray(previous) || previous.length === 0) return false;
		if (!Array.isArray(next) || next.length === 0) return false;

		const prevIndexById = new Map<string, number>();
		for (let i = 0; i < previous.length; i++) {
			prevIndexById.set(previous[i].uniqueId, i);
		}

		for (let i = 0; i < next.length; i++) {
			const prevIndex = prevIndexById.get(next[i].uniqueId);
			if (prevIndex != null && prevIndex !== i) {
				return true;
			}
		}

		return false;
	}

	async function applyVisibleItems(
		nextVisible: InventoryItem[],
		preserveAnchor: boolean
	): Promise<void> {
		const anchor = preserveAnchor ? captureScrollAnchor() : null;
		visibleItems = nextVisible;
		await tick();

		if (preserveAnchor) {
			restoreScrollAnchor(anchor);
		}
	}

	async function recomputeInventory({
		preserveAnchor
	}: {
		preserveAnchor: boolean;
	}): Promise<void> {
		await applyVisibleItems(computeVisible(inventoryItems), preserveAnchor);
	}

	async function loadCpus(): Promise<void> {
		if (!terminalId) {
			cpus = [];
			return;
		}

		try {
			const data = await fetchCpus(terminalId);
			cpus = Array.isArray(data.cpus) ? data.cpus : [];
		} catch (e) {
			console.error('Failed to load CPUs', e);
		}
	}

	async function refreshData(): Promise<void> {
		await checkConnection();
		startInventoryEventStream();
	}

	function openTerminalDropdown(): void {
		terminalDropdownOpen = !terminalDropdownOpen;
	}

	function selectTerminal(next: TerminalInfo): void {
		const previous = terminalId;
		terminalId = next.id;
		terminalDisplayName = next.displayName;
		terminalDropdownOpen = false;

		if (terminalId !== previous) {
			closeCpuProcessModal();
			inventoryItems = [];
			inventoryState = new Map();
			visibleItems = [];
			void loadCpus();
			startInventoryEventStream();
			void loadInventoryViewConfigForTerminal(terminalId);
		}
	}

	function normalizeInventoryViewConfig(
		raw: InventoryViewConfig | null | undefined
	): InventoryViewConfig {
		const whitelist = Array.isArray(raw?.whitelist)
			? raw!.whitelist.filter((x) => typeof x === 'string' && x.trim())
			: [];
		const blacklist = Array.isArray(raw?.blacklist)
			? raw!.blacklist.filter((x) => typeof x === 'string' && x.trim())
			: [];
		const categories = Array.isArray(raw?.categories)
			? raw!.categories
					.filter(
						(x) =>
							x &&
							typeof x.name === 'string' &&
							x.name.trim() &&
							typeof x.tagRegex === 'string' &&
							x.tagRegex.trim()
					)
					.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
			: [];
		const collapsed =
			raw?.collapsed && typeof raw.collapsed === 'object'
				? { ...(raw.collapsed as Record<string, boolean>) }
				: {};
		return { whitelist, blacklist, categories, collapsed };
	}

	function isUnauthorizedError(e: unknown): boolean {
		const message = e instanceof Error ? e.message : String(e ?? '');
		return message.toLowerCase().includes('unauthorized') || message.includes('HTTP 401');
	}

	async function loadInventoryViewConfigForTerminal(nextTerminalId: string): Promise<void> {
		if (!nextTerminalId) {
			inventoryViewConfig = { whitelist: [], blacklist: [], categories: [], collapsed: {} };
			inventoryViewConfigTerminalId = null;
			return;
		}
		if (inventoryViewConfigTerminalId === nextTerminalId) {
			return;
		}

		try {
			const cfg = await fetchInventoryViewConfig(nextTerminalId);
			inventoryViewConfig = normalizeInventoryViewConfig(cfg);
			inventoryViewConfigTerminalId = nextTerminalId;
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			inventoryViewConfig = { whitelist: [], blacklist: [], categories: [], collapsed: {} };
			inventoryViewConfigTerminalId = nextTerminalId;
		}
	}

	const saveInventoryViewConfigDebounced = debounce(
		(targetTerminalId: string, cfg: InventoryViewConfig) => {
			void (async () => {
				if (!targetTerminalId) return;
				try {
					const saved = await saveInventoryViewConfig(targetTerminalId, cfg);
					if (terminalId === targetTerminalId) {
						inventoryViewConfig = normalizeInventoryViewConfig(saved);
						inventoryViewConfigTerminalId = targetTerminalId;
					}
				} catch (e) {
					if (isUnauthorizedError(e)) {
						showToast('Invalid token', 'error');
						setAuthToken('');
					}
				}
			})();
		},
		600
	);

	function toggleInventorySectionCollapsed(key: string): void {
		if (!key) return;
		const current = inventoryViewConfig?.collapsed ?? {};
		const next = { ...current, [key]: !(current[key] === true) };
		inventoryViewConfig = { ...inventoryViewConfig, collapsed: next };
		if (terminalId) {
			saveInventoryViewConfigDebounced(terminalId, inventoryViewConfig);
		}
	}

	function onInventorySettingsOverlayClick(e: MouseEvent): void {
		if (e.target === inventorySettingsOverlayEl) {
			closeInventorySettingsModal();
		}
	}

	async function openInventorySettingsModalForTerminal(terminal: TerminalInfo): Promise<void> {
		if (!terminal?.id) return;
		inventorySettingsOpen = true;
		inventorySettingsTerminalId = terminal.id;
		inventorySettingsTerminalName = terminal.displayName;
		inventorySettingsSaving = false;
		inventorySettingsError = null;

		try {
			const cfg =
				terminal.id === inventoryViewConfigTerminalId
					? inventoryViewConfig
					: await fetchInventoryViewConfig(terminal.id);
			const normalized = normalizeInventoryViewConfig(cfg);
			inventorySettingsWhitelistText = normalized.whitelist.join('\n');
			inventorySettingsBlacklistText = normalized.blacklist.join('\n');
			inventorySettingsCategories = normalized.categories.map((x) =>
				createInventoryCategoryDraft(x)
			);
			clearInventoryCategoryDrag();
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			inventorySettingsWhitelistText = '';
			inventorySettingsBlacklistText = '';
			inventorySettingsCategories = [];
			inventorySettingsError = e instanceof Error ? e.message : 'Failed to load settings';
		}
	}

	function closeInventorySettingsModal(): void {
		inventorySettingsOpen = false;
		inventorySettingsTerminalId = null;
		inventorySettingsTerminalName = null;
		inventorySettingsSaving = false;
		inventorySettingsError = null;
		clearInventoryCategoryDrag();
	}

	function createInventoryCategoryDraft(rule?: {
		name?: string;
		tagRegex?: string;
	}): InventoryCategoryDraft {
		inventorySettingsCategoryIdSeq += 1;
		return {
			id: `cat_${inventorySettingsCategoryIdSeq}`,
			name: typeof rule?.name === 'string' ? rule.name : '',
			tagRegex: typeof rule?.tagRegex === 'string' ? rule.tagRegex : ''
		};
	}

	function clearInventoryCategoryDrag(): void {
		inventorySettingsCategoryDragId = null;
		inventorySettingsCategoryDragOverId = null;
	}

	function onInventoryCategoryDragStart(e: DragEvent, id: string): void {
		if (!id || inventorySettingsSaving) return;
		inventorySettingsCategoryDragId = id;
		inventorySettingsCategoryDragOverId = id;

		try {
			e.dataTransfer?.setData('text/plain', id);
		} catch {
			// ignore
		}

		if (e.dataTransfer) {
			e.dataTransfer.effectAllowed = 'move';
		}
	}

	function onInventoryCategoryDragOver(e: DragEvent, overId: string): void {
		if (!inventorySettingsCategoryDragId || inventorySettingsSaving) return;
		e.preventDefault();

		if (inventorySettingsCategoryDragOverId !== overId) {
			inventorySettingsCategoryDragOverId = overId;
		}

		if (e.dataTransfer) {
			e.dataTransfer.dropEffect = 'move';
		}
	}

	function onInventoryCategoryDrop(e: DragEvent, dropId: string): void {
		const fromId = inventorySettingsCategoryDragId;
		if (!fromId || inventorySettingsSaving) return;
		e.preventDefault();

		const fromIndex = inventorySettingsCategories.findIndex((x) => x.id === fromId);
		const toIndex = inventorySettingsCategories.findIndex((x) => x.id === dropId);
		if (fromIndex === -1 || toIndex === -1) {
			clearInventoryCategoryDrag();
			return;
		}

		inventorySettingsCategories = arrayMove(inventorySettingsCategories, fromIndex, toIndex);
		clearInventoryCategoryDrag();
	}

	function onInventoryCategoryDragEnd(): void {
		clearInventoryCategoryDrag();
	}

	function addInventoryCategoryRule(): void {
		inventorySettingsCategories = [...inventorySettingsCategories, createInventoryCategoryDraft()];
	}

	function removeInventoryCategoryRule(id: string): void {
		if (!id) return;
		inventorySettingsCategories = inventorySettingsCategories.filter((x) => x.id !== id);
	}

	function parseRegexLines(text: string): string[] {
		return String(text)
			.split('\n')
			.map((x) => x.trim())
			.filter(Boolean);
	}

	function validateRegex(pattern: string): string | null {
		try {
			// eslint-disable-next-line no-new
			new RegExp(pattern, 'i');
			return null;
		} catch {
			return `Invalid regex: ${pattern}`;
		}
	}

	async function saveInventorySettings(): Promise<void> {
		if (!inventorySettingsTerminalId) return;
		inventorySettingsSaving = true;
		inventorySettingsError = null;

		const whitelist = parseRegexLines(inventorySettingsWhitelistText);
		const blacklist = parseRegexLines(inventorySettingsBlacklistText);
		for (const p of [...whitelist, ...blacklist]) {
			const err = validateRegex(p);
			if (err) {
				inventorySettingsError = err;
				inventorySettingsSaving = false;
				return;
			}
		}

		const categories = inventorySettingsCategories
			.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
			.filter((x) => x.name && x.tagRegex);
		for (const c of categories) {
			const err = validateRegex(c.tagRegex);
			if (err) {
				inventorySettingsError = err;
				inventorySettingsSaving = false;
				return;
			}
		}

		const nextCfg: InventoryViewConfig = {
			whitelist,
			blacklist,
			categories,
			collapsed:
				inventoryViewConfigTerminalId === inventorySettingsTerminalId
					? inventoryViewConfig.collapsed
					: {}
		};

		try {
			const saved = await saveInventoryViewConfig(inventorySettingsTerminalId, nextCfg);
			if (terminalId === inventorySettingsTerminalId) {
				inventoryViewConfig = normalizeInventoryViewConfig(saved);
				inventoryViewConfigTerminalId = inventorySettingsTerminalId;
			}
			closeInventorySettingsModal();
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			inventorySettingsError = e instanceof Error ? e.message : 'Failed to save settings';
		} finally {
			inventorySettingsSaving = false;
		}
	}

	function setFilter(next: InventoryFilter): void {
		if (filter === next) return;
		filter = next;
		void recomputeInventory({ preserveAnchor: false });
	}

	async function setSort(next: SortMode): Promise<void> {
		if (sort === next) return;
		sort = next;
		const nextVisible = computeVisible(inventoryItems);
		await applyVisibleItems(nextVisible, next === 'amount');
	}

	function executeSearchNow(): void {
		applySearchDebounced.cancel();
		searchTerm = searchValue.trim();
		void recomputeInventory({ preserveAnchor: false });
	}

	function submitLoginToken(): void {
		const token = loginTokenValue.trim();
		if (!token) {
			loginError = 'Token required';
			return;
		}
		loginError = null;
		setAuthToken(token);
		loginTokenValue = '';
	}

	function openCraftModal(item: InventoryItem): void {
		craftItem = item;
		craftAmount = 1;
		craftCpuName = '';
		craftOpen = true;
	}

	function closeCraftModal(): void {
		craftOpen = false;
		craftItem = null;
	}

	function openCpuProcessModal(cpu: CpuDto): void {
		if (!cpu?.busy) return;
		if (!cpu.cpuId) return;
		if (!terminalId) return;

		cpuProcessOpen = true;
		cpuProcessCpuId = cpu.cpuId;
		cpuProcessCpuName = cpu.name;
		cpuProcessView = 'list';
		cpuProcessAutoRefresh = true;
		cpuProcessCompletionNotified = false;
		cpuProcessLoading = false;
		cpuProcessError = null;
		cpuProcessData = null;
		cpuProcessFilter = '';

		syncCpuProcessTimer();
	}

	function closeCpuProcessModal(): void {
		cpuProcessOpen = false;
		cpuProcessCpuId = null;
		cpuProcessCpuName = null;
		cpuProcessError = null;
		cpuProcessData = null;
		stopCpuProcessTimer();
	}

	function stopCpuProcessTimer(): void {
		if (cpuProcessTimer != null) {
			window.clearInterval(cpuProcessTimer);
			cpuProcessTimer = null;
		}
	}

	function syncCpuProcessTimer(): void {
		stopCpuProcessTimer();

		if (!cpuProcessOpen) return;
		void loadCpuProcess();

		if (!cpuProcessAutoRefresh) return;
		cpuProcessTimer = window.setInterval(() => void loadCpuProcess(), 400);
	}

	async function loadCpuProcess(): Promise<void> {
		if (!cpuProcessOpen) return;
		if (!terminalId) return;
		if (!cpuProcessCpuId) return;
		if (cpuProcessLoading) return;

		cpuProcessLoading = true;
		try {
			const data = await fetchCpuProcess(terminalId, cpuProcessCpuId);
			cpuProcessData = data;
			cpuProcessCpuName = data?.name ?? cpuProcessCpuName;
			cpuProcessError = null;

			if (data && data.busy === false) {
				cpuProcessAutoRefresh = false;
				stopCpuProcessTimer();
				if (!cpuProcessCompletionNotified) {
					const outputName = data?.summary?.finalOutput?.name;
					showToast(
						outputName ? `Crafting complete: ${outputName}` : 'Crafting complete',
						'success'
					);
					cpuProcessCompletionNotified = true;
				}
			}
		} catch (e) {
			const message = e instanceof Error ? e.message : 'Failed to load CPU process';
			if (message.toLowerCase().includes('not busy')) {
				cpuProcessAutoRefresh = false;
				stopCpuProcessTimer();
				cpuProcessError = null;

				const lastSummary: CpuProcessResponse['summary'] = cpuProcessData?.summary ?? {};
				const outputTotal = Number(lastSummary.outputTotal ?? 0);
				const nextSummary =
					outputTotal > 0
						? { ...lastSummary, outputDone: outputTotal, outputRemaining: 0 }
						: lastSummary;

				cpuProcessData = {
					cpuId: cpuProcessCpuId,
					busy: false,
					name: cpuProcessCpuName ?? null,
					summary: nextSummary,
					entries: [],
					patterns: []
				};

				if (!cpuProcessCompletionNotified) {
					const outputName = cpuProcessData?.summary?.finalOutput?.name;
					showToast(
						outputName ? `Crafting complete: ${outputName}` : 'Crafting complete',
						'success'
					);
					cpuProcessCompletionNotified = true;
				}
				return;
			}
			cpuProcessError = message;
		} finally {
			cpuProcessLoading = false;
		}
	}

	async function confirmCraft(): Promise<void> {
		if (!craftItem) return;

		try {
			const body: any = {
				id: craftItem.id,
				type: craftItem.type,
				amount: craftAmount,
				terminalId
			};
			if (craftCpuName) {
				body.cpuName = craftCpuName;
			}

			await submitCraft(body);
			const amountText = craftItem.type === 'fluid' ? `${craftAmount}B` : `${craftAmount}x`;
			showToast(`Request sent: ${amountText} ${displayNameFor(craftItem)}`, 'success');
			closeCraftModal();
			void loadCpus();
		} catch (e) {
			showToast(e instanceof Error ? e.message : 'Craft request failed', 'error');
		}
	}

	function ensureTooltipPositioner(): TooltipPositioner | null {
		if (!tooltipEl) return null;
		if (tooltipPositioner) return tooltipPositioner;

		tooltipPositioner = createTooltipPositioner({
			requestAnimationFrame: (cb) => window.requestAnimationFrame(cb),
			cancelAnimationFrame: (id) => window.cancelAnimationFrame(id),
			getTooltipSize: () => {
				const rect = tooltipEl?.getBoundingClientRect();
				return { width: rect?.width ?? 0, height: rect?.height ?? 0 };
			},
			getViewportSize: () => ({ width: window.innerWidth, height: window.innerHeight }),
			applyPosition: (left, top) => {
				if (!tooltipEl) return;
				tooltipEl.style.transform = `translate3d(${left}px, ${top}px, 0)`;
			}
		});

		return tooltipPositioner;
	}

	function renderTooltip(lines: string[]): void {
		const normalized = Array.isArray(lines) ? lines.filter(Boolean) : [];
		if (normalized.length === 0) {
			tooltipHtmlLines = [legacyTextToHtml('(no tooltip)')];
		} else {
			tooltipHtmlLines = normalized.map((line) => legacyTextToHtml(line));
		}

		tooltipVisible = true;
		const positioner = ensureTooltipPositioner();
		positioner?.invalidateSize();
		if (tooltipHasLastPos) {
			positioner?.onMouseMove(tooltipLastX, tooltipLastY);
		}
	}

	function showTooltipLoading(): void {
		tooltipHtmlLines = ['Loading...'].map((line) => legacyTextToHtml(line));
		tooltipVisible = true;
		const positioner = ensureTooltipPositioner();
		positioner?.invalidateSize();
	}

	function hideTooltip(): void {
		tooltipVisible = false;
		tooltipPositioner?.cancel();
	}

	async function requestTooltip(iconKey: string): Promise<void> {
		if (tooltipCache.has(iconKey)) {
			if (tooltipHoverKey === iconKey) {
				renderTooltip(tooltipCache.get(iconKey) ?? []);
			}
			return;
		}

		try {
			const data = await fetchTooltip(iconKey);
			const tooltip = Array.isArray(data?.tooltip) ? data.tooltip.filter(Boolean) : [];
			tooltipCache.set(iconKey, tooltip);

			if (tooltipHoverKey === iconKey) {
				renderTooltip(tooltip);
			}
		} catch {
			if (tooltipHoverKey === iconKey) {
				renderTooltip(['(tooltip not prerendered yet)']);
			}
		}
	}

	function onGridMouseOver(e: MouseEvent): void {
		const target = e.target as Element | null;
		const slot = target?.closest?.('.item-slot') as HTMLElement | null;
		if (!slot || !inventoryGridEl?.contains(slot)) return;

		const iconKey = slot.dataset.iconId;
		if (!iconKey) return;

		tooltipLastX = e.clientX;
		tooltipLastY = e.clientY;
		tooltipHasLastPos = true;

		tooltipHoverKey = iconKey;
		showTooltipLoading();
		ensureTooltipPositioner()?.onMouseMove(tooltipLastX, tooltipLastY);
		void requestTooltip(iconKey);
	}

	function onGridMouseMove(e: MouseEvent): void {
		if (!tooltipVisible || !tooltipEl) return;
		tooltipLastX = e.clientX;
		tooltipLastY = e.clientY;
		tooltipHasLastPos = true;
		ensureTooltipPositioner()?.onMouseMove(tooltipLastX, tooltipLastY);
	}

	function onGridMouseOut(e: MouseEvent): void {
		const fromSlot = (e.target as Element | null)?.closest?.('.item-slot');
		const toSlot = (e.relatedTarget as Element | null)?.closest?.('.item-slot');
		if (fromSlot && fromSlot === toSlot) return;
		tooltipHoverKey = null;
		tooltipHasLastPos = false;
		hideTooltip();
	}

	function onOverlayClick(e: MouseEvent): void {
		if (e.target === craftOverlayEl) {
			closeCraftModal();
		}
	}

	function onCpuProcessOverlayClick(e: MouseEvent): void {
		if (e.target === cpuProcessOverlayEl) {
			closeCpuProcessModal();
		}
	}

	function formatElapsedNanos(elapsedTimeNanos: number | undefined): string {
		const nanos = Number(elapsedTimeNanos);
		if (!Number.isFinite(nanos) || nanos <= 0) return '0:00';

		const totalSeconds = Math.floor(nanos / 1_000_000_000);
		const hours = Math.floor(totalSeconds / 3600);
		const minutes = Math.floor((totalSeconds % 3600) / 60);
		const seconds = totalSeconds % 60;

		const mm = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
		const ss = String(seconds).padStart(2, '0');
		return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
	}

	function onSearchKeyUp(e: KeyboardEvent): void {
		if (e.key === 'Enter') {
			executeSearchNow();
		}
	}

	function startApp(): () => void {
		void loadTerminalsPoll();
		void checkConnection();
		void loadCpus();

		const statusTimer = window.setInterval(() => void checkConnection(), 5000);
		const terminalsTimer = window.setInterval(() => void loadTerminalsPoll(), 10000);
		const cpuTimer = window.setInterval(() => void loadCpus(), 2000);
		inventoryScrollEl?.addEventListener('scroll', onInventoryScroll, { passive: true });

		const onDocClick = (e: MouseEvent) => {
			if (!terminalDropdownOpen) return;
			const target = e.target as Node | null;
			if (terminalSelectEl && target && !terminalSelectEl.contains(target)) {
				terminalDropdownOpen = false;
			}
		};

		const onKeyDown = (e: KeyboardEvent) => {
			if (e.key === 'Escape') {
				closeCraftModal();
				closeCpuProcessModal();
				closeInventorySettingsModal();
				terminalDropdownOpen = false;
			}
		};

		document.addEventListener('click', onDocClick);
		document.addEventListener('keydown', onKeyDown);

		return () => {
			clearInterval(statusTimer);
			clearInterval(terminalsTimer);
			clearInterval(cpuTimer);
			stopCpuProcessTimer();
			inventoryScrollEl?.removeEventListener('scroll', onInventoryScroll);
			document.removeEventListener('click', onDocClick);
			document.removeEventListener('keydown', onKeyDown);
			stopInventoryEventStream();
			applySearchDebounced.cancel();
			saveInventoryViewConfigDebounced.cancel();
		};
	}

	onMount(() => {
		const unsubscribe = authToken.subscribe((token) => {
			const hasToken = Boolean(String(token ?? '').trim());
			if (!hasToken) {
				if (appCleanup) {
					appCleanup();
					appCleanup = null;
				}
				return;
			}

			if (!appCleanup) {
				appCleanup = startApp();
			}
		});

		return () => {
			unsubscribe();
			if (appCleanup) {
				appCleanup();
				appCleanup = null;
			}
		};
	});

	onDestroy(() => stopInventoryEventStream());
	onDestroy(() => stopCpuProcessTimer());

	$: totalItemAmount = visibleItems.reduce((sum, item) => {
		const amount = Number(item?.amount);
		return sum + (Number.isFinite(amount) ? amount : 0);
	}, 0);

	$: typeCount = visibleItems.length;
	$: connectedTerminals = getConnectedTerminals(terminals);
	$: cpuCount = cpus.length;
	$: statusText = online ? 'ONLINE' : 'OFFLINE';
	$: inventoryRenderNodes = buildInventoryRenderNodes(
		visibleItems,
		filter,
		inventoryViewConfigTerminalId === terminalId ? inventoryViewConfig : null
	);

	$: cpuProcessEntries = Array.isArray(cpuProcessData?.entries) ? cpuProcessData.entries : [];
	$: cpuProcessFilterTerm = cpuProcessFilter.trim().toLowerCase();
	$: cpuProcessEntriesFiltered = cpuProcessFilterTerm
		? cpuProcessEntries.filter((e) => {
				const stack = e?.stack;
				const name = String(stack?.name ?? '').toLowerCase();
				const id = String(stack?.id ?? '').toLowerCase();
				return name.includes(cpuProcessFilterTerm) || id.includes(cpuProcessFilterTerm);
			})
		: cpuProcessEntries;

	$: cpuProcessEntriesActive = cpuProcessEntriesFiltered.filter((e) => Number(e?.active ?? 0) > 0);
	$: cpuProcessEntriesWaiting = cpuProcessEntriesFiltered.filter(
		(e) => Number(e?.waiting ?? 0) > 0
	);
	$: cpuProcessEntriesStored = cpuProcessEntriesFiltered.filter((e) => Number(e?.stored ?? 0) > 0);
</script>

<div class="terminal-container">
	<header class="terminal-header">
		<div class="network-selector">
			<div class="network-frame">
				<div class="network-indicator">
					<svg
						class="network-icon"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path
							d="M12 2v4M5 9c0 0 3-2 7-2s7 2 7 2M6 13c0 0 2-1 6-1s6 1 6 1"
							stroke-linecap="square"
						/>
						<rect x="11" y="16" width="2" height="6" fill="currentColor" stroke="none" />
						<circle cx="12" cy="15" r="2" fill="currentColor" stroke="none" />
					</svg>
					<div class="signal-rings">
						<div class="ring ring-1"></div>
						<div class="ring ring-2"></div>
						<div class="ring ring-3"></div>
					</div>
				</div>
				<div class="network-select-wrapper">
					<span class="network-label">NETWORK LINK</span>
					<div class="network-select-row">
						<div
							class="ae2-select-container"
							class:open={terminalDropdownOpen}
							id="terminal-select-container"
							bind:this={terminalSelectEl}
						>
							<div
								class="ae2-select-trigger"
								id="terminal-select-trigger"
								role="button"
								tabindex="0"
								aria-expanded={terminalDropdownOpen}
								on:click|stopPropagation={openTerminalDropdown}
								on:keydown={(e) => {
									if (e.key === 'Enter' || e.key === ' ') {
										e.preventDefault();
										openTerminalDropdown();
									}
								}}
							>
								<span id="terminal-selected-text">{terminalDisplayName}</span>
								<svg
									class="select-arrow"
									viewBox="0 0 24 24"
									fill="none"
									stroke="currentColor"
									stroke-width="2"
								>
									<polyline points="6 9 12 15 18 9" />
								</svg>
							</div>
							<div
								class="ae2-select-dropdown"
								class:hidden={!terminalDropdownOpen}
								id="terminal-dropdown-list"
							>
								{#if connectedTerminals.length === 0}
									<div class="ae2-select-option">No terminals found</div>
								{:else}
									{#each connectedTerminals as t (t.id)}
										<div
											class={`ae2-select-option ${t.id === terminalId ? 'selected' : ''}`}
											data-value={t.id}
											data-name={t.displayName}
											role="option"
											tabindex="0"
											aria-selected={t.id === terminalId}
											on:click={() => selectTerminal(t)}
											on:keydown={(e) => {
												if (e.key === 'Enter' || e.key === ' ') {
													e.preventDefault();
													selectTerminal(t);
												}
											}}
										>
											{t.displayName}
										</div>
									{/each}
								{/if}
							</div>
						</div>

						<button
							class="network-settings-btn"
							type="button"
							aria-label="Inventory Settings"
							title="Inventory Settings"
							disabled={!terminalId}
							on:click={() =>
								terminalId &&
								void openInventorySettingsModalForTerminal({
									id: terminalId,
									displayName: terminalDisplayName,
									connected: true
								})}
						>
							<svg
								viewBox="0 0 24 24"
								fill="none"
								xmlns="http://www.w3.org/2000/svg"
								width="1em"
								height="1em"
								focusable="false"
								aria-hidden="true"
								><path
									fill-rule="evenodd"
									clip-rule="evenodd"
									d="M8 5.07c-.8.47-1.8.46-2.6 0l-.24-.15a1.4 1.4 0 0 0-1.82.29c-.64.82-1.16 1.73-1.55 2.7a1.4 1.4 0 0 0 .66 1.71l.24.14a2.57 2.57 0 0 1 0 4.48l-.24.14a1.4 1.4 0 0 0-.66 1.72 11.1 11.1 0 0 0 1.55 2.69 1.4 1.4 0 0 0 1.82.3l.25-.15a2.57 2.57 0 0 1 3.88 2.24v.28c-.01.7.46 1.33 1.16 1.43a11 11 0 0 0 3.1 0 1.4 1.4 0 0 0 1.17-1.43v-.28a2.57 2.57 0 0 1 3.87-2.24l.25.14c.6.36 1.38.26 1.82-.29.64-.82 1.16-1.73 1.55-2.7a1.4 1.4 0 0 0-.66-1.71l-.24-.14a2.57 2.57 0 0 1 0-4.48l.24-.14a1.4 1.4 0 0 0 .66-1.72 11.08 11.08 0 0 0-1.55-2.69 1.4 1.4 0 0 0-1.82-.3l-.25.15a2.57 2.57 0 0 1-3.88-2.24v-.28c.01-.7-.46-1.33-1.16-1.43a10.99 10.99 0 0 0-3.1 0c-.7.1-1.17.72-1.17 1.43v.28C9.3 3.75 8.8 4.61 8 5.07Zm6 10.4a4 4 0 1 0-4-6.93 4 4 0 0 0 4 6.92Z"
									fill="currentColor"
								></path></svg
							>
						</button>
					</div>
				</div>
				<div class="connection-bar"></div>
			</div>
		</div>

		<div class="logo-section">
			<div class="logo-icon">
				<img id="logo-img" src="logo.svg" alt="Terminal" />
			</div>
			<div class="logo-text">
				<h1>AE2 Web Terminal</h1>
				<span>ME Network Interface</span>
			</div>
		</div>

		<div class="status-section">
			<div class="stat-box">
				<div class="stat-label">Total Items</div>
				<div class="stat-value items" id="total-items">{formatAmount(totalItemAmount)}</div>
			</div>
			<div class="stat-box">
				<div class="stat-label">Types</div>
				<div class="stat-value" id="item-types">{formatNumber(typeCount)}</div>
			</div>
			<div class="status-indicator">
				<div id="status-led" class={`status-led ${online ? '' : 'offline'}`}></div>
				<span id="status-text" class={online ? 'online' : 'offline'}>{statusText}</span>
			</div>
		</div>
	</header>

	<main class="terminal-main">
		<section class="inventory-panel">
			<div class="panel-header">
				<div class="panel-title">Network Storage</div>
				<div class="sort-toggle">
					<button
						class={`filter-btn ${sort === 'amount' ? 'active' : ''}`}
						type="button"
						on:click={() => setSort('amount')}>Amount</button
					>
					<button
						class={`filter-btn ${sort === 'name' ? 'active' : ''}`}
						type="button"
						on:click={() => setSort('name')}>Name</button
					>
				</div>
				<div class="filter-toggle">
					<button
						class={`filter-btn ${filter === 'all' ? 'active' : ''}`}
						type="button"
						on:click={() => setFilter('all')}>All</button
					>
					<button
						class={`filter-btn ${filter === 'items' ? 'active' : ''}`}
						type="button"
						on:click={() => setFilter('items')}
					>
						Items
					</button>
					<button
						class={`filter-btn ${filter === 'fluids' ? 'active' : ''}`}
						type="button"
						on:click={() => setFilter('fluids')}
					>
						Fluids
					</button>
				</div>
			</div>

			<div class="search-container">
				<svg
					class="search-icon"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2.5"
				>
					<circle cx="10" cy="10" r="6" />
					<line x1="15" y1="15" x2="20" y2="20" />
				</svg>
				<input
					type="text"
					id="search-input"
					class="search-input"
					bind:value={searchValue}
					placeholder="Search by name, ID or pinyin (e.g. zsj = 钻石剑)"
					on:keyup={onSearchKeyUp}
					on:input={applySearchDebounced}
				/>
			</div>

			<div class="inventory-scroll" bind:this={inventoryScrollEl}>
				<!-- svelte-ignore a11y_mouse_events_have_key_events -->
				<div
					id="inventory-grid"
					class="inventory-grid"
					role="grid"
					tabindex="-1"
					bind:this={inventoryGridEl}
					on:mouseover={onGridMouseOver}
					on:mousemove={onGridMouseMove}
					on:mouseout={onGridMouseOut}
				>
					{#if inventoryRenderNodes.length === 0}
						<div
							style="grid-column:1/-1;display:flex;align-items:center;justify-content:center;height:200px;color:#64748b;font-size:0.9rem;"
						>
							No items found
						</div>
					{:else}
						{#each inventoryRenderNodes as node (node.kind === 'header' ? `h:${node.key}` : node.key)}
							{#if node.kind === 'header'}
								<div class="inventory-group-header" data-section-key={node.key} role="row">
									<button
										class="inventory-group-toggle"
										type="button"
										aria-label={node.collapsed ? `Expand ${node.title}` : `Collapse ${node.title}`}
										on:click={() => toggleInventorySectionCollapsed(node.key)}
									>
										{node.collapsed ? '+' : '−'}
									</button>
									<div class="inventory-group-title">{node.title}</div>
									<div class="inventory-group-count">{node.count}</div>
								</div>
							{:else}
								{@const item = node.item}
								<div
									class={`item-slot ${item.type === 'fluid' ? 'fluid' : ''} ${item.craftable ? 'craftable' : ''}`}
									data-item-id={item.uniqueId}
									data-icon-id={item.iconKey}
									data-item-type={item.type}
									role="button"
									tabindex="-1"
									on:click={() => item.craftable && openCraftModal(item)}
									on:keydown={(e) => {
										if (e.key === 'Enter' || e.key === ' ') {
											e.preventDefault();
											if (item.craftable) {
												openCraftModal(item);
											}
										}
									}}
								>
									<IconImage
										iconKey={item.iconKey}
										alt={displayNameFor(item)}
										className="item-icon"
									/>
									<span class="item-name">{displayNameFor(item)}</span>
									<span class="item-amount">
										{item.type === 'fluid'
											? formatFluidAmount(Number(item.amount))
											: formatAmount(Number(item.amount))}
									</span>
								</div>
							{/if}
						{/each}
					{/if}
				</div>
			</div>
		</section>

		<aside class="side-panel">
			<div class="info-card">
				<div class="info-card-header">Terminal Data</div>
				<div class="info-card-body">
					<div class="stats-grid">
						<div class="stat-item">
							<div class="stat-item-label">Storage</div>
							<div class="stat-item-value cyan" id="storage-used">--</div>
						</div>
						<div class="stat-item">
							<div class="stat-item-label">Channels</div>
							<div class="stat-item-value purple">1/8</div>
						</div>
					</div>
				</div>
			</div>

			<div class="info-card">
				<div class="info-card-header">Quick Actions</div>
				<div class="info-card-body">
					<div class="action-grid">
						<button class="action-btn" type="button" on:click={() => void refreshData()}>
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
								<path d="M3 3v5h5"></path>
								<path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"></path>
								<path d="M16 16h5v5"></path>
							</svg>
							Refresh
						</button>
						<button class="action-btn" type="button" on:click={executeSearchNow}>
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<circle cx="11" cy="11" r="8"></circle>
								<path d="m21 21-4.35-4.35"></path>
							</svg>
							Search
						</button>
						<button
							class="action-btn"
							type="button"
							on:click={() => inventoryScrollEl?.scrollTo({ top: 0, behavior: 'smooth' })}
						>
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
								<path d="M12 5v14M5 12l7-7 7 7" />
							</svg>
							Top
						</button>
						<button
							class="action-btn"
							type="button"
							on:click={() => {
								searchValue = '';
								executeSearchNow();
							}}
						>
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<path d="M18 6 6 18"></path>
								<path d="m6 6 12 12"></path>
							</svg>
							Clear
						</button>
					</div>
				</div>
			</div>

			<div class="info-card cpu-panel">
				<div class="info-card-header">
					<svg
						class="cpu-header-icon"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="1.5"
					>
						<rect x="4" y="4" width="16" height="16" rx="2" />
						<rect x="9" y="9" width="6" height="6" />
						<path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" />
					</svg>
					<span>Crafting CPUs</span>
					<span class="cpu-count" id="cpu-count">{cpuCount}</span>
				</div>
				<div class="cpu-list" id="cpu-list">
					{#if cpus.length === 0}
						<div class="cpu-empty">
							<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
								<rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="4 2" />
								<path d="M9 9h6M9 12h6M9 15h3" />
							</svg>
							<span>No CPUs detected</span>
						</div>
					{:else}
						{#each cpus as cpu, i (cpu.cpuId || (cpu.name ?? `cpu-${i}`))}
							{@const canOpenProcess = cpu.busy && Boolean(cpu.jobStatus) && Boolean(cpu.cpuId)}
							<button
								class={`cpu-card ${cpu.busy ? 'busy' : 'idle'} ${canOpenProcess ? 'clickable' : ''}`}
								data-cpu-index={i}
								type="button"
								disabled={!canOpenProcess}
								on:click={() => openCpuProcessModal(cpu)}
							>
								<div class="cpu-header">
									<div class="cpu-identity">
										<div class="cpu-led"></div>
										<span class="cpu-name">{cpu.name || `CPU ${i + 1}`}</span>
									</div>
									<span class="cpu-status">{cpu.busy ? 'BUSY' : 'IDLE'}</span>
								</div>
								{#if cpu.busy && cpu.jobStatus}
									{@const job = cpu.jobStatus}
									{@const crafting = job.crafting}
									{@const outputTotal = job.outputTotal ?? 0}
									{@const outputDone = job.outputDone ?? 0}
									{@const hasOutputProgress = outputTotal > 0}
									{@const progressPct = hasOutputProgress
										? Math.round((outputDone / outputTotal) * 100)
										: job.totalItems > 0
											? Math.round((job.progress / job.totalItems) * 100)
											: 0}
									<div class="cpu-job">
										<div class="cpu-job-icon-wrapper">
											{#if crafting?.iconKey}
												<IconImage
													iconKey={crafting.iconKey}
													alt={crafting.name}
													className="cpu-job-icon"
													lazy={false}
													hideOnError={true}
												/>
											{:else}
												<img class="cpu-job-icon" src={PLACEHOLDER_ICON} alt="" />
											{/if}
										</div>
										<div class="cpu-job-details">
											<div class="cpu-job-name">{crafting?.name ?? 'Unknown'}</div>
											<div class="cpu-progress-wrapper">
												<div class="cpu-progress">
													<div class="cpu-progress-bar" style={`width: ${progressPct}%`}></div>
												</div>
												{#if hasOutputProgress}
													<span class="cpu-progress-text">{outputDone}/{outputTotal}</span>
												{:else}
													<span class="cpu-progress-text">{job.progress}/{job.totalItems}</span>
												{/if}
											</div>
										</div>
									</div>
								{/if}
								<div class="cpu-stats">
									<div class="cpu-stat">
										<span class="cpu-stat-label">Storage</span>
										<span class="cpu-stat-value">{formatAmount(Number(cpu.availableStorage))}</span>
									</div>
									<div class="cpu-stat">
										<span class="cpu-stat-label">Co-Proc</span>
										<span class="cpu-stat-value">{cpu.coProcessors}</span>
									</div>
								</div>
							</button>
						{/each}
					{/if}
				</div>
			</div>
		</aside>
	</main>
</div>

<div
	id="cpu-process-modal"
	class="modal-overlay"
	class:active={cpuProcessOpen}
	role="presentation"
	bind:this={cpuProcessOverlayEl}
	on:click={onCpuProcessOverlayClick}
>
	<div class="modal-content cpu-process-modal">
		<div class="modal-header cpu-process-header">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
				<rect x="4" y="4" width="16" height="16" rx="2" />
				<rect x="9" y="9" width="6" height="6" />
				<path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3" />
			</svg>
			<div class="cpu-process-title">
				<h3>Crafting Process</h3>
				<div class="cpu-process-subtitle">{cpuProcessCpuName || 'Crafting CPU'}</div>
			</div>
			<div class="cpu-process-actions">
				<button
					class="cpu-process-action"
					type="button"
					disabled={!cpuProcessOpen || cpuProcessLoading}
					on:click={() => void loadCpuProcess()}
				>
					Refresh
				</button>
				<label class="cpu-process-autorefresh">
					<input
						type="checkbox"
						bind:checked={cpuProcessAutoRefresh}
						on:change={syncCpuProcessTimer}
					/>
					<span>Auto</span>
				</label>
			</div>
		</div>

		<div class="cpu-process-tabbar">
			<button
				class={`cpu-process-tab ${cpuProcessView === 'list' ? 'active' : ''}`}
				type="button"
				on:click={() => (cpuProcessView = 'list')}
			>
				List
			</button>
			<button
				class={`cpu-process-tab ${cpuProcessView === 'statusTree' ? 'active' : ''}`}
				type="button"
				on:click={() => (cpuProcessView = 'statusTree')}
			>
				Status Tree
			</button>
			<button
				class={`cpu-process-tab ${cpuProcessView === 'dependencyTree' ? 'active' : ''}`}
				type="button"
				on:click={() => (cpuProcessView = 'dependencyTree')}
			>
				Dependency Tree (Experimental)
			</button>
		</div>

		<div class="cpu-process-body">
			{#if cpuProcessError}
				<div class="cpu-process-error">{cpuProcessError}</div>
			{/if}

			{#if cpuProcessLoading && !cpuProcessData}
				<div class="cpu-process-loading">Loading…</div>
			{:else if cpuProcessData}
				{@const summary = cpuProcessData.summary}
				{@const output = summary.finalOutput}
				{@const outputTotal = Number(summary.outputTotal ?? 0)}
				{@const outputDone = Number(summary.outputDone ?? 0)}
				{@const totalItems = Number(summary.totalItems ?? 0)}
				{@const progress = Number(summary.progress ?? 0)}
				{@const hasOutput = outputTotal > 0}
				{@const progressPct = hasOutput
					? Math.round((outputDone / outputTotal) * 100)
					: totalItems > 0
						? Math.round((progress / totalItems) * 100)
						: 0}

				{#if cpuProcessData.busy === false}
					<div class="cpu-process-warning">CPU is no longer busy.</div>
				{/if}

				<div class="cpu-process-summary">
					<div class="cpu-process-output">
						<div class="cpu-process-output-icon">
							{#if output?.iconKey}
								<IconImage
									iconKey={output.iconKey}
									alt={output.name}
									className="cpu-process-output-icon-img"
									lazy={false}
									hideOnError={true}
								/>
							{:else}
								<img class="cpu-process-output-icon-img" src={PLACEHOLDER_ICON} alt="" />
							{/if}
						</div>
						<div class="cpu-process-output-text">
							<div class="cpu-process-output-name">{output?.name ?? 'Unknown'}</div>
							<div class="cpu-process-output-meta">
								<span class="cpu-process-output-id">{output?.id ?? ''}</span>
								<span class="cpu-process-output-elapsed"
									>Elapsed {formatElapsedNanos(summary.elapsedTimeNanos)}</span
								>
							</div>
						</div>
					</div>

					<div class="cpu-process-progress">
						<div class="cpu-progress">
							<div class="cpu-progress-bar" style={`width: ${progressPct}%`}></div>
						</div>
						<div class="cpu-process-progress-text">
							{#if hasOutput}
								<span>{formatAmount(outputDone)}/{formatAmount(outputTotal)}</span>
							{:else}
								<span>{formatAmount(progress)}/{formatAmount(totalItems)}</span>
							{/if}
							<span class="cpu-process-progress-pct">{progressPct}%</span>
						</div>
					</div>
				</div>

				<div class="cpu-process-view">
					{#if cpuProcessView === 'list'}
						<div class="cpu-process-list-controls">
							<input
								class="cpu-process-filter"
								type="text"
								placeholder="Filter items…"
								bind:value={cpuProcessFilter}
							/>
							<div class="cpu-process-count">{cpuProcessEntriesFiltered.length} entries</div>
						</div>
						<div class="cpu-process-table-wrap">
							<table class="cpu-process-table">
								<thead>
									<tr>
										<th>Item</th>
										<th class="num">Stored</th>
										<th class="num">Waiting</th>
										<th class="num">Active</th>
									</tr>
								</thead>
								<tbody>
									{#each cpuProcessEntriesFiltered as row (row.stack.iconKey)}
										{@const stack = row.stack}
										<tr>
											<td class="item">
												<div class="cpu-process-item">
													{#if stack.iconKey}
														<IconImage
															iconKey={stack.iconKey}
															alt={stack.name}
															className="cpu-process-item-icon"
															lazy={true}
															hideOnError={true}
														/>
													{:else}
														<img class="cpu-process-item-icon" src={PLACEHOLDER_ICON} alt="" />
													{/if}
													<div class="cpu-process-item-text">
														<div class="cpu-process-item-name">{stack.name}</div>
														<div class="cpu-process-item-id">{stack.id}</div>
													</div>
												</div>
											</td>
											<td class="num">{formatAmount(Number(row.stored ?? 0))}</td>
											<td class="num">{formatAmount(Number(row.waiting ?? 0))}</td>
											<td class="num">{formatAmount(Number(row.active ?? 0))}</td>
										</tr>
									{/each}
								</tbody>
							</table>
						</div>
					{:else if cpuProcessView === 'statusTree'}
						<div class="cpu-process-tree">
							<details open>
								<summary>Active ({cpuProcessEntriesActive.length})</summary>
								<div class="cpu-process-tree-list">
									{#each cpuProcessEntriesActive as row (row.stack.iconKey)}
										<CpuProcessTreeRow stack={row.stack} value={row.active} label="Active" />
									{/each}
								</div>
							</details>
							<details open>
								<summary>Waiting ({cpuProcessEntriesWaiting.length})</summary>
								<div class="cpu-process-tree-list">
									{#each cpuProcessEntriesWaiting as row (row.stack.iconKey)}
										<CpuProcessTreeRow stack={row.stack} value={row.waiting} label="Waiting" />
									{/each}
								</div>
							</details>
							<details>
								<summary>Stored ({cpuProcessEntriesStored.length})</summary>
								<div class="cpu-process-tree-list">
									{#each cpuProcessEntriesStored as row (row.stack.iconKey)}
										<CpuProcessTreeRow stack={row.stack} value={row.stored} label="Stored" />
									{/each}
								</div>
							</details>
						</div>
					{:else if cpuProcessView === 'dependencyTree'}
						{#if cpuProcessData.patterns.length === 0}
							<div class="cpu-process-empty">No pattern/task data available for this job.</div>
						{:else}
							<CpuDependencyTree
								root={cpuProcessData.summary.finalOutput}
								patterns={cpuProcessData.patterns}
								entries={cpuProcessData.entries}
							/>
						{/if}
					{/if}
				</div>
			{:else}
				<div class="cpu-process-empty">Select a busy CPU to view its process.</div>
			{/if}
		</div>
	</div>
</div>

<div
	id="inventory-settings-modal"
	class="modal-overlay"
	class:active={inventorySettingsOpen}
	role="presentation"
	bind:this={inventorySettingsOverlayEl}
	on:click={onInventorySettingsOverlayClick}
>
	<div class="modal-content inventory-settings-modal" role="dialog" aria-modal="true" tabindex="-1">
		<div class="modal-header">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path
					d="M19.14 12.94a7.43 7.43 0 0 0 .05-.94 7.43 7.43 0 0 0-.05-.94l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.28 7.28 0 0 0-1.63-.94l-.36-2.54A.5.5 0 0 0 13.9 1h-3.8a.5.5 0 0 0-.49.42l-.36 2.54a7.28 7.28 0 0 0-1.63.94l-2.39-.96a.5.5 0 0 0-.6.22L2.71 7.98a.5.5 0 0 0 .12.64l2.03 1.58a7.43 7.43 0 0 0-.05.94c0 .32.02.63.05.94l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32a.5.5 0 0 0 .6.22l2.39-.96c.5.39 1.05.72 1.63.94l.36 2.54a.5.5 0 0 0 .49.42h3.8a.5.5 0 0 0 .49-.42l.36-2.54c.58-.22 1.13-.55 1.63-.94l2.39.96a.5.5 0 0 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64l-2.03-1.58z"
				/>
				<circle cx="12" cy="12" r="3" />
			</svg>
			<h3>Inventory Settings</h3>
		</div>
		<div class="modal-body inventory-settings-body">
			<div class="inventory-settings-subtitle">
				{inventorySettingsTerminalName || inventorySettingsTerminalId || 'Terminal'}
			</div>

			<div class="inventory-settings-section">
				<div class="inventory-settings-section-title">Tag Filter</div>
				<div class="inventory-settings-hint">
					One regex per line. Filters by item tags (e.g. <span class="mono">forge:ores/iron</span>).
					In <b>All</b> view, enabling this hides fluids (fluids have no tags).
				</div>

				<div class="inventory-settings-grid">
					<div class="inventory-settings-field">
						<label for="inventory-settings-whitelist">Whitelist (must match)</label>
						<textarea
							id="inventory-settings-whitelist"
							class="inventory-settings-textarea"
							rows="5"
							placeholder="^forge:ores/.*"
							bind:value={inventorySettingsWhitelistText}
							disabled={inventorySettingsSaving}
						></textarea>
					</div>
					<div class="inventory-settings-field">
						<label for="inventory-settings-blacklist">Blacklist (exclude)</label>
						<textarea
							id="inventory-settings-blacklist"
							class="inventory-settings-textarea"
							rows="5"
							placeholder="uranium|plutonium"
							bind:value={inventorySettingsBlacklistText}
							disabled={inventorySettingsSaving}
						></textarea>
					</div>
				</div>
			</div>

			<div class="inventory-settings-section">
				<div class="inventory-settings-section-title">Categories</div>
				<div class="inventory-settings-hint">
					Scheme 1: first match wins (top to bottom). Each category is <b>name</b> +
					<b>tag regex</b>.
				</div>

				{#if inventorySettingsCategories.length === 0}
					<div class="inventory-settings-empty">No categories configured.</div>
				{/if}

				<div class="inventory-settings-categories" role="list">
					{#each inventorySettingsCategories as rule (rule.id)}
						<div
							class="inventory-settings-category-row"
							class:drag-over={inventorySettingsCategoryDragOverId === rule.id}
							class:dragging={inventorySettingsCategoryDragId === rule.id}
							role="listitem"
							on:dragover={(e) => onInventoryCategoryDragOver(e, rule.id)}
							on:drop={(e) => onInventoryCategoryDrop(e, rule.id)}
						>
							<button
								class="inventory-settings-category-handle"
								type="button"
								aria-label="Drag to reorder"
								title="Drag to reorder"
								disabled={inventorySettingsSaving}
								draggable={!inventorySettingsSaving}
								on:dragstart={(e) => onInventoryCategoryDragStart(e, rule.id)}
								on:dragend={onInventoryCategoryDragEnd}
							>
								<svg viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
									<circle cx="3" cy="3" r="1" />
									<circle cx="9" cy="3" r="1" />
									<circle cx="3" cy="6" r="1" />
									<circle cx="9" cy="6" r="1" />
									<circle cx="3" cy="9" r="1" />
									<circle cx="9" cy="9" r="1" />
								</svg>
							</button>
							<input
								class="inventory-settings-input inventory-settings-category-name"
								type="text"
								placeholder="Category name"
								bind:value={rule.name}
								disabled={inventorySettingsSaving}
							/>
							<input
								class="inventory-settings-input mono inventory-settings-category-regex"
								type="text"
								placeholder="Tag regex"
								bind:value={rule.tagRegex}
								disabled={inventorySettingsSaving}
							/>
							<button
								class="inventory-settings-remove inventory-settings-category-remove"
								type="button"
								disabled={inventorySettingsSaving}
								on:click={() => removeInventoryCategoryRule(rule.id)}
							>
								Remove
							</button>
						</div>
					{/each}
				</div>

				<div class="inventory-settings-actions">
					<button
						class="inventory-settings-add"
						type="button"
						disabled={inventorySettingsSaving}
						on:click={addInventoryCategoryRule}
					>
						Add Category
					</button>
				</div>
			</div>

			{#if inventorySettingsError}
				<div class="inventory-settings-error">{inventorySettingsError}</div>
			{/if}
		</div>
		<div class="modal-footer">
			<button
				class="btn-cancel"
				type="button"
				disabled={inventorySettingsSaving}
				on:click={closeInventorySettingsModal}
			>
				Cancel
			</button>
			<button
				class="btn-confirm"
				type="button"
				disabled={inventorySettingsSaving}
				on:click={() => void saveInventorySettings()}
			>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				Save
			</button>
		</div>
	</div>
</div>

<div
	id="craft-modal"
	class="modal-overlay"
	class:active={craftOpen}
	role="presentation"
	bind:this={craftOverlayEl}
	on:click={onOverlayClick}
>
	<div class="modal-content">
		<div class="modal-header">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path d="m15 12-8.5 8.5c-.83.83-2.17.83-3 0 0 0 0 0 0 0a2.12 2.12 0 0 1 0-3L12 9"></path>
				<path d="M17.64 15 22 10.64"></path>
				<path
					d="m20.91 11.7-1.25-1.25c-.6-.6-.93-1.4-.93-2.25v-.86L16.01 4.6a5.56 5.56 0 0 0-3.94-1.64H9l.92.82A6.18 6.18 0 0 1 12 8.4v1.56l2 2h2.47l2.26 1.91"
				></path>
			</svg>
			<h3>Crafting Request</h3>
		</div>
		<div class="modal-body">
			<div class="craft-item-display">
				<div class="craft-item-icon" id="craft-icon-container">
					{#if craftItem?.iconKey}
						<IconImage
							iconKey={craftItem.iconKey}
							alt={displayNameFor(craftItem)}
							className=""
							lazy={false}
						/>
					{:else}
						<img src={PLACEHOLDER_ICON} alt="" />
					{/if}
				</div>
				<div class="craft-item-info">
					<div class="craft-item-name" id="craft-item-name">
						{craftItem ? displayNameFor(craftItem) : 'Item Name'}
					</div>
					<div class="craft-item-id" id="craft-item-id">
						{craftItem ? craftItem.id : 'minecraft:item'}{craftItem?.type === 'fluid'
							? ' [Fluid]'
							: ''}
					</div>
				</div>
			</div>
			<div class="amount-section">
				<label for="craft-amount">Quantity{craftItem?.type === 'fluid' ? ' (B)' : ''}</label>
				<input
					type="number"
					id="craft-amount"
					class="amount-input"
					min="1"
					max={craftItem?.type === 'fluid' ? 1000 : 64000}
					value={craftAmount}
					on:input={(e) => {
						const value = (e.target as HTMLInputElement | null)?.valueAsNumber;
						if (typeof value === 'number' && Number.isFinite(value)) {
							craftAmount = value;
						}
					}}
				/>
				<div class="quick-amounts">
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 1)}
						>1</button
					>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 16)}
						>16</button
					>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 64)}
						>64</button
					>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 256)}
						>256</button
					>
				</div>
			</div>
			<div class="cpu-select-section">
				<label for="craft-cpu-select">Target CPU</label>
				<div class="cpu-select-wrapper">
					<svg
						class="cpu-select-icon"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="1.5"
					>
						<rect x="4" y="4" width="16" height="16" rx="2" />
						<rect x="9" y="9" width="6" height="6" />
						<path d="M9 1v3M15 1v3M9 20v3M15 20v3" />
					</svg>
					<select id="craft-cpu-select" class="cpu-select" bind:value={craftCpuName}>
						<option value="">Auto (Any Available)</option>
						{#each cpus as cpu, i (cpu.cpuId || (cpu.name ?? `cpu-${i}`))}
							{@const cpuName = cpu.name || `CPU ${i + 1}`}
							{@const status = cpu.busy ? ' (Busy)' : ''}
							<option value={cpu.name ?? ''}>{cpuName}{status}</option>
						{/each}
					</select>
					<svg
						class="cpu-select-arrow"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<polyline points="6 9 12 15 18 9" />
					</svg>
				</div>
			</div>
		</div>
		<div class="modal-footer">
			<button id="craft-cancel-btn" class="btn-cancel" type="button" on:click={closeCraftModal}
				>Cancel</button
			>
			<button
				id="craft-confirm-btn"
				class="btn-confirm"
				type="button"
				on:click={() => void confirmCraft()}
			>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				Confirm
			</button>
		</div>
	</div>
</div>

<div
	id="ae2web-tooltip"
	class="ae2web-tooltip"
	style:display={tooltipVisible ? 'block' : 'none'}
	bind:this={tooltipEl}
>
	{#each tooltipHtmlLines as line, i (i)}
		<div class="ae2web-tooltip-line">{@html line}</div>
	{/each}
</div>

<div
	id="login-modal"
	class="modal-overlay"
	class:active={needsLogin}
	role="dialog"
	aria-modal="true"
>
	<div class="modal-content login-modal">
		<div class="modal-header">
			<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
				<path
					d="M12 2a7 7 0 0 0-7 7v4a2 2 0 0 0 2 2h1v3a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-3h1a2 2 0 0 0 2-2V9a7 7 0 0 0-7-7z"
				/>
				<path d="M9 11h6" />
			</svg>
			<h3>Enter Token</h3>
		</div>
		<div class="modal-body">
			<div class="login-hint">
				Get your personal token in-game: right-click the Web Terminal block, or run <span
					class="mono">/ae2web token</span
				>.
			</div>
			<input
				class="login-input"
				type="password"
				placeholder="Paste token…"
				autocomplete="off"
				autocapitalize="off"
				spellcheck="false"
				bind:value={loginTokenValue}
				on:keydown={(e) => {
					if (e.key === 'Enter') {
						e.preventDefault();
						submitLoginToken();
					}
				}}
			/>
			{#if loginError}
				<div class="login-error">{loginError}</div>
			{/if}
		</div>
		<div class="modal-footer">
			<button class="btn-cancel" type="button" on:click={() => setAuthToken('')}>Clear</button>
			<button class="btn-confirm" type="button" on:click={submitLoginToken}>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				Connect
			</button>
		</div>
	</div>
</div>

<ToastHost />
