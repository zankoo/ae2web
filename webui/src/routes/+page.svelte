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
		CpuProcessResponse,
		InventoryItem,
		InventoryViewConfig,
		TerminalInfo
	} from '$lib/api/types';
	import ToastHost from '$lib/components/ToastHost.svelte';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import GradientText from '$lib/components/GradientText.svelte';
	import HeroGraphic from '$lib/components/HeroGraphic.svelte';
	import LoginModal from '$lib/components/modals/LoginModal.svelte';
	import CraftModal from '$lib/components/modals/CraftModal.svelte';
	import CpuProcessModal from '$lib/components/modals/CpuProcessModal.svelte';
	import InventorySettingsModal, {
		type CategoryDraft
	} from '$lib/components/modals/InventorySettingsModal.svelte';
	import CpuSidebar from '$lib/components/CpuSidebar.svelte';
	import TerminalSelector from '$lib/components/TerminalSelector.svelte';
	import InventoryToolbar, { type SortMode } from '$lib/components/InventoryToolbar.svelte';
	import InventoryGrid from '$lib/components/InventoryGrid.svelte';
	import ItemTooltip from '$lib/components/ItemTooltip.svelte';
	import { authToken, setAuthToken } from '$lib/stores/session';
	import { showToast } from '$lib/stores/toast';
	import { formatAmount, formatNumber } from '$lib/utils/format';
	import {
		buildInventoryRenderNodes,
		type InventoryRenderNode
	} from '$lib/utils/inventoryGrouping';
	import { sortInventoryItems } from '$lib/utils/inventorySort';
	import { legacyTextToHtml } from '$lib/utils/minecraftText';
	import { matchItem } from '$lib/utils/pinyin';
	import { createTooltipPositioner, type TooltipPositioner } from '$lib/utils/tooltipPositioner';

	let online = false;
	let loginError: string | null = null;
	$: needsLogin = !$authToken || !$authToken.trim();
	let appCleanup: (() => void) | null = null;

	let terminals: TerminalInfo[] = [];
	let terminalId = '';
	let terminalDisplayName = 'Loading...';
	let terminalDropdownOpen = false;
	let terminalSelectorComponent: TerminalSelector;

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

	let inventoryGridComponent: InventoryGrid;
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
	let inventorySettingsTerminalId: string | null = null;
	let inventorySettingsTerminalName: string | null = null;
	let inventorySettingsWhitelistText = '';
	let inventorySettingsBlacklistText = '';
	let inventorySettingsCategories: CategoryDraft[] = [];
	let inventorySettingsSaving = false;
	let inventorySettingsError: string | null = null;

	let craftOpen = false;
	let craftItem: InventoryItem | null = null;

	let cpuProcessOpen = false;
	let cpuProcessCpuId: string | null = null;
	let cpuProcessCpuName: string | null = null;
	let cpuProcessAutoRefresh = true;
	let cpuProcessCompletionNotified = false;
	let cpuProcessLoading = false;
	let cpuProcessError: string | null = null;
	let cpuProcessData: CpuProcessResponse | null = null;
	let cpuProcessTimer: number | null = null;

	const tooltipCache = new Map<string, string[]>();
	let tooltipComponent: ItemTooltip;
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
		const inventoryScrollEl = inventoryGridComponent?.getScrollElement();
		const inventoryGridEl = inventoryGridComponent?.getGridElement();
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
		const inventoryScrollEl = inventoryGridComponent?.getScrollElement();
		const inventoryGridEl = inventoryGridComponent?.getGridElement();
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

	let inventorySettingsCategoryIdSeq = 0;

	function createInventoryCategoryDraft(rule?: {
		name?: string;
		tagRegex?: string;
	}): CategoryDraft {
		inventorySettingsCategoryIdSeq += 1;
		return {
			id: `cat_${inventorySettingsCategoryIdSeq}`,
			name: typeof rule?.name === 'string' ? rule.name : '',
			tagRegex: typeof rule?.tagRegex === 'string' ? rule.tagRegex : ''
		};
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
	}

	function parseRegexLines(text: string): string[] {
		return String(text)
			.split('\n')
			.map((x) => x.trim())
			.filter(Boolean);
	}

	function validateRegex(pattern: string): string | null {
		try {
			new RegExp(pattern, 'i');
			return null;
		} catch {
			return `Invalid regex: ${pattern}`;
		}
	}

	async function saveInventorySettings(
		whitelistText: string,
		blacklistText: string,
		categories: CategoryDraft[]
	): Promise<void> {
		if (!inventorySettingsTerminalId) return;
		inventorySettingsSaving = true;
		inventorySettingsError = null;

		const whitelist = parseRegexLines(whitelistText);
		const blacklist = parseRegexLines(blacklistText);
		for (const p of [...whitelist, ...blacklist]) {
			const err = validateRegex(p);
			if (err) {
				inventorySettingsError = err;
				inventorySettingsSaving = false;
				return;
			}
		}

		const cats = categories
			.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
			.filter((x) => x.name && x.tagRegex);
		for (const c of cats) {
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
			categories: cats,
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

	function handleLogin(token: string): void {
		if (!token.trim()) {
			loginError = 'Token required';
			return;
		}
		loginError = null;
		setAuthToken(token);
	}

	function handleLoginClear(): void {
		setAuthToken('');
	}

	function openCraftModal(item: InventoryItem): void {
		craftItem = item;
		craftOpen = true;
	}

	function closeCraftModal(): void {
		craftOpen = false;
	}

	async function handleCraft(item: InventoryItem, amount: number, cpuName: string): Promise<void> {
		try {
			const body: any = {
				id: item.id,
				type: item.type,
				amount,
				terminalId
			};
			if (cpuName) {
				body.cpuName = cpuName;
			}

			await submitCraft(body);
			const amountText = item.type === 'fluid' ? `${amount}B` : `${amount}x`;
			showToast(`Request sent: ${amountText} ${displayNameFor(item)}`, 'success');
			closeCraftModal();
			void loadCpus();
		} catch (e) {
			showToast(e instanceof Error ? e.message : 'Craft request failed', 'error');
		}
	}

	function openCpuProcessModal(cpu: CpuDto): void {
		if (!cpu?.busy) return;
		if (!cpu.cpuId) return;
		if (!terminalId) return;

		cpuProcessOpen = true;
		cpuProcessCpuId = cpu.cpuId;
		cpuProcessCpuName = cpu.name;
		cpuProcessAutoRefresh = true;
		cpuProcessCompletionNotified = false;
		cpuProcessLoading = false;
		cpuProcessError = null;
		cpuProcessData = null;

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
		cpuProcessTimer = window.setInterval(() => void loadCpuProcess(), 2000);
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

	function handleCpuProcessAutoRefreshChange(value: boolean): void {
		cpuProcessAutoRefresh = value;
		syncCpuProcessTimer();
	}

	function ensureTooltipPositioner(): TooltipPositioner | null {
		const tooltipEl = tooltipComponent?.getElement();
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
				tooltipComponent?.setPosition(left, top);
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
		const inventoryGridEl = inventoryGridComponent?.getGridElement();
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
		if (!tooltipVisible) return;
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

		const onDocClick = (e: MouseEvent) => {
			if (!terminalDropdownOpen) return;
			const target = e.target as Node | null;
			const containerEl = terminalSelectorComponent?.getContainerElement();
			if (containerEl && target && !containerEl.contains(target)) {
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
</script>

<!-- Main Container -->
<div class="flex flex-col h-screen bg-background text-foreground overflow-hidden font-sans">
	<!-- Hero / Header Section -->
	<div class="relative flex-none bg-background z-10 border-b border-border shadow-sm">
		<div class="absolute inset-0 pointer-events-none overflow-hidden">
			<!-- Abstract Background -->
			<div class="absolute top-0 right-0 w-1/2 h-full opacity-50">
				<HeroGraphic />
			</div>
			<div
				class="absolute bottom-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-border to-transparent opacity-50"
			></div>
		</div>

		<div class="flex items-center justify-between px-4 lg:px-6 py-4 relative">
			<!-- Left: Logo, Title, Selector & Settings -->
			<div class="flex items-center gap-4">
				<!-- Logo & Title -->
				<div class="flex items-center gap-3">
					<div
						class="w-10 h-10 rounded-xl bg-gradient-to-br from-accent to-accent-secondary flex items-center justify-center shadow-lg shadow-accent/20"
					>
						<img
							src="logo.svg"
							alt="AE2"
							class="w-6 h-6"
							style="image-rendering: pixelated;"
						/>
					</div>
					<h1 class="text-2xl md:text-3xl font-display font-normal tracking-tight text-foreground">
						AE2 <GradientText>Web</GradientText>
					</h1>
				</div>

				<div class="w-px h-8 bg-border/50"></div>

				<!-- Terminal Selector -->
				<TerminalSelector
					bind:this={terminalSelectorComponent}
					{online}
					displayName={terminalDisplayName}
					{terminalId}
					{connectedTerminals}
					dropdownOpen={terminalDropdownOpen}
					onToggleDropdown={openTerminalDropdown}
					onSelectTerminal={selectTerminal}
				/>

				<!-- Settings -->
				<Button
					variant="ghost"
					size="sm"
					className="btn-square rounded-full"
					title="Inventory Settings"
					aria-label="Inventory Settings"
					onClick={() => {
						if (terminalId) {
							void openInventorySettingsModalForTerminal({
								id: terminalId,
								displayName: terminalDisplayName,
								connected: true
							});
						} else {
							showToast('Please select a terminal first', 'error');
						}
					}}
				>
					<svg
						class="w-5 h-5 text-muted-foreground"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						><circle cx="12" cy="12" r="3"></circle><path
							d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
						></path></svg
					>
				</Button>
			</div>

			<!-- Right: Stats -->
			<div
				class="hidden md:flex items-center gap-6 px-6 py-2 bg-muted/50 rounded-full border border-border/50"
			>
				<div class="flex flex-col items-center">
					<span class="text-[10px] uppercase tracking-widest text-muted-foreground font-bold"
						>Items</span
					>
					<span class="font-mono text-sm font-semibold">{formatAmount(totalItemAmount)}</span>
				</div>
				<div class="w-px h-6 bg-border"></div>
				<div class="flex flex-col items-center">
					<span class="text-[10px] uppercase tracking-widest text-muted-foreground font-bold"
						>Types</span
					>
					<span class="font-mono text-sm font-semibold">{formatNumber(typeCount)}</span>
				</div>
			</div>
		</div>
	</div>

	<!-- Main Content -->
	<div class="flex flex-1 overflow-hidden px-4 lg:px-6 pb-4 lg:pb-6 gap-6 z-0">
		<!-- Inventory Panel -->
		<Card className="flex-1 flex flex-col overflow-hidden shadow-xl border-border/50" padding="none">
			<InventoryToolbar
				{filter}
				{sort}
				bind:searchValue
				onFilterChange={setFilter}
				onSortChange={setSort}
				onSearchInput={applySearchDebounced}
				{onSearchKeyUp}
				onClearSearch={() => {
					searchValue = '';
					executeSearchNow();
				}}
			/>

			<InventoryGrid
				bind:this={inventoryGridComponent}
				renderNodes={inventoryRenderNodes}
				onItemClick={openCraftModal}
				onToggleSection={toggleInventorySectionCollapsed}
				onMouseOver={onGridMouseOver}
				onMouseMove={onGridMouseMove}
				onMouseOut={onGridMouseOut}
				onScroll={onInventoryScroll}
			/>
		</Card>

		<!-- Side Panel -->
		<CpuSidebar
			{cpus}
			onCpuClick={openCpuProcessModal}
			onRefresh={() => void refreshData()}
			onSearch={executeSearchNow}
			onScrollToTop={() =>
				inventoryGridComponent?.getScrollElement()?.scrollTo({ top: 0, behavior: 'smooth' })}
			onClearSearch={() => {
				searchValue = '';
				executeSearchNow();
			}}
		/>
	</div>
</div>

<!-- CPU Process Modal -->
<CpuProcessModal
	open={cpuProcessOpen}
	cpuName={cpuProcessCpuName}
	autoRefresh={cpuProcessAutoRefresh}
	loading={cpuProcessLoading}
	error={cpuProcessError}
	data={cpuProcessData}
	onClose={closeCpuProcessModal}
	onRefresh={() => void loadCpuProcess()}
	onAutoRefreshChange={handleCpuProcessAutoRefreshChange}
/>

<!-- Inventory Settings Modal -->
<InventorySettingsModal
	open={inventorySettingsOpen}
	terminalName={inventorySettingsTerminalName}
	terminalId={inventorySettingsTerminalId}
	saving={inventorySettingsSaving}
	error={inventorySettingsError}
	whitelistText={inventorySettingsWhitelistText}
	blacklistText={inventorySettingsBlacklistText}
	categories={inventorySettingsCategories}
	onClose={closeInventorySettingsModal}
	onSave={saveInventorySettings}
	onWhitelistChange={(v) => (inventorySettingsWhitelistText = v)}
	onBlacklistChange={(v) => (inventorySettingsBlacklistText = v)}
	onCategoriesChange={(c) => (inventorySettingsCategories = c)}
/>

<!-- Craft Modal -->
<CraftModal
	open={craftOpen}
	item={craftItem}
	{cpus}
	onCraft={handleCraft}
	onClose={closeCraftModal}
/>

<!-- Login Modal -->
<LoginModal open={needsLogin} error={loginError} onLogin={handleLogin} onClear={handleLoginClear} />

<!-- Tooltip -->
<ItemTooltip bind:this={tooltipComponent} visible={tooltipVisible} htmlLines={tooltipHtmlLines} />

<ToastHost />
