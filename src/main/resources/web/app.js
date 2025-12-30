// AE2 Web Terminal - Frontend Application
const API_BASE = '';
let currentPage = 0;
let totalPages = 1;
let currentSearch = '';
let selectedItem = null;
let refreshInterval = null;
let currentLang = null;

const MOD_TARGET_MC_VERSION = '1.20.1';
const CDN_ITEM_ICON_URL = 'https://assets.mcasset.cloud/1.20.1/assets/minecraft/textures/item/';
const CDN_BLOCK_ICON_URL = 'https://assets.mcasset.cloud/1.20.1/assets/minecraft/textures/block/';
const MC_LANG_CDN_URL = `https://assets.mcasset.cloud/${MOD_TARGET_MC_VERSION}/assets/minecraft/lang/en_us.json`;
const PLACEHOLDER_ICON_URL = (() => {
    const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32">
            <rect x="1" y="1" width="30" height="30" rx="6" fill="#1a1a25" stroke="#2a2a3a" stroke-width="2"/>
            <text x="16" y="21" text-anchor="middle" font-family="Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif"
                  font-size="18" fill="#606070">?</text>
        </svg>
    `.trim();
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
})();

const PLACEHOLDER_ICON_TTL_MS = 60 * 1000;
const SERVER_ICON_ERROR_TTL_MS = 60 * 1000;

// Cache icon resolution results to avoid repeated 404/CDN fetches
const itemIconCache = new Map(); // itemId -> { src, source, expiresAt? }
const serverIconErrorCache = new Map(); // itemId -> expiresAt ms

let minecraftTranslations = null;
let minecraftTranslationsPromise = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

function initApp() {
    currentLang = getInitialLang();

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
                'X-AE2Web-Lang': currentLang || 'en_us',
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
        await loadMinecraftTranslations();

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

async function loadMinecraftTranslations() {
    if (minecraftTranslations !== null) return minecraftTranslations;
    if (minecraftTranslationsPromise) return minecraftTranslationsPromise;

    minecraftTranslationsPromise = (async () => {
        try {
            const response = await fetch(MC_LANG_CDN_URL);
            if (!response.ok) {
                throw new Error(`Failed to fetch translations (HTTP ${response.status})`);
            }

            const json = await response.json();
            minecraftTranslations = json && typeof json === 'object' ? json : {};
        } catch (error) {
            console.warn('Failed to load Minecraft translations from CDN:', error);
            minecraftTranslations = {};
        }

        return minecraftTranslations;
    })().finally(() => {
        minecraftTranslationsPromise = null;
    });

    return minecraftTranslationsPromise;
}

function translateItemName(item) {
    const displayName = item?.displayName ?? item?.name ?? '';
    if (!displayName) return displayName;

    // Best-effort heuristic: translation keys tend to have dots and start with lowercase.
    if (!displayName.includes('.') || /^[A-Z]/.test(displayName)) return displayName;

    const translationKey = item?.translationKey ?? displayName;
    const translated = minecraftTranslations?.[translationKey];
    return typeof translated === 'string' && translated.trim() ? translated : displayName;
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

    grid.innerHTML = data.items.map(item => {
        const itemName = translateItemName(item);
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
        const itemId = card.dataset.itemId;
        const itemName = card.dataset.itemName;
        card.addEventListener('click', () => openCraftModal(itemId, itemName));
    });

    grid.querySelectorAll('.item-icon-img').forEach(img => {
        const itemId = img.dataset.iconItemId;
        initItemIcon(img, itemId);
    });
}

function getServerIconUrl(itemId) {
    return `/api/item-icon?id=${encodeURIComponent(itemId)}`;
}

function getCdnIconUrl(itemId) {
    if (!itemId) return null;

    const [namespace, rawPath] = itemId.includes(':') ? itemId.split(':', 2) : ['minecraft', itemId];
    // CDN only hosts vanilla assets, avoid repeated failed fetches for modded items.
    if (namespace !== 'minecraft') return null;

    const path = (rawPath || '').trim();
    if (!path) return null;

    // Best-effort sanitization: keep typical registry characters only.
    const safePath = path.replace(/[^a-zA-Z0-9_./-]/g, '');
    if (!safePath) return null;

    return {
        itemUrl: `${CDN_ITEM_ICON_URL}${safePath}.png`,
        blockUrl: `${CDN_BLOCK_ICON_URL}${safePath}.png`
    };
}

function setIconSrc(img, itemId, src, source) {
    img.dataset.iconSource = source;
    img.crossOrigin = 'anonymous';
    img.src = src;
}

function hideEmojiForIcon(img) {
    const emoji = img.parentElement?.querySelector('.item-emoji');
    if (emoji) {
        emoji.style.display = 'none';
    }
}

function normalizeMaterialColor(value) {
    if (!value) return null;

    const raw = String(value).trim();
    if (!/^[0-9a-fA-F]{6}$/.test(raw)) return null;

    return raw.toUpperCase();
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
    icon.dataset.materialColor = color;
    icon.style.display = 'inline-block';

    img.dataset.iconSource = 'material';
    img.style.display = 'none';
    hideEmojiForIcon(img);
    return true;
}

function initItemIcon(img, itemId) {
    img.crossOrigin = 'anonymous';

    img.addEventListener('load', () => {
        hideEmojiForIcon(img);
        const source = img.dataset.iconSource;
        if (source === 'cdn-item' || source === 'cdn-block' || source === 'placeholder') {
            const src = img.currentSrc || img.src;
            if (source === 'cdn-item' || source === 'cdn-block') {
                itemIconCache.set(itemId, { src, source });
            } else {
                itemIconCache.set(itemId, { src, source, expiresAt: Date.now() + PLACEHOLDER_ICON_TTL_MS });
            }
        }
    });
    img.addEventListener('error', () => handleItemIconError(img, itemId));

    const cached = (() => {
        const entry = itemIconCache.get(itemId);
        if (!entry) return null;
        const expiresAt = entry.expiresAt;
        if (typeof expiresAt === 'number' && expiresAt <= Date.now()) {
            itemIconCache.delete(itemId);
            return null;
        }
        return entry;
    })();
    if (cached) {
        if (cached.source === 'placeholder' && showMaterialIcon(img)) {
            return;
        }
        setIconSrc(img, itemId, cached.src, cached.source);
        return;
    }

    const serverIconErrorUntil = serverIconErrorCache.get(itemId);
    if (typeof serverIconErrorUntil === 'number') {
        if (serverIconErrorUntil <= Date.now()) {
            serverIconErrorCache.delete(itemId);
        } else {
            const cdnUrls = getCdnIconUrl(itemId);
            if (cdnUrls) {
                setIconSrc(img, itemId, cdnUrls.itemUrl, 'cdn-item');
            } else if (showMaterialIcon(img)) {
                // Material-based placeholder for modded items with no server icon
            } else {
                setIconSrc(img, itemId, PLACEHOLDER_ICON_URL, 'placeholder');
            }
            return;
        }
    }

    setIconSrc(img, itemId, getServerIconUrl(itemId), 'server');
}

function handleItemIconError(img, itemId) {
    const source = img.dataset.iconSource;
    if (source === 'cdn-item') {
        const cdnUrls = getCdnIconUrl(itemId);
        if (cdnUrls) {
            setIconSrc(img, itemId, cdnUrls.blockUrl, 'cdn-block');
            return;
        }
    }
    if (source === 'cdn-block') {
        if (showMaterialIcon(img)) return;
        setIconSrc(img, itemId, PLACEHOLDER_ICON_URL, 'placeholder');
        return;
    }
    if (source === 'placeholder') {
        return;
    }
    if (source === 'material') {
        return;
    }

    // Source === 'server': avoid extra fetch to detect HTTP status, treat errors as missing and retry later.
    serverIconErrorCache.set(itemId, Date.now() + SERVER_ICON_ERROR_TTL_MS);

    const cdnUrls = getCdnIconUrl(itemId);
    if (cdnUrls) {
        setIconSrc(img, itemId, cdnUrls.itemUrl, 'cdn-item');
        return;
    }

    if (showMaterialIcon(img)) return;
    setIconSrc(img, itemId, PLACEHOLDER_ICON_URL, 'placeholder');
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

function getInitialLang() {
    const url = new URL(window.location.href);
    const fromQuery = url.searchParams.get('lang');
    const fromStorage = localStorage.getItem('ae2web.lang');
    const fromNavigator = navigator.language || 'en-US';

    const lang = normalizeMcLang(fromQuery || fromStorage || fromNavigator);
    localStorage.setItem('ae2web.lang', lang);
    return lang;
}

function normalizeMcLang(value) {
    if (!value) return 'en_us';

    const raw = value.toLowerCase().replace('-', '_');
    if (raw.startsWith('zh')) {
        // Default Chinese locale to mainland unless explicitly TW/HK
        if (raw.includes('tw') || raw.includes('hk')) return 'zh_tw';
        return 'zh_cn';
    }
    if (raw.startsWith('en')) return 'en_us';

    // Best-effort: many MC lang codes match this pattern (e.g. ja_jp, pt_br)
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
