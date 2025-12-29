// AE2 Web Terminal - Frontend Application
const API_BASE = '';
let currentPage = 0;
let totalPages = 1;
let currentSearch = '';
let selectedItem = null;
let refreshInterval = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

function initApp() {
    // Set up event listeners
    document.getElementById('search-input').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') {
            performSearch();
        }
    });

    document.getElementById('search-btn').addEventListener('click', performSearch);
    document.getElementById('prev-page').addEventListener('click', () => changePage(-1));
    document.getElementById('next-page').addEventListener('click', () => changePage(1));

    // Initial load
    checkStatus();
    loadTerminals();
    loadItems();

    // Set up auto-refresh
    refreshInterval = setInterval(() => {
        checkStatus();
        loadItems();
        loadTerminals();
    }, 5000);
}

// API Functions
async function apiCall(endpoint, options = {}) {
    try {
        const response = await fetch(`${API_BASE}${endpoint}`, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'API Error');
        }

        return await response.json();
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

async function checkStatus() {
    try {
        const status = await apiCall('/api/status');
        updateConnectionStatus(true, status.terminals);
    } catch (error) {
        updateConnectionStatus(false, 0);
    }
}

function updateConnectionStatus(connected, terminalCount) {
    const statusEl = document.getElementById('connection-status');
    const statusText = statusEl.querySelector('.status-text');
    const terminalCountEl = document.getElementById('terminal-count');

    if (connected) {
        statusEl.classList.remove('disconnected');
        statusEl.classList.add('connected');
        statusText.textContent = 'Connected';
    } else {
        statusEl.classList.remove('connected');
        statusEl.classList.add('disconnected');
        statusText.textContent = 'Disconnected';
    }

    terminalCountEl.textContent = `${terminalCount} Terminal${terminalCount !== 1 ? 's' : ''}`;
}

async function loadTerminals() {
    try {
        const terminals = await apiCall('/api/terminals');
        renderTerminals(terminals);
    } catch (error) {
        console.error('Failed to load terminals:', error);
    }
}

function renderTerminals(terminals) {
    const list = document.getElementById('terminal-list');

    if (terminals.length === 0) {
        list.innerHTML = '<li class="no-terminals">No terminals connected</li>';
        return;
    }

    list.innerHTML = terminals.map(t => `
        <li class="terminal-item" data-id="${t.id}">
            <span class="terminal-indicator"></span>
            <div class="terminal-info">
                <span class="terminal-id">${t.id}</span>
                <span class="terminal-pos">${t.position.x}, ${t.position.y}, ${t.position.z}</span>
            </div>
        </li>
    `).join('');
}

async function loadItems() {
    const grid = document.getElementById('inventory-grid');

    try {
        const params = new URLSearchParams({
            page: currentPage,
            pageSize: 50,
            search: currentSearch
        });

        const data = await apiCall(`/api/items?${params}`);
        renderItems(data);
        updatePagination(data);
    } catch (error) {
        grid.innerHTML = `
            <div class="loading-state">
                <p>Failed to load inventory</p>
                <p style="color: var(--text-muted); font-size: 0.875rem;">${error.message}</p>
            </div>
        `;
    }
}

function renderItems(data) {
    const grid = document.getElementById('inventory-grid');
    const countEl = document.getElementById('item-count');
    const totalEl = document.getElementById('total-items');

    countEl.textContent = `${data.items.length} items`;
    totalEl.textContent = `${formatNumber(data.total)} total`;

    if (data.items.length === 0) {
        grid.innerHTML = `
            <div class="loading-state">
                <p>No items found</p>
                ${currentSearch ? '<p style="color: var(--text-muted);">Try a different search term</p>' : ''}
            </div>
        `;
        return;
    }

    grid.innerHTML = data.items.map(item => `
        <div class="item-card" onclick="openCraftModal('${escapeHtml(item.id)}', '${escapeHtml(item.name)}')">
            <div class="item-icon">${getItemEmoji(item.namespace)}</div>
            <div class="item-name" title="${escapeHtml(item.name)}">${escapeHtml(item.name)}</div>
            <div class="item-id" title="${escapeHtml(item.id)}">${escapeHtml(item.id)}</div>
            <div class="item-amount">${formatNumber(item.amount)}</div>
        </div>
    `).join('');
}

function updatePagination(data) {
    totalPages = data.totalPages || 1;
    currentPage = data.page;

    document.getElementById('page-info').textContent = `Page ${currentPage + 1} of ${totalPages}`;
    document.getElementById('prev-page').disabled = currentPage === 0;
    document.getElementById('next-page').disabled = currentPage >= totalPages - 1;
}

function changePage(delta) {
    const newPage = currentPage + delta;
    if (newPage >= 0 && newPage < totalPages) {
        currentPage = newPage;
        loadItems();
    }
}

function performSearch() {
    currentSearch = document.getElementById('search-input').value.trim();
    currentPage = 0;
    loadItems();
}

// Craft Modal Functions
function openCraftModal(itemId, itemName) {
    selectedItem = { id: itemId, name: itemName };
    document.getElementById('craft-item-name').textContent = itemName;
    document.getElementById('craft-item-id').textContent = itemId;
    document.getElementById('craft-amount').value = 1;
    document.getElementById('craft-modal').classList.remove('hidden');
}

function closeCraftModal() {
    document.getElementById('craft-modal').classList.add('hidden');
    selectedItem = null;
}

function setCraftAmount(amount) {
    document.getElementById('craft-amount').value = amount;
}

async function submitCraft() {
    if (!selectedItem) return;

    const amount = parseInt(document.getElementById('craft-amount').value) || 1;

    try {
        const response = await apiCall('/api/craft', {
            method: 'POST',
            body: JSON.stringify({
                itemId: selectedItem.id,
                amount: amount
            })
        });

        showToast(`Crafting ${amount}x ${selectedItem.name}`, 'success');
        closeCraftModal();
    } catch (error) {
        showToast(`Failed to start crafting: ${error.message}`, 'error');
    }
}

// Utility Functions
function formatNumber(num) {
    if (num >= 1000000) {
        return (num / 1000000).toFixed(1) + 'M';
    } else if (num >= 1000) {
        return (num / 1000).toFixed(1) + 'K';
    }
    return num.toString();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getItemEmoji(namespace) {
    const emojis = {
        'minecraft': '🧱',
        'ae2': '⚡',
        'create': '⚙️',
        'thermal': '🔥',
        'mekanism': '⚛️',
        'botania': '🌸'
    };
    return emojis[namespace] || '📦';
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s ease reverse';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// Close modal on escape key
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
        closeCraftModal();
    }
});

// Close modal on background click
document.getElementById('craft-modal').addEventListener('click', (e) => {
    if (e.target.id === 'craft-modal') {
        closeCraftModal();
    }
});
