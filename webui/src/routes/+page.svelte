<script lang="ts">
	import { flip } from 'svelte/animate';
	import { onDestroy, onMount, tick } from 'svelte';

	import debounce from 'lodash-es/debounce';

	import { fetchCpus } from '$lib/api/cpus';
	import { submitCraft } from '$lib/api/craft';
	import { fetchInventory, openInventorySse, type InventoryFilter } from '$lib/api/inventory';
	import { fetchTooltip } from '$lib/api/prerender';
	import { fetchStatus } from '$lib/api/status';
	import { fetchTerminals } from '$lib/api/terminals';
	import type { CpuDto, InventoryItem, TerminalInfo } from '$lib/api/types';
	import IconImage from '$lib/components/IconImage.svelte';
	import ToastHost from '$lib/components/ToastHost.svelte';
	import { showToast } from '$lib/stores/toast';
	import { PLACEHOLDER_ICON } from '$lib/constants';
	import { formatAmount, formatNumber } from '$lib/utils/format';
	import { sortInventoryItems } from '$lib/utils/inventorySort';
	import { legacyTextToHtml } from '$lib/utils/minecraftText';
	import { matchItem } from '$lib/utils/pinyin';
	import { createTooltipPositioner, type TooltipPositioner } from '$lib/utils/tooltipPositioner';

	type SortMode = 'amount' | 'name';

	let online = false;

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
	let inventoryRequestId = 0;

	let inventoryEventSource: EventSource | null = null;
	let lastInventoryEventVersion: number | null = null;

	let cpus: CpuDto[] = [];

	let craftOpen = false;
	let craftItem: InventoryItem | null = null;
	let craftAmount = 1;
	let craftCpuName = '';
	let craftOverlayEl: HTMLDivElement | null = null;

	const tooltipCache = new Map<string, string[]>();
	let tooltipEl: HTMLDivElement | null = null;
	let tooltipVisible = false;
	let tooltipHtmlLines: string[] = [];
	let tooltipHoverKey: string | null = null;
	let tooltipPositioner: TooltipPositioner | null = null;
	let tooltipLastX = 0;
	let tooltipLastY = 0;
	let tooltipHasLastPos = false;

	const requestInventoryReload = debounce(() => {
		if (document.hidden) return;
		void loadInventory(true);
	}, 250);

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
		requestInventoryReload.cancel();
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
				lastInventoryEventVersion = msg.version ?? null;
			},
			onInventory: (msg) => {
				if (msg.terminalId && msg.terminalId !== terminalId) {
					return;
				}
				if (msg.version != null && msg.version === lastInventoryEventVersion) {
					return;
				}
				lastInventoryEventVersion = msg.version ?? lastInventoryEventVersion;
				requestInventoryReload();
			}
		});
	}

	async function checkConnection(): Promise<void> {
		try {
			await fetchStatus();
			online = true;
		} catch {
			online = false;
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
				await loadInventory(false);
				await loadCpus();
				startInventoryEventStream();
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

	function restoreScrollAnchor(anchor: { id: string; topOffset: number } | null): void {
		if (!anchor?.id || !inventoryScrollEl || !inventoryGridEl) return;

		try {
			const selector = `[data-item-id="${escapeCssAttr(anchor.id)}"]`;
			const el = inventoryGridEl.querySelector(selector) as HTMLElement | null;
			if (!el) return;

			const scrollRect = inventoryScrollEl.getBoundingClientRect();
			const rect = el.getBoundingClientRect();
			const newOffset = rect.top - scrollRect.top;
			const delta = newOffset - anchor.topOffset;
			if (Number.isFinite(delta) && Math.abs(delta) > 0.5) {
				inventoryScrollEl.scrollTop += delta;
			}
		} catch {
			// ignore
		}
	}

	function computeVisible(items: InventoryItem[]): InventoryItem[] {
		let out = Array.isArray(items) ? items : [];

		if (searchTerm) {
			out = out.filter((item) => {
				const name = displayNameFor(item);
				return matchItem(name, searchTerm) || matchItem(item.id, searchTerm);
			});
		}

		return sortInventoryItems(out, sort);
	}

	async function recomputeInventory({ preserveAnchor }: { preserveAnchor: boolean }): Promise<void> {
		const anchor = preserveAnchor ? captureScrollAnchor() : null;
		visibleItems = computeVisible(inventoryItems);
		await tick();
		restoreScrollAnchor(anchor);
	}

	async function loadInventory(preserveAnchor: boolean): Promise<void> {
		const requestId = ++inventoryRequestId;
		try {
			const data = await fetchInventory(terminalId, filter);
			if (requestId !== inventoryRequestId) return;

			inventoryItems = Array.isArray(data.items) ? data.items : [];
			await recomputeInventory({ preserveAnchor });
		} catch (e) {
			console.error('Failed to load inventory', e);
		}
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
		await loadInventory(false);
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
			void loadInventory(false);
			void loadCpus();
			startInventoryEventStream();
		}
	}

	function setFilter(next: InventoryFilter): void {
		filter = next;
		void loadInventory(false);
	}

	function setSort(next: SortMode): void {
		if (sort === next) return;
		sort = next;
		void recomputeInventory({ preserveAnchor: sort === 'amount' });
	}

	function executeSearchNow(): void {
		applySearchDebounced.cancel();
		searchTerm = searchValue.trim();
		void recomputeInventory({ preserveAnchor: false });
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
			showToast(`Request sent: ${craftAmount}x ${displayNameFor(craftItem)}`, 'success');
			closeCraftModal();
			void loadCpus();
		} catch (e) {
			showToast(e instanceof Error ? e.message : 'Craft request failed', 'error');
		}
	}

	function showCpuDetail(index: number): void {
		const cpu = cpus[index];
		if (!cpu) return;

		const name = cpu.name || `CPU ${index + 1}`;
		let msg = name;

		if (cpu.busy && cpu.jobStatus) {
			const elapsed = Math.round(cpu.jobStatus.elapsedTimeNanos / 1e9);
			const mins = Math.floor(elapsed / 60);
			const secs = elapsed % 60;
			msg += ` - ${mins}m ${secs}s elapsed`;
		}

		showToast(msg, cpu.busy ? 'success' : '');
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

	function onSearchKeyUp(e: KeyboardEvent): void {
		if (e.key === 'Enter') {
			executeSearchNow();
		}
	}

	onMount(() => {
		void loadTerminalsPoll();
		void refreshData();
		void loadCpus();
		startInventoryEventStream();

		const statusTimer = window.setInterval(() => void checkConnection(), 5000);
		const terminalsTimer = window.setInterval(() => void loadTerminalsPoll(), 10000);
		const cpuTimer = window.setInterval(() => void loadCpus(), 2000);

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
				terminalDropdownOpen = false;
			}
		};

		document.addEventListener('click', onDocClick);
		document.addEventListener('keydown', onKeyDown);

		return () => {
			clearInterval(statusTimer);
			clearInterval(terminalsTimer);
			clearInterval(cpuTimer);
			document.removeEventListener('click', onDocClick);
			document.removeEventListener('keydown', onKeyDown);
			stopInventoryEventStream();
			applySearchDebounced.cancel();
			requestInventoryReload.cancel();
		};
	});

	onDestroy(() => stopInventoryEventStream());

	$: totalItemAmount = visibleItems.reduce((sum, item) => {
		const amount = Number(item?.amount);
		return sum + (Number.isFinite(amount) ? amount : 0);
	}, 0);

	$: typeCount = visibleItems.length;
	$: connectedTerminals = getConnectedTerminals(terminals);
	$: cpuCount = cpus.length;
	$: statusText = online ? 'ONLINE' : 'OFFLINE';
</script>

<div class="terminal-container">
	<header class="terminal-header">
		<div class="network-selector">
			<div class="network-frame">
				<div class="network-indicator">
					<svg class="network-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<path d="M12 2v4M5 9c0 0 3-2 7-2s7 2 7 2M6 13c0 0 2-1 6-1s6 1 6 1" stroke-linecap="square" />
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
							<svg class="select-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
								<polyline points="6 9 12 15 18 9" />
							</svg>
						</div>
						<div class="ae2-select-dropdown" class:hidden={!terminalDropdownOpen} id="terminal-dropdown-list">
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
				<div class="stat-value items" id="total-items">{formatNumber(totalItemAmount)}</div>
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
					<button class={`filter-btn ${sort === 'amount' ? 'active' : ''}`} type="button" on:click={() => setSort('amount')}
						>Amount</button
					>
					<button class={`filter-btn ${sort === 'name' ? 'active' : ''}`} type="button" on:click={() => setSort('name')}>Name</button>
				</div>
				<div class="filter-toggle">
					<button class={`filter-btn ${filter === 'all' ? 'active' : ''}`} type="button" on:click={() => setFilter('all')}>All</button>
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
				<svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
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
					{#if visibleItems.length === 0}
						<div style="grid-column:1/-1;display:flex;align-items:center;justify-content:center;height:200px;color:#64748b;font-size:0.9rem;">
							No items found
						</div>
					{:else}
						{#each visibleItems as item (item.uniqueId)}
							<div
								class={`item-slot ${item.type === 'fluid' ? 'fluid' : ''} ${item.craftable ? 'craftable' : ''}`}
								data-item-id={item.uniqueId}
								data-icon-id={item.iconKey}
								data-item-type={item.type}
								role="button"
								tabindex="-1"
								animate:flip={{ duration: sort === 'amount' ? 180 : 0 }}
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
								<IconImage iconKey={item.iconKey} alt={displayNameFor(item)} className="item-icon" />
								<span class="item-name">{displayNameFor(item)}</span>
								<span class="item-amount">{formatAmount(Number(item.amount))}</span>
							</div>
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
					<svg class="cpu-header-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
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
						{#each cpus as cpu, i (cpu.name ?? `cpu-${i}`)}
							<div
								class={`cpu-card ${cpu.busy ? 'busy' : 'idle'}`}
								role="button"
								tabindex="0"
								data-cpu-index={i}
								on:click={() => showCpuDetail(i)}
								on:keydown={(e) => {
									if (e.key === 'Enter' || e.key === ' ') {
										e.preventDefault();
										showCpuDetail(i);
									}
								}}
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
									{@const progressPct = job.totalItems > 0 ? Math.round((job.progress / job.totalItems) * 100) : 0}
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
												<span class="cpu-progress-text">{job.progress}/{job.totalItems}</span>
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
							</div>
						{/each}
					{/if}
				</div>
			</div>
		</aside>
	</main>
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
						<IconImage iconKey={craftItem.iconKey} alt={displayNameFor(craftItem)} className="" lazy={false} />
					{:else}
						<img src={PLACEHOLDER_ICON} alt="" />
					{/if}
				</div>
				<div class="craft-item-info">
					<div class="craft-item-name" id="craft-item-name">{craftItem ? displayNameFor(craftItem) : 'Item Name'}</div>
					<div class="craft-item-id" id="craft-item-id">
						{craftItem ? craftItem.id : 'minecraft:item'}{craftItem?.type === 'fluid' ? ' [Fluid]' : ''}
					</div>
				</div>
			</div>
			<div class="amount-section">
				<label for="craft-amount">Quantity</label>
				<input
					type="number"
					id="craft-amount"
					class="amount-input"
					min="1"
					max="64000"
					value={craftAmount}
					on:input={(e) => {
						const value = (e.target as HTMLInputElement | null)?.valueAsNumber;
						if (typeof value === 'number' && Number.isFinite(value)) {
							craftAmount = value;
						}
					}}
				/>
				<div class="quick-amounts">
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 1)}>1</button>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 16)}>16</button>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 64)}>64</button>
					<button class="quick-amount-btn" type="button" on:click={() => (craftAmount = 256)}>256</button>
				</div>
			</div>
			<div class="cpu-select-section">
				<label for="craft-cpu-select">Target CPU</label>
				<div class="cpu-select-wrapper">
					<svg class="cpu-select-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
						<rect x="4" y="4" width="16" height="16" rx="2" />
						<rect x="9" y="9" width="6" height="6" />
						<path d="M9 1v3M15 1v3M9 20v3M15 20v3" />
					</svg>
					<select id="craft-cpu-select" class="cpu-select" bind:value={craftCpuName}>
						<option value="">Auto (Any Available)</option>
						{#each cpus as cpu, i (cpu.name ?? `cpu-${i}`)}
							{@const cpuName = cpu.name || `CPU ${i + 1}`}
							{@const status = cpu.busy ? ' (Busy)' : ''}
							<option value={cpu.name ?? ''}>{cpuName}{status}</option>
						{/each}
					</select>
					<svg class="cpu-select-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
						<polyline points="6 9 12 15 18 9" />
					</svg>
				</div>
			</div>
		</div>
		<div class="modal-footer">
			<button id="craft-cancel-btn" class="btn-cancel" type="button" on:click={closeCraftModal}>Cancel</button>
			<button id="craft-confirm-btn" class="btn-confirm" type="button" on:click={() => void confirmCraft()}>
				<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
					<polyline points="20 6 9 17 4 12"></polyline>
				</svg>
				Confirm
			</button>
		</div>
	</div>
</div>

<div id="ae2web-tooltip" class="ae2web-tooltip" style:display={tooltipVisible ? 'block' : 'none'} bind:this={tooltipEl}>
	{#each tooltipHtmlLines as line, i (i)}
		<div class="ae2web-tooltip-line">{@html line}</div>
	{/each}
</div>

<ToastHost />
