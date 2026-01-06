<script lang="ts">
	import { onMount, onDestroy } from 'svelte';
	import { resolve } from '$app/paths';
	import { page } from '$app/stores';

	import { openInventorySse } from '$lib/api/inventory';
	import { fetchDashboardConfig, saveDashboardConfig } from '$lib/api/dashboardConfig';
	import type { InventoryItem, TerminalInfo, WidgetConfig } from '$lib/api/types';
	import ToastHost from '$lib/components/ToastHost.svelte';
	import GradientText from '$lib/components/GradientText.svelte';
	import HeroGraphic from '$lib/components/HeroGraphic.svelte';
	import LoginModal from '$lib/components/modals/LoginModal.svelte';
	import AddWidgetModal from '$lib/components/modals/AddWidgetModal.svelte';
	import EditWidgetModal from '$lib/components/modals/EditWidgetModal.svelte';
	import TerminalSelector from '$lib/components/TerminalSelector.svelte';
	import DashboardGrid from '$lib/components/dashboard/DashboardGrid.svelte';
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
	import { ProductionStatsTracker } from '$lib/utils/productionStats';

	let loginError: string | null = $state(null);
	let needsLogin = $derived(!$authToken || !$authToken.trim());
	let appCleanup: (() => void) | null = null;

	// Use global terminal state from store
	let online = $derived($onlineStore);
	let terminalId = $derived($terminalIdStore);
	let terminalDisplayName = $derived($terminalDisplayNameStore);
	let terminalDropdownOpen = $derived($terminalDropdownOpenStore);
	let connectedTerminals = $derived($connectedTerminalsStore);

	let inventoryItems: InventoryItem[] = $state([]);
	let inventoryEventSource: ReturnType<typeof openInventorySse> | null = null;
	let lastInventoryEventVersion: number | null = null;

	// eslint-disable-next-line svelte/prefer-svelte-reactivity
	let inventoryState = new Map<string, InventoryItem>();

	// Production stats tracker
	const productionStats = new ProductionStatsTracker();

	// Dashboard config
	let widgets: WidgetConfig[] = $state([]);
	let configLoaded = $state(false);
	let configSaveTimeout: ReturnType<typeof setTimeout> | null = null;

	// Add Widget Modal
	let addWidgetModalOpen = $state(false);

	// Edit Widget Modal
	let editWidgetModalOpen = $state(false);
	let editingWidget: WidgetConfig | null = $state(null);

	// Default widgets for new users
	function getDefaultWidgets(): WidgetConfig[] {
		return [
			{
				id: 'default-overview',
				type: 'network-overview',
				title: 'Network Overview',
				w: 1,
				h: 1,
				x: 0,
				y: 0,
				options: {
					showProduction: true,
					showConsumption: true,
					showNetChange: true
				}
			},
			{
				id: 'default-top-production',
				type: 'top-items',
				title: 'Top Production',
				w: 1,
				h: 2,
				x: 1,
				y: 0,
				options: {
					mode: 'production',
					count: 5,
					filter: 'all'
				}
			},
			{
				id: 'default-top-consumption',
				type: 'top-items',
				title: 'Top Consumption',
				w: 1,
				h: 2,
				x: 2,
				y: 0,
				options: {
					mode: 'consumption',
					count: 5,
					filter: 'all'
				}
			}
		];
	}

	async function loadDashboardConfig(): Promise<void> {
		const targetTerminalId = terminalId;
		if (!targetTerminalId) return;

		try {
			const config = await fetchDashboardConfig(targetTerminalId);
			// Guard: only apply if still the same terminal
			if (terminalId !== targetTerminalId) return;

			if (config.widgets && config.widgets.length > 0) {
				widgets = config.widgets;
			} else {
				widgets = getDefaultWidgets();
			}
			configLoaded = true;
		} catch (e) {
			console.error('Failed to load dashboard config', e);
			// Guard: only apply if still the same terminal
			if (terminalId !== targetTerminalId) return;
			widgets = getDefaultWidgets();
			configLoaded = true;
		}
	}

	function cancelPendingSave(): void {
		if (configSaveTimeout) {
			clearTimeout(configSaveTimeout);
			configSaveTimeout = null;
		}
	}

	function scheduleSaveConfig(): void {
		if (!terminalId || !configLoaded) return;

		cancelPendingSave();

		// Capture current state at schedule time
		const targetTerminalId = terminalId;
		const widgetsToSave = [...widgets];

		configSaveTimeout = setTimeout(async () => {
			// Guard: only save if still the same terminal
			if (terminalId !== targetTerminalId) return;

			try {
				await saveDashboardConfig(targetTerminalId, { widgets: widgetsToSave });
			} catch (e) {
				console.error('Failed to save dashboard config', e);
				// Only show toast if still on same terminal
				if (terminalId === targetTerminalId) {
					showToast('Failed to save dashboard layout', 'error');
				}
			}
		}, 500);
	}

	function stopInventoryEventStream(): void {
		lastInventoryEventVersion = null;
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
				inventoryItems = msg.items;
				lastInventoryEventVersion = msg.version;

				// Reset production stats
				productionStats.clearTerminal(terminalId);
				const now = Date.now();
				for (const item of msg.items) {
					productionStats.resetBaseline(terminalId, item.uniqueId, item.amount, now);
				}
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
					productionStats.resetBaseline(terminalId, id, 0, now);
				}

				for (const item of msg.added) {
					inventoryState.set(item.uniqueId, item);
					productionStats.resetBaseline(terminalId, item.uniqueId, item.amount, now);
				}

				for (const patch of msg.updated) {
					const prev = inventoryState.get(patch.uniqueId);
					if (!prev) continue;

					if (prev.amount !== patch.amount) {
						productionStats.recordUpdate(terminalId, patch.uniqueId, patch.amount, now);
					}

					const nextName = patch.name ?? prev.name;
					inventoryState.set(patch.uniqueId, {
						...prev,
						amount: patch.amount,
						craftable: patch.craftable,
						name: nextName
					});
				}

				inventoryItems = Array.from(inventoryState.values());
			}
		});
	}

	// Track previous terminal ID to detect changes
	let previousTerminalId = '';

	// React to terminal changes from the global store
	$effect(() => {
		if (terminalId !== previousTerminalId) {
			previousTerminalId = terminalId;

			if (!terminalId) {
				// Terminal disconnected
				cancelPendingSave();
				stopInventoryEventStream();
				inventoryItems = [];
				inventoryState = new Map();
				productionStats.clearAll();
				configLoaded = false;
				widgets = [];
			} else {
				// Terminal changed or first connection
				cancelPendingSave();
				inventoryItems = [];
				inventoryState = new Map();
				productionStats.clearAll();
				configLoaded = false;
				widgets = [];
				startInventoryEventStream();
				void loadDashboardConfig();
			}
		}
	});

	function openTerminalDropdown(): void {
		toggleTerminalDropdown();
	}

	function selectTerminal(next: TerminalInfo): void {
		// The reactive effect handles page-specific logic when terminalId changes
		selectTerminalFromStore(next);
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

	function handleWidgetsChange(newWidgets: WidgetConfig[]): void {
		widgets = newWidgets;
		scheduleSaveConfig();
	}

	function handleEditWidget(widget: WidgetConfig): void {
		editingWidget = widget;
		editWidgetModalOpen = true;
	}

	function handleEditWidgetSave(updatedWidget: WidgetConfig): void {
		widgets = widgets.map((w) => (w.id === updatedWidget.id ? updatedWidget : w));
		scheduleSaveConfig();
		showToast('Widget updated', 'success');
	}

	function handleEditWidgetClose(): void {
		editWidgetModalOpen = false;
		editingWidget = null;
	}

	function handleDeleteWidget(widgetId: string): void {
		widgets = widgets.filter((w) => w.id !== widgetId);
		scheduleSaveConfig();
		showToast('Widget removed', 'success');
	}

	function handleAddWidget(): void {
		addWidgetModalOpen = true;
	}

	function handleAddWidgetConfirm(widget: WidgetConfig): void {
		widgets = [...widgets, widget];
		scheduleSaveConfig();
		showToast('Widget added', 'success');
	}

	function handleAddWidgetClose(): void {
		addWidgetModalOpen = false;
	}

	function startApp(): () => void {
		// Terminal polling is handled globally in +layout.svelte
		// Page-specific initialization is handled by the $effect watching terminalId

		const onKeyDown = (e: KeyboardEvent) => {
			if (e.key === 'Escape') {
				addWidgetModalOpen = false;
				editWidgetModalOpen = false;
			}
		};

		document.addEventListener('keydown', onKeyDown);

		return () => {
			document.removeEventListener('keydown', onKeyDown);
			cancelPendingSave();
			stopInventoryEventStream();
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
</script>

<!-- Main Container -->
<div class="flex flex-col h-screen bg-background text-foreground overflow-hidden font-sans">
	<!-- Hero / Header Section -->
	<div class="relative flex-none bg-background z-10 border-b border-border shadow-sm">
		<div class="absolute inset-0 pointer-events-none overflow-hidden">
			<div class="absolute top-0 right-0 w-1/2 h-full opacity-50">
				<HeroGraphic />
			</div>
			<div
				class="absolute bottom-0 left-0 w-full h-1 bg-gradient-to-r from-transparent via-border to-transparent opacity-50"
			></div>
		</div>

		<div class="flex items-center justify-between px-4 lg:px-6 py-4 relative">
			<!-- Left: Logo, Title, Nav, Selector -->
			<div class="flex items-center gap-4">
				<!-- Logo & Title -->
				<div class="flex items-center gap-3">
					<div
						class="w-10 h-10 rounded-xl bg-gradient-to-br from-accent to-accent-secondary flex items-center justify-center shadow-lg shadow-accent/20"
					>
						<img src="/logo.svg" alt="AE2" class="w-6 h-6" style="image-rendering: pixelated;" />
					</div>
					<h1 class="text-2xl md:text-3xl font-display font-normal tracking-tight text-foreground">
						AE2 <GradientText>Web</GradientText>
					</h1>
				</div>

				<div class="w-px h-8 bg-border/50"></div>

				<!-- Navigation Tabs -->
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
			</div>

			<!-- Right: Add Widget Button -->
			<div class="flex items-center gap-3">
				<button type="button" class="btn btn-primary btn-sm gap-2" onclick={handleAddWidget}>
					<svg
						class="w-4 h-4"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
					>
						<path stroke-linecap="round" stroke-linejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
					</svg>
					Add Widget
				</button>
			</div>
		</div>
	</div>

	<!-- Main Content -->
	<div class="flex-1 overflow-auto p-4 lg:p-6">
		<DashboardGrid
			{widgets}
			{terminalId}
			{inventoryItems}
			{productionStats}
			onWidgetsChange={handleWidgetsChange}
			onEditWidget={handleEditWidget}
			onDeleteWidget={handleDeleteWidget}
		/>
	</div>
</div>

<!-- Login Modal -->
<LoginModal open={needsLogin} error={loginError} onLogin={handleLogin} onClear={handleLoginClear} />

<!-- Add Widget Modal -->
<AddWidgetModal
	open={addWidgetModalOpen}
	{inventoryItems}
	onAdd={handleAddWidgetConfirm}
	onClose={handleAddWidgetClose}
/>

<!-- Edit Widget Modal -->
<EditWidgetModal
	open={editWidgetModalOpen}
	widget={editingWidget}
	{inventoryItems}
	onSave={handleEditWidgetSave}
	onClose={handleEditWidgetClose}
/>

<ToastHost />
