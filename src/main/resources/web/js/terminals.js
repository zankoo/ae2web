import { state } from './state.js';
import { apiRequest } from './api.js';
import { escapeHtml } from './utils.js';
import { loadInventory, renderInventory, startInventoryEventStream, stopInventoryEventStream } from './inventory.js';
import { loadCPUs, renderCPUs } from './cpus.js';


export function bindTerminalSelectHandlers() {
    const trigger = document.getElementById('terminal-select-trigger');
    const dropdown = document.getElementById('terminal-dropdown-list');
    const container = document.getElementById('terminal-select-container');

    if (!trigger || !dropdown) return;

    trigger.addEventListener('click', (e) => {
        e.stopPropagation();
        dropdown.classList.toggle('hidden');
        container.classList.toggle('open');
    });

    document.addEventListener('click', (e) => {
        if (!container.contains(e.target)) {
            dropdown.classList.add('hidden');
            container.classList.remove('open');
        }
    });
}

function selectTerminal(id, name) {
    state.currentTerminalId = id;
    state.lastItemsHash = null; // Force refresh

    // Update UI
    const textEl = document.getElementById('terminal-selected-text');
    if (textEl) textEl.textContent = name;

    const dropdown = document.getElementById('terminal-dropdown-list');
    const container = document.getElementById('terminal-select-container');
    if (dropdown) dropdown.classList.add('hidden');
    if (container) container.classList.remove('open');

    // Update active class
    document.querySelectorAll('.ae2-select-option').forEach(opt => {
        if (opt.dataset.value === id) opt.classList.add('selected');
        else opt.classList.remove('selected');
    });

    loadInventory();
    startInventoryEventStream();
}

export async function loadTerminals() {
    try {
        const data = await apiRequest('/api/terminals');
        const dropdown = document.getElementById('terminal-dropdown-list');
        const textEl = document.getElementById('terminal-selected-text');

        if (!dropdown || !textEl) return;

        if (!data.terminals || data.terminals.length === 0) {
            textEl.textContent = 'No terminals';
            dropdown.innerHTML = '<div class="ae2-select-option">No terminals found</div>';
            state.currentTerminalId = '';
            renderInventory({ items: [], total: 0 });
            state.cpuData = [];
            renderCPUs();
            stopInventoryEventStream();
            return;
        }

        const connectedTerminals = data.terminals.filter(t => t.connected);
        if (connectedTerminals.length === 0) {
            textEl.textContent = 'No connected terminals';
            dropdown.innerHTML = '<div class="ae2-select-option">No connected terminals</div>';
            state.currentTerminalId = '';
            renderInventory({ items: [], total: 0 });
            state.cpuData = [];
            renderCPUs();
            stopInventoryEventStream();
            return;
        }

        const previousId = state.currentTerminalId;

        // Render options
        dropdown.innerHTML = connectedTerminals.map(t => {
            const isSelected = t.id === previousId || (!previousId && t === connectedTerminals[0]);
            return `<div class="ae2-select-option ${isSelected ? 'selected' : ''}" data-value="${escapeHtml(t.id)}" data-name="${escapeHtml(t.displayName)}">${escapeHtml(t.displayName)}</div>`;
        }).join('');

        // Bind clicks for new options
        dropdown.querySelectorAll('.ae2-select-option').forEach(opt => {
            opt.addEventListener('click', (e) => {
                const id = e.target.dataset.value;
                const name = e.target.dataset.name;
                if (id) selectTerminal(id, name);
            });
        });

        // Determine effective current terminal
        let effectiveTerminal = null;
        if (previousId && connectedTerminals.find(t => t.id === previousId)) {
            effectiveTerminal = connectedTerminals.find(t => t.id === previousId);
        } else {
            effectiveTerminal = connectedTerminals[0];
        }

        if (effectiveTerminal) {
            textEl.textContent = effectiveTerminal.displayName;
            state.currentTerminalId = effectiveTerminal.id;
        }

        if (state.currentTerminalId !== previousId) {
            state.lastItemsHash = null;
            loadInventory();
            loadCPUs();
            startInventoryEventStream();
        }
    } catch (e) {
        console.error('Failed to load terminals', e);
    }
}

