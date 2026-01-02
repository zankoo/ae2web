import { state } from './state.js';
import { apiRequest } from './api.js';
import { debounce } from './utils.js';
import { bindTerminalSelectHandlers, loadTerminals } from './terminals.js';
import { bindInventoryClickHandlers, loadInventory, resortInventory, startInventoryEventStream } from './inventory.js';
import { bindCraftModalHandlers } from './craft.js';
import { bindCpuClickHandlers, loadCPUs } from './cpus.js';

document.addEventListener('DOMContentLoaded', () => {
    initTerminal().catch(e => console.error('Failed to init terminal', e));
});

async function initTerminal() {
    const searchInput = document.getElementById('search-input');
    if (searchInput) {
        searchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') executeSearch();
        });
        searchInput.addEventListener('input', debounce(executeSearch, 500));
    }

    bindTerminalSelectHandlers();
    bindFilterClickHandlers();
    bindSortClickHandlers();
    bindQuickActions();
    bindCraftModalHandlers();
    bindInventoryClickHandlers();
    bindCpuClickHandlers();

    await loadTerminals();
    await refreshData();
    await loadCPUs();
    startInventoryEventStream();

    setInterval(checkConnection, 5000);
    setInterval(loadTerminals, 10000);
    setInterval(loadCPUs, 2000);
}

function bindFilterClickHandlers() {
    const toggle = document.querySelector('.filter-toggle');
    if (!toggle) return;

    toggle.addEventListener('click', (e) => {
        const btn = e.target.closest('button.filter-btn');
        if (!btn || !toggle.contains(btn)) return;
        const filter = btn.dataset.filter;
        if (!filter) return;
        setFilter(filter);
    });
}

function bindSortClickHandlers() {
    const toggle = document.querySelector('.sort-toggle');
    if (!toggle) return;

    toggle.addEventListener('click', (e) => {
        const btn = e.target.closest('button.filter-btn');
        if (!btn || !toggle.contains(btn)) return;
        const sort = btn.dataset.sort;
        if (!sort) return;
        setSort(sort);
    });
}

function bindQuickActions() {
    const grid = document.querySelector('.action-grid');
    if (!grid) return;

    grid.addEventListener('click', (e) => {
        const btn = e.target.closest('button.action-btn');
        if (!btn || !grid.contains(btn)) return;

        const action = btn.dataset.action;
        if (!action) return;

        if (action === 'refresh') {
            refreshData();
        } else if (action === 'search') {
            executeSearch();
        } else if (action === 'top') {
            document.querySelector('.inventory-scroll')?.scrollTo({ top: 0, behavior: 'smooth' });
        } else if (action === 'clear-search') {
            const input = document.getElementById('search-input');
            if (input) input.value = '';
            executeSearch();
        }
    });
}

async function refreshData() {
    await checkConnection();
    await loadInventory();
}

async function checkConnection() {
    try {
        const status = await apiRequest('/api/status');
        updateStatusDisplay(true, status.terminals);
    } catch {
        updateStatusDisplay(false, 0);
    }
}

function updateStatusDisplay(online) {
    const textEl = document.getElementById('status-text');
    const ledEl = document.getElementById('status-led');

    if (!textEl || !ledEl) return;

    if (online) {
        textEl.innerText = "ONLINE";
        textEl.className = "online";
        ledEl.className = "status-led";
    } else {
        textEl.innerText = "OFFLINE";
        textEl.className = "offline";
        ledEl.className = "status-led offline";
    }
}

function executeSearch() {
    const input = document.getElementById('search-input');
    state.currentSearch = input ? input.value.trim() : '';
    state.lastItemsHash = null;
    loadInventory();
}

function setFilter(filter) {
    state.currentFilter = filter;
    state.lastItemsHash = null;

    document.querySelectorAll('.filter-toggle .filter-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.filter === filter);
    });

    loadInventory();
}

function setSort(sort) {
    const normalized = String(sort);
    if (state.currentSort === normalized) {
        return;
    }

    state.currentSort = normalized;
    state.lastItemsHash = null;

    document.querySelectorAll('.sort-toggle .filter-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.sort === normalized);
    });

    if (!resortInventory()) {
        loadInventory();
    }
}
