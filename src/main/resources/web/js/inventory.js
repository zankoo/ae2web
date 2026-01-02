import { API_BASE, AUTH_TOKEN, PLACEHOLDER_ICON, state } from './state.js';
import { apiRequest } from './api.js';
import { formatAmount, formatNumber, matchItem } from './utils.js';
import { openCraftModal } from './craft.js';
import { createTooltipPositioner } from './tooltipPositioner.js';
import { sortInventoryItems } from './inventorySort.js';

let iconObserver = null;
const loadedIcons = new Set();
const ICON_RETRY_BASE_MS = 1500;
const ICON_RETRY_MAX_MS = 30000;
const ICON_RETRY_MAX_ATTEMPTS = 6;
const iconRetryTimers = new WeakMap();

let inventoryItems = [];
const inventorySlotById = new Map();
const tooltipCache = new Map(); // iconKey -> string[]
let tooltipEl = null;
let tooltipVisible = false;
let tooltipHoverKey = null;
let tooltipPositioner = null;
let tooltipLastX = 0;
let tooltipLastY = 0;
let tooltipHasLastPos = false;

function iconUrlFor(iconKey) {
    if (AUTH_TOKEN) {
        return `/api/prerender/image?id=${encodeURIComponent(iconKey)}&token=${encodeURIComponent(AUTH_TOKEN)}`;
    }
    return `/api/prerender/image?id=${encodeURIComponent(iconKey)}`;
}

function scheduleIconRetry(img) {
    if (!img?.dataset?.iconSrc) {
        return;
    }

    const iconKey = img.dataset.iconKey;
    if (iconKey && loadedIcons.has(iconKey)) {
        return;
    }

    const attempts = Number(img.dataset.retryAttempts || '0');
    if (attempts >= ICON_RETRY_MAX_ATTEMPTS) {
        return;
    }

    const nextAttempts = attempts + 1;
    img.dataset.retryAttempts = String(nextAttempts);

    const delay = Math.min(ICON_RETRY_BASE_MS * (2 ** (nextAttempts - 1)), ICON_RETRY_MAX_MS);
    const prevTimer = iconRetryTimers.get(img);
    if (prevTimer) {
        clearTimeout(prevTimer);
    }

    const timer = setTimeout(() => {
        iconRetryTimers.delete(img);
        if (!img.isConnected) {
            return;
        }
        if (iconKey && loadedIcons.has(iconKey)) {
            return;
        }
        const iconSrc = img.dataset.iconSrc;
        if (!iconSrc) {
            return;
        }
        img.src = iconSrc;
    }, delay);

    iconRetryTimers.set(img, timer);
}

export function bindInventoryClickHandlers() {
    const grid = document.getElementById('inventory-grid');
    if (!grid) return;

    grid.addEventListener('click', (e) => {
        const slot = e.target.closest('.item-slot.craftable');
        if (!slot || !grid.contains(slot)) return;

        const craftId = slot.dataset.craftId;
        if (!craftId) return;

        const craftType = slot.dataset.craftType || 'item';
        const craftIconKey = slot.dataset.craftIconKey;
        openCraftModal(
            craftId,
            slot.dataset.craftName || craftId,
            craftType,
            craftIconKey
        );
    });

    bindInventoryTooltipHandlers(grid);
}

export function stopInventoryEventStream() {
    if (state.inventoryReloadTimer) {
        clearTimeout(state.inventoryReloadTimer);
        state.inventoryReloadTimer = null;
    }
    state.lastInventoryEventVersion = null;

    if (state.inventoryEventSource) {
        try {
            state.inventoryEventSource.close();
        } catch {
        }
        state.inventoryEventSource = null;
    }
}

function requestInventoryReload() {
    if (state.inventoryReloadTimer) {
        clearTimeout(state.inventoryReloadTimer);
    }
    state.inventoryReloadTimer = setTimeout(() => {
        state.inventoryReloadTimer = null;
        if (document.hidden) return;
        loadInventory({ refresh: true });
    }, 250);
}

export function startInventoryEventStream() {
    stopInventoryEventStream();

    const terminalId = state.currentTerminalId;
    if (!terminalId) return;

    const params = new URLSearchParams({ terminalId });
    if (AUTH_TOKEN) {
        params.append('token', AUTH_TOKEN);
    }
    const url = `${API_BASE}/api/events/inventory?${params}`;

    try {
        const es = new EventSource(url);
        state.inventoryEventSource = es;

        es.addEventListener('hello', (evt) => {
            try {
                const msg = JSON.parse(evt.data);
                state.lastInventoryEventVersion = msg.version ?? null;
            } catch {
            }
        });

        es.addEventListener('inventory', (evt) => {
            try {
                const msg = JSON.parse(evt.data);
                if (msg.terminalId && msg.terminalId !== state.currentTerminalId) {
                    return;
                }
                if (msg.version != null && msg.version === state.lastInventoryEventVersion) {
                    return;
                }
                state.lastInventoryEventVersion = msg.version ?? state.lastInventoryEventVersion;
            } catch {
            }
            requestInventoryReload();
        });

        es.onerror = () => {
            // EventSource auto-reconnects; keep UI quiet.
        };
    } catch (e) {
        console.warn('EventSource not available', e);
    }
}

export async function loadInventory(options = {}) {
    const refresh = options.refresh === true;
    const requestId = ++state.currentRequestId;
    try {
        const params = new URLSearchParams({ type: state.currentFilter });
        if (state.currentTerminalId) {
            params.append('terminalId', state.currentTerminalId);
        }

        const data = await apiRequest(`/api/items?${params}`);
        if (requestId !== state.currentRequestId) return;

        if (state.currentSearch) {
            data.items = data.items.filter(item => {
                const name = item.name || item.id.split(':')[1] || item.id;
                return matchItem(name, state.currentSearch) || matchItem(item.id, state.currentSearch);
            });
            data.total = data.items.length;
        }

        if (refresh) {
            syncInventory(data);
            return;
        }

        renderInventory(data);
    } catch (e) {
        console.error('Failed to load inventory', e);
    }
}

export function resortInventory() {
    if (state.currentRequestId === 0) {
        return false;
    }
    syncInventory({ items: inventoryItems, total: inventoryItems.length });
    return true;
}

function renderEmptyInventory() {
    const grid = document.getElementById('inventory-grid');
    if (!grid) return;

    state.lastItemsHash = null;
    inventoryItems = [];
    inventorySlotById.clear();
    if (iconObserver) {
        iconObserver.disconnect();
        iconObserver = null;
    }

    grid.innerHTML = '<div style="grid-column:1/-1;display:flex;align-items:center;justify-content:center;height:200px;color:#64748b;font-size:0.9rem;">No items found</div>';
    document.getElementById('total-items').innerText = '0';
    document.getElementById('item-types').innerText = '0';
}

export function renderInventory(data) {
    const items = Array.isArray(data?.items) ? data.items : [];
    if (items.length === 0) {
        renderEmptyInventory();
        return;
    }

    state.lastItemsHash = null;
    syncInventory(data);
}

function syncInventory(data) {
    const grid = document.getElementById('inventory-grid');
    const scrollEl = document.querySelector('.inventory-scroll');
    if (!grid || !scrollEl) return;

    const items = Array.isArray(data?.items) ? data.items : [];
    if (items.length === 0) {
        renderEmptyInventory();
        return;
    }

    inventoryItems = sortInventoryItems(items, state.currentSort);

    if (inventorySlotById.size === 0) {
        grid.innerHTML = '';
    }

    const typeCount = Number.isFinite(data?.total) ? data.total : inventoryItems.length;
    const totalItems = inventoryItems.reduce((sum, item) => {
        const amount = item?.amount;
        return sum + (Number.isFinite(amount) ? amount : 0);
    }, 0);

    document.getElementById('total-items').innerText = formatNumber(totalItems);
    document.getElementById('item-types').innerText = formatNumber(typeCount);

    ensureIconObserver();

    const anchor = captureScrollAnchor(scrollEl, grid);

    const seen = new Set();
    const desiredSlots = [];
    for (const item of inventoryItems) {
        const id = item.uniqueId;
        if (!id) continue;

        seen.add(id);
        let slot = inventorySlotById.get(id);
        if (!slot) {
            slot = createItemSlot(item);
            inventorySlotById.set(id, slot);
        } else {
            updateItemSlot(slot, item);
        }
        desiredSlots.push(slot);
    }

    // Reorder with minimal DOM moves to avoid :hover flicker/transition restarts.
    // (Appending all nodes every refresh re-parents them and repeatedly restarts the hover scale animation.)
    let cursor = grid.firstElementChild;
    for (const slot of desiredSlots) {
        if (cursor === slot) {
            cursor = cursor.nextElementSibling;
            continue;
        }
        grid.insertBefore(slot, cursor);
    }

    for (const [id, el] of inventorySlotById.entries()) {
        if (seen.has(id)) continue;
        el.remove();
        inventorySlotById.delete(id);
    }

    observeLazyIcons(grid);
    restoreScrollAnchor(scrollEl, grid, anchor);
}

function captureScrollAnchor(scrollEl, grid) {
    try {
        const scrollRect = scrollEl.getBoundingClientRect();
        const x = scrollRect.left + Math.min(16, scrollRect.width / 2);
        const y = scrollRect.top + 16;
        const hit = document.elementFromPoint(x, y);
        const slot = hit?.closest?.('.item-slot');
        if (slot && grid.contains(slot)) {
            const id = slot.dataset.itemId;
            if (!id) return null;
            const rect = slot.getBoundingClientRect();
            return { id, topOffset: rect.top - scrollRect.top };
        }
    } catch {
    }
    return null;
}

function restoreScrollAnchor(scrollEl, grid, anchor) {
    if (!anchor?.id) return;

    try {
        const selector = `[data-item-id="${escapeCssAttr(anchor.id)}"]`;
        const el = grid.querySelector(selector);
        if (!el) return;

        const scrollRect = scrollEl.getBoundingClientRect();
        const rect = el.getBoundingClientRect();
        const newOffset = rect.top - scrollRect.top;
        const delta = newOffset - anchor.topOffset;
        if (Number.isFinite(delta) && Math.abs(delta) > 0.5) {
            scrollEl.scrollTop += delta;
        }
    } catch {
    }
}

function escapeCssAttr(value) {
    if (window.CSS && typeof window.CSS.escape === 'function') {
        return window.CSS.escape(value);
    }
    return String(value).replace(/["\\]/g, '\\$&');
}

function createItemSlot(item) {
    const slot = document.createElement('div');
    slot.className = `item-slot${item.type === 'fluid' ? ' fluid' : ''}${item.craftable ? ' craftable' : ''}`;
    slot.dataset.itemId = item.uniqueId;
    slot.dataset.iconId = item.iconKey;
    slot.dataset.itemType = item.type || 'item';

    if (item.craftable) {
        slot.dataset.craftId = item.id;
        slot.dataset.craftName = item.name || item.id;
        slot.dataset.craftType = item.type || 'item';
        slot.dataset.craftIconKey = item.iconKey;
    } else {
        slot.dataset.craftId = '';
        slot.dataset.craftName = '';
        slot.dataset.craftType = '';
        slot.dataset.craftIconKey = '';
    }

    slot.title = '';

    const img = document.createElement('img');
    img.className = 'item-icon';
    img.loading = 'lazy';

    const iconUrl = iconUrlFor(item.iconKey);
    img.dataset.iconKey = item.iconKey;
    img.dataset.iconSrc = iconUrl;
    if (loadedIcons.has(item.iconKey)) {
        img.src = iconUrl;
    } else {
        img.src = PLACEHOLDER_ICON;
        img.dataset.src = iconUrl;
    }

    img.onload = () => {
        const iconKey = img.dataset.iconKey;
        const desired = img.dataset.iconSrc;
        if (!iconKey || !desired) {
            return;
        }

        const desiredAbs = new URL(desired, window.location.href).href;
        const current = img.currentSrc || img.src || '';
        if (current !== desiredAbs) {
            return;
        }

        loadedIcons.add(iconKey);
        delete img.dataset.retryAttempts;
    };

    img.onerror = () => {
        img.src = PLACEHOLDER_ICON;
        scheduleIconRetry(img);
    };

    const name = document.createElement('span');
    name.className = 'item-name';
    name.innerText = item.name || item.id.split(':')[1] || item.id;

    const amount = document.createElement('span');
    amount.className = 'item-amount';
    amount.innerText = formatAmount(item.amount);

    slot.append(img, name, amount);
    return slot;
}

function updateItemSlot(slot, item) {
    slot.classList.toggle('fluid', item.type === 'fluid');
    slot.classList.toggle('craftable', item.craftable === true);
    slot.dataset.itemType = item.type || 'item';

    slot.title = '';

    if (item.craftable) {
        slot.dataset.craftId = item.id;
        slot.dataset.craftName = item.name || item.id;
        slot.dataset.craftType = item.type || 'item';
        slot.dataset.craftIconKey = item.iconKey;
    } else {
        slot.dataset.craftId = '';
        slot.dataset.craftName = '';
        slot.dataset.craftType = '';
        slot.dataset.craftIconKey = '';
    }

    const amountEl = slot.querySelector('.item-amount');
    if (amountEl) {
        amountEl.innerText = formatAmount(item.amount);
    }

    const nameEl = slot.querySelector('.item-name');
    if (nameEl) {
        nameEl.innerText = item.name || item.id.split(':')[1] || item.id;
    }
}

function bindInventoryTooltipHandlers(grid) {
    if (!grid || grid.dataset.tooltipBound === '1') return;
    grid.dataset.tooltipBound = '1';

    grid.addEventListener('mouseover', (e) => {
        const slot = e.target.closest('.item-slot');
        if (!slot || !grid.contains(slot)) return;

        const iconKey = slot.dataset.iconId;
        if (!iconKey) return;

        tooltipLastX = e.clientX;
        tooltipLastY = e.clientY;
        tooltipHasLastPos = true;

        tooltipHoverKey = iconKey;
        showTooltipLoading();
        ensureTooltipPositioner().onMouseMove(tooltipLastX, tooltipLastY);
        requestTooltip(iconKey).catch(() => {
            if (tooltipHoverKey !== iconKey) return;
            renderTooltip(['(tooltip not prerendered yet)']);
        });
    });

    grid.addEventListener('mousemove', (e) => {
        if (!tooltipVisible || !tooltipEl) return;
        tooltipLastX = e.clientX;
        tooltipLastY = e.clientY;
        tooltipHasLastPos = true;
        ensureTooltipPositioner().onMouseMove(tooltipLastX, tooltipLastY);
    });

    grid.addEventListener('mouseout', (e) => {
        const fromSlot = e.target.closest?.('.item-slot');
        const toSlot = e.relatedTarget?.closest?.('.item-slot');
        if (fromSlot && fromSlot === toSlot) return;
        tooltipHoverKey = null;
        tooltipHasLastPos = false;
        hideTooltip();
    });
}

async function requestTooltip(iconKey) {
    if (tooltipCache.has(iconKey)) {
        if (tooltipHoverKey === iconKey) {
            renderTooltip(tooltipCache.get(iconKey));
        }
        return;
    }

    const data = await apiRequest(`/api/prerender/tooltip?id=${encodeURIComponent(iconKey)}`);
    const tooltip = Array.isArray(data?.tooltip) ? data.tooltip.filter(Boolean) : [];
    tooltipCache.set(iconKey, tooltip);

    if (tooltipHoverKey === iconKey) {
        renderTooltip(tooltip);
    }
}

function ensureTooltipEl() {
    if (tooltipEl) return tooltipEl;
    tooltipEl = document.createElement('div');
    tooltipEl.id = 'ae2web-tooltip';
    tooltipEl.className = 'ae2web-tooltip';
    tooltipEl.style.display = 'none';
    document.body.appendChild(tooltipEl);
    return tooltipEl;
}

function ensureTooltipPositioner() {
    ensureTooltipEl();
    if (tooltipPositioner) return tooltipPositioner;

    tooltipPositioner = createTooltipPositioner({
        requestAnimationFrame: (callback) => window.requestAnimationFrame(callback),
        cancelAnimationFrame: (id) => window.cancelAnimationFrame(id),
        getTooltipSize: () => {
            const rect = tooltipEl.getBoundingClientRect();
            return { width: rect.width, height: rect.height };
        },
        getViewportSize: () => ({ width: window.innerWidth, height: window.innerHeight }),
        applyPosition: (left, top) => {
            tooltipEl.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        },
    });

    return tooltipPositioner;
}

function showTooltipLoading() {
    ensureTooltipEl();
    tooltipEl.innerHTML = '<div class="ae2web-tooltip-line">Loading...</div>';
    tooltipEl.style.display = 'block';
    tooltipVisible = true;
    ensureTooltipPositioner().invalidateSize();
}

function renderTooltip(lines) {
    ensureTooltipEl();
    if (!lines || lines.length === 0) {
        tooltipEl.innerHTML = '<div class="ae2web-tooltip-line">(no tooltip)</div>';
    } else {
        tooltipEl.innerHTML = lines.map(line => {
            return `<div class="ae2web-tooltip-line">${legacyTextToHtml(line)}</div>`;
        }).join('');
    }
    tooltipEl.style.display = 'block';
    tooltipVisible = true;
    const positioner = ensureTooltipPositioner();
    positioner.invalidateSize();
    if (tooltipHasLastPos) {
        positioner.onMouseMove(tooltipLastX, tooltipLastY);
    }
}

function hideTooltip() {
    if (!tooltipEl) return;
    tooltipEl.style.display = 'none';
    tooltipVisible = false;
    tooltipPositioner?.cancel();
}

function escapeHtml(str) {
    return String(str)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function legacyTextToHtml(text) {
    const colorMap = {
        '0': '#000000',
        '1': '#0000AA',
        '2': '#00AA00',
        '3': '#00AAAA',
        '4': '#AA0000',
        '5': '#AA00AA',
        '6': '#FFAA00',
        '7': '#AAAAAA',
        '8': '#555555',
        '9': '#5555FF',
        'a': '#55FF55',
        'b': '#55FFFF',
        'c': '#FF5555',
        'd': '#FF55FF',
        'e': '#FFFF55',
        'f': '#FFFFFF',
    };

    const out = [];
    let state = { color: null, bold: false, italic: false, underline: false, strike: false };
    let buf = '';

    function flush() {
        if (!buf) return;
        const safe = escapeHtml(buf);
        const styles = [];
        if (state.color) styles.push(`color:${state.color}`);
        if (state.bold) styles.push('font-weight:700');
        if (state.italic) styles.push('font-style:italic');
        const decorations = [];
        if (state.underline) decorations.push('underline');
        if (state.strike) decorations.push('line-through');
        if (decorations.length) styles.push(`text-decoration:${decorations.join(' ')}`);

        if (styles.length) {
            out.push(`<span style="${styles.join(';')}">${safe}</span>`);
        } else {
            out.push(safe);
        }
        buf = '';
    }

    const s = String(text ?? '');
    for (let i = 0; i < s.length; i++) {
        const ch = s[i];
        if (ch !== '§' || i === s.length - 1) {
            buf += ch;
            continue;
        }

        const code = (s[i + 1] || '').toLowerCase();
        i++;
        flush();

        if (code === 'r') {
            state = { color: null, bold: false, italic: false, underline: false, strike: false };
            continue;
        }

        if (code === 'l') { state.bold = true; continue; }
        if (code === 'o') { state.italic = true; continue; }
        if (code === 'n') { state.underline = true; continue; }
        if (code === 'm') { state.strike = true; continue; }
        if (code === 'k') { continue; } // obfuscated: ignore

        if (code === 'x') {
            // RGB format: §x§R§R§G§G§B§B
            let hex = '';
            for (let j = 0; j < 6 && i + 2 < s.length; j++) {
                if (s[i + 1] !== '§') break;
                const h = s[i + 2];
                hex += h;
                i += 2;
            }
            if (/^[0-9a-fA-F]{6}$/.test(hex)) {
                state = { color: `#${hex}`, bold: false, italic: false, underline: false, strike: false };
            }
            continue;
        }

        const color = colorMap[code];
        if (color) {
            state = { color, bold: false, italic: false, underline: false, strike: false };
        }
    }

    flush();
    return out.join('');
}

function ensureIconObserver() {
    if (iconObserver) return;

    iconObserver = new IntersectionObserver((entries, obs) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;

            const img = entry.target;
            if (!img || !img.dataset || !img.dataset.src) {
                obs.unobserve(img);
                return;
            }

            // Kick off icon load. Leave retry/onerror handling to the <img> handlers set in createItemSlot().
            img.src = img.dataset.iconSrc || img.dataset.src;
            img.removeAttribute('data-src');
            obs.unobserve(img);
        });
    });
}

function observeLazyIcons(rootEl) {
    ensureIconObserver();
    if (!rootEl || !iconObserver) return;

    rootEl.querySelectorAll('img[data-src]').forEach(img => {
        if (img.dataset.observed) return;
        img.dataset.observed = '1';
        iconObserver.observe(img);
    });
}
