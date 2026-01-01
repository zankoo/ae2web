import { state } from './state.js';
import { apiRequest } from './api.js';
import { escapeHtml } from './utils.js';
import { loadInventory, renderInventory, startInventoryEventStream, stopInventoryEventStream } from './inventory.js';
import { loadCPUs, renderCPUs } from './cpus.js';

export function bindTerminalSelectHandlers() {
    const select = document.getElementById('terminal-select');
    if (!select) return;
    select.addEventListener('change', switchTerminal);
}

function switchTerminal() {
    const select = document.getElementById('terminal-select');
    state.currentTerminalId = select.value;
    state.lastItemsHash = null;
    loadInventory();
    startInventoryEventStream();
}

export async function loadTerminals() {
    try {
        const data = await apiRequest('/api/terminals');
        const select = document.getElementById('terminal-select');
        if (!select) return;

        if (!data.terminals || data.terminals.length === 0) {
            select.innerHTML = '<option value="">No terminals</option>';
            state.currentTerminalId = '';
            renderInventory({ items: [], total: 0 });
            state.cpuData = [];
            renderCPUs();
            stopInventoryEventStream();
            return;
        }

        const connectedTerminals = data.terminals.filter(t => t.connected);
        if (connectedTerminals.length === 0) {
            select.innerHTML = '<option value="">No connected terminals</option>';
            state.currentTerminalId = '';
            renderInventory({ items: [], total: 0 });
            state.cpuData = [];
            renderCPUs();
            stopInventoryEventStream();
            return;
        }

        const previousId = state.currentTerminalId;
        select.innerHTML = connectedTerminals
            .map(t => `<option value="${escapeHtml(t.id)}">${escapeHtml(t.displayName)}</option>`)
            .join('');

        if (previousId && connectedTerminals.find(t => t.id === previousId)) {
            select.value = previousId;
            state.currentTerminalId = previousId;
        } else {
            state.currentTerminalId = select.value;
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

