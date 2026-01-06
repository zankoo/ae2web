<script lang="ts">
	import { onDestroy, onMount, tick } from 'svelte';
	import { resolve } from '$app/paths';
	import { page } from '$app/stores';

	import debounce from 'lodash-es/debounce';

	import { CraftMissingError } from '$lib/api/client';
	import { fetchCpus } from '$lib/api/cpus';
	import { fetchCpuProcess } from '$lib/api/cpuProcess';
	import { submitCraft } from '$lib/api/craft';
	import { openInventorySse, type InventoryFilter } from '$lib/api/inventory';
	import { fetchInventoryViewConfig, saveInventoryViewConfig } from '$lib/api/userConfig';
	import type {
		CpuDto,
		CpuProcessResponse,
		GenericStackDto,
		InventoryItem,
		InventoryViewConfig,
		TerminalInfo
	} from '$lib/api/types';
	import ToastHost from '$lib/components/ToastHost.svelte';
	import GradientText from '$lib/components/GradientText.svelte';
	import HeroGraphic from '$lib/components/HeroGraphic.svelte';
	import LoginModal from '$lib/components/modals/LoginModal.svelte';
	import CraftModal from '$lib/components/modals/CraftModal.svelte';
	import CpuProcessModal from '$lib/components/modals/CpuProcessModal.svelte';
	import InventorySettingsModal, {
		type CategoryDraft
	} from '$lib/components/modals/InventorySettingsModal.svelte';
	import ProductionChartModal from '$lib/components/modals/ProductionChartModal.svelte';
	import CpuSidebar from '$lib/components/CpuSidebar.svelte';
	import TerminalSelector from '$lib/components/TerminalSelector.svelte';
	import InventoryToolbar, { type SortMode } from '$lib/components/InventoryToolbar.svelte';
	import InventoryGrid from '$lib/components/InventoryGrid.svelte';
	import ItemTooltip from '$lib/components/ItemTooltip.svelte';
	import { authToken, setAuthToken } from '$lib/stores/session';
	import { showToast } from '$lib/stores/toast';
	import {
		terminalId as terminalIdStore,
		terminalDisplayName as terminalDisplayNameStore,
		online as onlineStore,
		connectedTerminals as connectedTerminalsStore,
		terminalDropdownOpen as terminalDropdownOpenStore,
		selectTerminal as selectTerminalFromStore,
		toggleTerminalDropdown
	} from '$lib/stores/terminal';
	import { formatAmount, formatNumber } from '$lib/utils/format';
	import { buildInventoryRenderNodes } from '$lib/utils/inventoryGrouping';
	import { ProductionStatsTracker } from '$lib/utils/productionStats';
	import { displayNameFor } from '$lib/pages/inventory/displayName';
	import { captureScrollAnchor, restoreScrollAnchor } from '$lib/pages/inventory/scrollAnchor';
	import { createCpuProcessController } from '$lib/pages/inventory/useCpuProcess';
	import { createInventoryConfigController } from '$lib/pages/inventory/useInventoryConfig';
	import { createTooltipController } from '$lib/pages/inventory/useTooltip';
	import { computeVisibleItems, hasPositionChanged } from '$lib/pages/inventory/visibleItems';
	import {
		loadTerminalHistory,
		queueHistoryUpdate,
		forceFlush as forceFlushHistory
	} from '$lib/storage/productionHistory';

	let loginError = $state<string | null>(null);
	let needsLogin = $derived(!$authToken || !$authToken.trim());
	let appCleanup: (() => void) | null = null;

	// Use global terminal state from store
	let online = $derived($onlineStore);
	let terminalId = $derived($terminalIdStore);
	let terminalDisplayName = $derived($terminalDisplayNameStore);
	let terminalDropdownOpen = $derived($terminalDropdownOpenStore);
	let connectedTerminals = $derived($connectedTerminalsStore);

	let filter = $state<InventoryFilter>('all');
	let sort = $state<SortMode>('amount');
	let searchValue = $state('');
	let searchTerm = $state('');
	let inventoryItems = $state<InventoryItem[]>([]);
	let visibleItems = $state<InventoryItem[]>([]);
	let totalItemAmount = $derived(
		visibleItems.reduce((sum, item) => {
			const amount = Number(item?.amount);
			return sum + (Number.isFinite(amount) ? amount : 0);
		}, 0)
	);
	let typeCount = $derived(visibleItems.length);

	let inventoryGridComponent: InventoryGrid;
	let inventoryEventSource: ReturnType<typeof openInventorySse> | null = null;
	let lastInventoryEventVersion: number | null = null;

	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- rebuilt on SSE updates
	let inventoryState = new Map<string, InventoryItem>();
	let inventoryStateDirty = false;
	let inventoryStateFlushInFlight = false;
	let inventoryStateFlushPending = false;

	// Production stats tracker instance
	const productionStats = new ProductionStatsTracker();

	// Track items whose chart was opened (for persistence)
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- simple add-only tracking set
	const chartViewedItems = new Set<string>();

	// Production rate cache - avoid repeated calculations
	const RATE_CACHE_TTL = 200; // 200ms
	// eslint-disable-next-line svelte/prefer-svelte-reactivity -- cache Map, not reactive
	const productionRateCache = new Map<string, { rate: number | null; timestamp: number }>();

	/**
	 * Get production rate for an item with caching.
	 * @returns rate per second, or null if not available
	 */
	function getProductionRate(uniqueId: string): number | null {
		if (!terminalId) return null;

		const now = Date.now();
		const cached = productionRateCache.get(uniqueId);

		if (cached && now - cached.timestamp < RATE_CACHE_TTL) {
			return cached.rate;
		}

		const rate = productionStats.getRate(terminalId, uniqueId, now);
		productionRateCache.set(uniqueId, { rate, timestamp: now });
		return rate;
	}

	let inventoryScrollActive = false;
	let inventoryScrollTimer: ReturnType<typeof setTimeout> | null = null;

	let cpus = $state<CpuDto[]>([]);
	let inventoryViewConfig = $state<InventoryViewConfig>({
		whitelist: [],
		blacklist: [],
		categories: [],
		collapsed: {}
	});
	let inventoryViewConfigTerminalId = $state<string | null>(null);
	let inventoryRenderNodes = $derived(
		buildInventoryRenderNodes(
			visibleItems,
			filter,
			inventoryViewConfigTerminalId === terminalId ? inventoryViewConfig : null
		)
	);

	let inventorySettingsOpen = $state(false);
	let inventorySettingsTerminalId = $state<string | null>(null);
	let inventorySettingsTerminalName = $state<string | null>(null);
	let inventorySettingsWhitelistText = $state('');
	let inventorySettingsBlacklistText = $state('');
	let inventorySettingsCategories = $state<CategoryDraft[]>([]);
	let inventorySettingsSaving = $state(false);
	let inventorySettingsError = $state<string | null>(null);

	const inventoryConfigController = createInventoryConfigController({
		view: {
			get config() {
				return inventoryViewConfig;
			},
			set config(value) {
				inventoryViewConfig = value;
			},
			get terminalId() {
				return inventoryViewConfigTerminalId;
			},
			set terminalId(value) {
				inventoryViewConfigTerminalId = value;
			}
		},
		settings: {
			get open() {
				return inventorySettingsOpen;
			},
			set open(value) {
				inventorySettingsOpen = value;
			},
			get terminalId() {
				return inventorySettingsTerminalId;
			},
			set terminalId(value) {
				inventorySettingsTerminalId = value;
			},
			get terminalName() {
				return inventorySettingsTerminalName;
			},
			set terminalName(value) {
				inventorySettingsTerminalName = value;
			},
			get whitelistText() {
				return inventorySettingsWhitelistText;
			},
			set whitelistText(value) {
				inventorySettingsWhitelistText = value;
			},
			get blacklistText() {
				return inventorySettingsBlacklistText;
			},
			set blacklistText(value) {
				inventorySettingsBlacklistText = value;
			},
			get categories() {
				return inventorySettingsCategories;
			},
			set categories(value) {
				inventorySettingsCategories = value;
			},
			get saving() {
				return inventorySettingsSaving;
			},
			set saving(value) {
				inventorySettingsSaving = value;
			},
			get error() {
				return inventorySettingsError;
			},
			set error(value) {
				inventorySettingsError = value;
			}
		},
		getCurrentTerminalId: () => terminalId,
		fetchInventoryViewConfig,
		saveInventoryViewConfig,
		showToast,
		setAuthToken
	});

	let craftOpen = $state(false);
	let craftItem = $state<InventoryItem | null>(null);
	let craftMissingItems = $state<GenericStackDto[]>([]);

	let productionChartOpen = $state(false);
	let productionChartItem = $state<InventoryItem | null>(null);

	let cpuProcessOpen = $state(false);
	let cpuProcessCpuId = $state<string | null>(null);
	let cpuProcessCpuName = $state<string | null>(null);
	let cpuProcessAutoRefresh = $state(true);
	let cpuProcessCompletionNotified = $state(false);
	let cpuProcessLoading = $state(false);
	let cpuProcessError = $state<string | null>(null);
	let cpuProcessData = $state<CpuProcessResponse | null>(null);

	const cpuProcessController = createCpuProcessController({
		state: {
			get open() {
				return cpuProcessOpen;
			},
			set open(value) {
				cpuProcessOpen = value;
			},

			get cpuId() {
				return cpuProcessCpuId;
			},
			set cpuId(value) {
				cpuProcessCpuId = value;
			},

			get cpuName() {
				return cpuProcessCpuName;
			},
			set cpuName(value) {
				cpuProcessCpuName = value;
			},

			get autoRefresh() {
				return cpuProcessAutoRefresh;
			},
			set autoRefresh(value) {
				cpuProcessAutoRefresh = value;
			},

			get completionNotified() {
				return cpuProcessCompletionNotified;
			},
			set completionNotified(value) {
				cpuProcessCompletionNotified = value;
			},

			get loading() {
				return cpuProcessLoading;
			},
			set loading(value) {
				cpuProcessLoading = value;
			},

			get error() {
				return cpuProcessError;
			},
			set error(value) {
				cpuProcessError = value;
			},

			get data() {
				return cpuProcessData;
			},
			set data(value) {
				cpuProcessData = value;
			}
		},
		getTerminalId: () => terminalId,
		fetchCpuProcess,
		showToast
	});

	let tooltipComponent: ItemTooltip;
	let tooltipVisible = $state(false);
	let tooltipHtmlLines = $state<string[]>([]);

	const tooltipController = createTooltipController({
		getGrid: () => inventoryGridComponent,
		getTooltip: () => tooltipComponent,
		setVisible: (visible) => {
			tooltipVisible = visible;
		},
		setHtmlLines: (lines) => {
			tooltipHtmlLines = lines;
		}
	});

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
				lastInventoryEventVersion = msg.version;
			},
			onSnapshot: (msg) => {
				if (msg.terminalId !== terminalId) return;

				inventoryState = new Map(msg.items.map((item) => [item.uniqueId, item]));
				inventoryStateDirty = true;
				lastInventoryEventVersion = msg.version;

				// Clear production stats and cache for this terminal
				productionStats.clearTerminal(terminalId);
				productionRateCache.clear();

				// Load persisted history first, then set baselines
				// This prevents time-travel issues where resetBaseline(now) followed by
				// recordUpdate(old_timestamp) would corrupt the window point order
				void loadTerminalHistory(terminalId).then((historyMap) => {
					const now = Date.now();

					// Load historical points for items that have history
					for (const [uniqueId, points] of historyMap) {
						productionStats.loadHistoricalPoints(terminalId, uniqueId, points);
						chartViewedItems.add(uniqueId);
					}

					// Set baselines for all current items
					for (const item of msg.items) {
						const entry = productionStats.getEntry(terminalId, item.uniqueId);
						if (!entry) {
							// No history, create fresh baseline
							productionStats.resetBaseline(terminalId, item.uniqueId, item.amount, now);
						} else if (entry.lastPoint.amount !== item.amount) {
							// Has history but amount changed, record update
							productionStats.recordUpdate(terminalId, item.uniqueId, item.amount, now);
						}
						// If amount is same as history, no action needed
					}
				});

				void flushInventoryState();
			},
			onDelta: (msg) => {
				if (msg.terminalId !== terminalId) return;
				if (lastInventoryEventVersion == null) return;
				if (msg.version !== lastInventoryEventVersion + 1) {
					showToast('Inventory stream out of sync, resyncing...', 'error');
					startInventoryEventStream();
					return;
				}

				lastInventoryEventVersion = msg.version;
				const now = Date.now();

				for (const id of msg.removed) {
					inventoryState.delete(id);
					// Reset baseline to 0 (removes entry)
					productionStats.resetBaseline(terminalId, id, 0, now);
				}

				for (const item of msg.added) {
					inventoryState.set(item.uniqueId, item);
					// Reset baseline for newly added items (don't count as production spike)
					productionStats.resetBaseline(terminalId, item.uniqueId, item.amount, now);
				}

				for (const patch of msg.updated) {
					const prev = inventoryState.get(patch.uniqueId);
					if (!prev) continue;

					// Record update for production stats (before changing inventoryState)
					if (prev.amount !== patch.amount) {
						productionStats.recordUpdate(terminalId, patch.uniqueId, patch.amount, now);

						// Update cache immediately for this item
						const newRate = productionStats.getRate(terminalId, patch.uniqueId, now);
						productionRateCache.set(patch.uniqueId, { rate: newRate, timestamp: now });

						// Persist if this item was viewed in chart
						if (chartViewedItems.has(patch.uniqueId)) {
							const points = productionStats.getPoints(terminalId, patch.uniqueId);
							if (points) {
								queueHistoryUpdate(terminalId, patch.uniqueId, points);
							}
						}
					}

					const nextName = patch.name ?? prev.name;
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

	// Track previous terminal ID to detect changes
	let previousTerminalId = '';

	// React to terminal changes from the global store
	// Use $terminalIdStore directly to ensure proper reactivity
	$effect(() => {
		const currentTerminalId = $terminalIdStore;
		if (currentTerminalId !== previousTerminalId) {
			const wasEmpty = !previousTerminalId;
			previousTerminalId = currentTerminalId;

			if (!currentTerminalId) {
				// Terminal disconnected
				stopInventoryEventStream();
				inventoryItems = [];
				inventoryState = new Map();
				visibleItems = [];
				cpus = [];
				productionStats.clearAll();
				productionRateCache.clear();
				chartViewedItems.clear();
			} else if (!wasEmpty || currentTerminalId) {
				// Terminal changed or first connection
				cpuProcessController.closeModal();
				inventoryItems = [];
				inventoryState = new Map();
				visibleItems = [];
				productionStats.clearAll();
				productionRateCache.clear();
				chartViewedItems.clear();
				void loadCpus();
				startInventoryEventStream();
				void inventoryConfigController.loadForTerminal(currentTerminalId);
			}
		}
	});

	function computeVisible(items: InventoryItem[]): InventoryItem[] {
		return computeVisibleItems(items, { filter, searchTerm, sort });
	}

	async function applyVisibleItems(
		nextVisible: InventoryItem[],
		preserveAnchor: boolean
	): Promise<void> {
		const anchor = preserveAnchor ? captureScrollAnchor(inventoryGridComponent) : null;
		visibleItems = nextVisible;
		await tick();

		if (preserveAnchor) {
			restoreScrollAnchor(inventoryGridComponent, anchor);
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

	function refreshData(): void {
		startInventoryEventStream();
	}

	function openTerminalDropdown(): void {
		toggleTerminalDropdown();
	}

	function selectTerminal(next: TerminalInfo): void {
		// The reactive block handles page-specific logic when terminalId changes
		selectTerminalFromStore(next);
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
		craftMissingItems = [];
		craftOpen = true;
	}

	function closeCraftModal(): void {
		craftOpen = false;
	}

	function openProductionChartModal(item: InventoryItem): void {
		productionChartItem = item;
		productionChartOpen = true;
		// Mark item as viewed for persistence
		chartViewedItems.add(item.uniqueId);
	}

	function closeProductionChartModal(): void {
		productionChartOpen = false;
		productionChartItem = null;
	}

	/**
	 * Handle right-click on inventory item to show production chart.
	 */
	function handleItemContextMenu(item: InventoryItem, _event: MouseEvent): void {
		openProductionChartModal(item);
	}

	/**
	 * Get production points for an item (for chart).
	 */
	function getProductionPoints(uniqueId: string) {
		if (!terminalId) return null;
		return productionStats.getPoints(terminalId, uniqueId);
	}

	function handleCraftRetry(): void {
		craftMissingItems = [];
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
			if (e instanceof CraftMissingError) {
				craftMissingItems = e.missing;
			} else {
				showToast(e instanceof Error ? e.message : 'Craft request failed', 'error');
			}
		}
	}

	function onSearchKeyUp(e: KeyboardEvent): void {
		if (e.key === 'Enter') {
			executeSearchNow();
		}
	}

	function startApp(): () => void {
		// Terminal polling is handled globally in +layout.svelte
		void loadCpus();

		const cpuTimer = window.setInterval(() => void loadCpus(), 2000);

		const onKeyDown = (e: KeyboardEvent) => {
			if (e.key === 'Escape') {
				closeCraftModal();
				cpuProcessController.closeModal();
				inventoryConfigController.closeSettingsModal();
			}
		};

		document.addEventListener('keydown', onKeyDown);

		return () => {
			clearInterval(cpuTimer);
			cpuProcessController.destroy();
			document.removeEventListener('keydown', onKeyDown);
			stopInventoryEventStream();
			tooltipController.destroy();
			applySearchDebounced.cancel();
			inventoryConfigController.destroy();
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

		// Force flush history on page unload
		const handleBeforeUnload = () => {
			forceFlushHistory();
		};
		window.addEventListener('beforeunload', handleBeforeUnload);

		return () => {
			unsubscribe();
			window.removeEventListener('beforeunload', handleBeforeUnload);
			forceFlushHistory();
			if (appCleanup) {
				appCleanup();
				appCleanup = null;
			}
		};
	});

	onDestroy(() => stopInventoryEventStream());
	onDestroy(() => cpuProcessController.destroy());
	onDestroy(() => tooltipController.destroy());
	onDestroy(() => inventoryConfigController.destroy());
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

		<div class="flex items-center justify-between px-4 lg:px-6 py-4 relative h-[72px]">
			<!-- Left: Logo, Title, Selector & Settings -->
			<div class="flex items-center gap-4">
				<!-- Logo & Title -->
				<div class="flex items-center gap-3">
					<div
						class="w-10 h-10 rounded-xl bg-gradient-to-br from-accent to-accent-secondary flex items-center justify-center shadow-lg shadow-accent/20"
					>
						<img src="logo.svg" alt="AE2" class="w-6 h-6" style="image-rendering: pixelated;" />
					</div>
					<h1 class="text-2xl md:text-3xl font-display font-normal tracking-tight text-foreground">
						AE2 <GradientText>Web</GradientText>
					</h1>
				</div>

				<div class="w-px h-8 bg-border/50"></div>

				<!-- Terminal Selector -->
				<TerminalSelector
					{online}
					displayName={terminalDisplayName}
					{terminalId}
					{connectedTerminals}
					dropdownOpen={terminalDropdownOpen}
					onToggleDropdown={openTerminalDropdown}
					onSelectTerminal={selectTerminal}
				/>

				<!-- Settings -->
				<button
					type="button"
					class="btn btn-ghost btn-sm btn-square rounded-full"
					title="Inventory Settings"
					aria-label="Inventory Settings"
					onclick={() => {
						if (terminalId) {
							void inventoryConfigController.openSettingsModalForTerminal({
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
				</button>
			</div>

			<!-- Center: Navigation Tabs -->
			<div class="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2">
				<div class="tabs tabs-boxed bg-muted/50 p-1">
					<a
						href={resolve('/')}
						class="tab tab-sm {$page.url.pathname === '/' ? 'tab-active' : ''}"
					>
						Inventory
					</a>
					<a
						href={resolve('/dashboard')}
						class="tab tab-sm {$page.url.pathname === '/dashboard' ? 'tab-active' : ''}"
					>
						Dashboard
					</a>
				</div>
			</div>

			<!-- Right: Stats -->
			<div
				class="stats stats-horizontal hidden md:inline-grid bg-muted/50 border border-border/50 rounded-full"
			>
				<div class="stat place-items-center py-2 px-6">
					<div
						class="stat-title text-[10px] uppercase tracking-widest text-muted-foreground font-bold"
					>
						Items
					</div>
					<div class="stat-value font-mono text-sm font-semibold">
						{formatAmount(totalItemAmount)}
					</div>
				</div>
				<div class="stat place-items-center py-2 px-6">
					<div
						class="stat-title text-[10px] uppercase tracking-widest text-muted-foreground font-bold"
					>
						Types
					</div>
					<div class="stat-value font-mono text-sm font-semibold">{formatNumber(typeCount)}</div>
				</div>
			</div>
		</div>
	</div>

	<!-- Main Content -->
	<div class="flex flex-1 overflow-hidden px-4 lg:px-6 pb-4 lg:pb-6 gap-6 z-0">
		<!-- Inventory Panel -->
		<div
			class="card bg-card border border-border/50 rounded-xl flex-1 flex flex-col overflow-hidden shadow-xl"
		>
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
				onItemContextMenu={handleItemContextMenu}
				onItemClick={openCraftModal}
				onToggleSection={inventoryConfigController.toggleSectionCollapsed}
				onMouseOver={tooltipController.onMouseOver}
				onMouseMove={tooltipController.onMouseMove}
				onMouseOut={tooltipController.onMouseOut}
				onScroll={onInventoryScroll}
			/>
		</div>

		<!-- Side Panel -->
		<CpuSidebar
			{cpus}
			onCpuClick={cpuProcessController.openModal}
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
	onClose={cpuProcessController.closeModal}
	onRefresh={() => void cpuProcessController.refresh()}
	onAutoRefreshChange={cpuProcessController.setAutoRefresh}
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
	onClose={inventoryConfigController.closeSettingsModal}
	onSave={inventoryConfigController.saveSettings}
	onWhitelistChange={(v) => (inventorySettingsWhitelistText = v)}
	onBlacklistChange={(v) => (inventorySettingsBlacklistText = v)}
	onCategoriesChange={(c) => (inventorySettingsCategories = c)}
/>

<!-- Craft Modal -->
<CraftModal
	open={craftOpen}
	item={craftItem}
	{cpus}
	missingItems={craftMissingItems}
	onCraft={handleCraft}
	onRetry={handleCraftRetry}
	onClose={closeCraftModal}
/>

<!-- Production Chart Modal -->
<ProductionChartModal
	open={productionChartOpen}
	item={productionChartItem}
	getPoints={getProductionPoints}
	getRate={getProductionRate}
	onClose={closeProductionChartModal}
/>

<!-- Login Modal -->
<LoginModal open={needsLogin} error={loginError} onLogin={handleLogin} onClear={handleLoginClear} />

<!-- Tooltip -->
<ItemTooltip bind:this={tooltipComponent} visible={tooltipVisible} htmlLines={tooltipHtmlLines} />

<ToastHost />
