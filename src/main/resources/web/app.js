// AE2 Web Terminal - Frontend Application
const API_BASE = '';
let currentPage = 0;
let totalPages = 1;
let currentSearch = '';
let selectedItem = null;
let refreshInterval = null;
let currentLang = null;

const PLACEHOLDER_ICON_URL = (() => {
    const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
            <rect x="1" y="1" width="30" height="30" rx="6" fill="#1a1a25" stroke="#2a2a3a" stroke-width="2"/>
            <text x="16" y="21" text-anchor="middle" font-family="Inter, system-ui, sans-serif"
                  font-size="18" fill="#606070">?</text>
        </svg>
    `.trim();
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
})();

// Initialize
document.addEventListener('DOMContentLoaded', initApp);

function initApp() {
    currentLang = getInitialLang();

    document.getElementById('search-input').addEventListener('keyup', (e) => {
        if (e.key === 'Enter') performSearch();
    });
    document.getElementById('search-btn').addEventListener('click', performSearch);
    document.getElementById('prev-page').addEventListener('click', () => changePage(-1));
    document.getElementById('next-page').addEventListener('click', () => changePage(1));

    checkStatus();
    loadTerminals();
    loadItems();

    refreshInterval = setInterval(() => {
        checkStatus();
        loadItems();
        loadTerminals();
    }, 5000);
}

// API
async function apiCall(endpoint, options = {}) {
    const response = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers: {
            'Content-Type': 'application/json',
            'X-AE2Web-Lang': currentLang || 'en_us',
            ...options.headers
        }
    });

    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || 'API Error');
    }

    return response.json();
}

async function checkStatus() {
    try {
        const status = await apiCall('/api/status');
        updateConnectionStatus(true, status.terminals);
    } catch {
        updateConnectionStatus(false, 0);
    }
}

function updateConnectionStatus(connected, terminalCount) {
    const statusEl = document.getElementById('connection-status');
    const statusText = statusEl.querySelector('.status-text');
    const terminalCountEl = document.getElementById('terminal-count');

    statusEl.classList.toggle('connected', connected);
    statusEl.classList.toggle('disconnected', !connected);
    statusText.textContent = connected ? 'Connected' : 'Disconnected';
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
    document.getElementById('item-count').textContent = `${data.items.length} items`;
    document.getElementById('total-items').textContent = `${formatNumber(data.total)} total`;

    if (data.items.length === 0) {
        grid.innerHTML = `
            <div class="loading-state">
                <p>No items found</p>
                ${currentSearch ? '<p style="color: var(--text-muted);">Try a different search term</p>' : ''}
            </div>
        `;
        return;
    }

    grid.innerHTML = data.items.map(item => {
        const itemName = item.name || item.id;
        const materialColor = normalizeMaterialColor(item.materialColor);
        const materialAttr = materialColor ? ` data-material-color="${escapeHtml(materialColor)}"` : '';

        return `
            <div class="item-card" data-item-id="${escapeHtml(item.id)}" data-item-name="${escapeHtml(itemName)}">
                <div class="item-icon">
                    <img class="item-icon-img" loading="lazy" alt="${escapeHtml(itemName)}"
                         data-icon-item-id="${escapeHtml(item.id)}"${materialAttr}>
                    <span class="item-emoji">${getItemEmoji(item.namespace)}</span>
                </div>
                <div class="item-name" title="${escapeHtml(itemName)}">${escapeHtml(itemName)}</div>
                <div class="item-id" title="${escapeHtml(item.id)}">${escapeHtml(item.id)}</div>
                <div class="item-amount">${formatNumber(item.amount)}</div>
            </div>
        `;
    }).join('');

    wireInventoryGrid(grid);
}

function wireInventoryGrid(grid) {
    grid.querySelectorAll('.item-card').forEach(card => {
        card.addEventListener('click', () => openCraftModal(card.dataset.itemId, card.dataset.itemName));
    });

    grid.querySelectorAll('.item-icon-img').forEach(img => {
        initItemIcon(img, img.dataset.iconItemId);
    });
}

// Icon handling - prerender only
function getPrerenderIconUrl(itemId) {
    return `/api/prerender/image?id=${encodeURIComponent(itemId)}`;
}

function initItemIcon(img, itemId) {
    img.crossOrigin = 'anonymous';

    img.addEventListener('load', () => hideEmojiForIcon(img));
    img.addEventListener('error', () => handleIconError(img));

    img.src = getPrerenderIconUrl(itemId);
}

function handleIconError(img) {
    // Try material color placeholder for GTCEu items
    if (showMaterialIcon(img)) return;

    // Final fallback: placeholder
    img.src = PLACEHOLDER_ICON_URL;
}

function hideEmojiForIcon(img) {
    const emoji = img.parentElement?.querySelector('.item-emoji');
    if (emoji) emoji.style.display = 'none';
}

function normalizeMaterialColor(value) {
    if (!value) return null;
    const raw = String(value).trim();
    return /^[0-9a-fA-F]{6}$/.test(raw) ? raw.toUpperCase() : null;
}

function showMaterialIcon(img) {
    const color = normalizeMaterialColor(img.dataset.materialColor);
    if (!color) return false;

    const container = img.parentElement;
    if (!container) return false;

    let icon = container.querySelector('.material-icon');
    if (!icon) {
        icon = document.createElement('div');
        icon.className = 'material-icon';
        container.insertBefore(icon, img.nextSibling);
    }

    icon.style.backgroundColor = `#${color}`;
    icon.style.display = 'inline-block';
    img.style.display = 'none';
    hideEmojiForIcon(img);
    return true;
}

// Pagination
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

// Craft Modal
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
        await apiCall('/api/craft', {
            method: 'POST',
            body: JSON.stringify({ itemId: selectedItem.id, amount })
        });
        showToast(`Crafting ${amount}x ${selectedItem.name}`, 'success');
        closeCraftModal();
    } catch (error) {
        showToast(`Failed to start crafting: ${error.message}`, 'error');
    }
}

// Utilities
function formatNumber(num) {
    if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
    return num.toString();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getItemEmoji(namespace) {
    const emojis = {
        'minecraft': '🧱', 'ae2': '⚡', 'create': '⚙️',
        'thermal': '🔥', 'mekanism': '⚛️', 'botania': '🌸'
    };
    return emojis[namespace] || '📦';
}

function getInitialLang() {
    const url = new URL(window.location.href);
    const lang = normalizeMcLang(
        url.searchParams.get('lang') ||
        localStorage.getItem('ae2web.lang') ||
        navigator.language
    );
    localStorage.setItem('ae2web.lang', lang);
    return lang;
}

function normalizeMcLang(value) {
    if (!value) return 'en_us';
    const raw = value.toLowerCase().replace('-', '_');
    if (raw.startsWith('zh')) {
        return (raw.includes('tw') || raw.includes('hk')) ? 'zh_tw' : 'zh_cn';
    }
    if (raw.startsWith('en')) return 'en_us';
    return raw.replace(/[^a-z0-9_]/g, '') || 'en_us';
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

// Modal events
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeCraftModal();
});

document.getElementById('craft-modal').addEventListener('click', (e) => {
    if (e.target.id === 'craft-modal') closeCraftModal();
});
