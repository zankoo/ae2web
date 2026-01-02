import { AUTH_TOKEN, PLACEHOLDER_ICON, state } from './state.js';
import { apiRequest } from './api.js';
import { showToast } from './utils.js';
import { loadCPUs } from './cpus.js';

export function bindCraftModalHandlers() {
    const overlay = document.getElementById('craft-modal');
    if (overlay) {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) closeModal();
        });
    }

    const cancelBtn = document.getElementById('craft-cancel-btn');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeModal);
    }

    const confirmBtn = document.getElementById('craft-confirm-btn');
    if (confirmBtn) {
        confirmBtn.addEventListener('click', submitCraft);
    }

    const quickAmounts = document.querySelector('.quick-amounts');
    if (quickAmounts) {
        quickAmounts.addEventListener('click', (e) => {
            const btn = e.target.closest('button.quick-amount-btn');
            if (!btn || !quickAmounts.contains(btn)) return;
            const amount = Number(btn.dataset.amount);
            if (!Number.isFinite(amount) || amount <= 0) return;
            setAmount(amount);
        });
    }

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') closeModal();
    });
}

export function openCraftModal(id, name, type = 'item', iconKey) {
    state.selectedItem = { id, name, type };

    document.getElementById('craft-item-name').innerText = name;
    document.getElementById('craft-item-id').innerText = id + (type === 'fluid' ? ' [Fluid]' : '');
    document.getElementById('craft-amount').value = 1;

    const iconContainer = document.getElementById('craft-icon-container');
    iconContainer.innerHTML = '';
    const img = new Image();
    img.src = AUTH_TOKEN
        ? `/api/prerender/image?id=${encodeURIComponent(iconKey)}&token=${encodeURIComponent(AUTH_TOKEN)}`
        : `/api/prerender/image?id=${encodeURIComponent(iconKey)}`;

    img.style.imageRendering = 'pixelated';
    img.onerror = () => { img.src = PLACEHOLDER_ICON; };
    iconContainer.appendChild(img);

    const cpuSelect = document.getElementById('craft-cpu-select');
    if (cpuSelect) {
        cpuSelect.textContent = '';
        const autoOption = document.createElement('option');
        autoOption.value = '';
        autoOption.textContent = 'Auto (Any Available)';
        cpuSelect.appendChild(autoOption);

        state.cpuData.forEach((cpu, i) => {
            const cpuName = cpu.name || `CPU ${i + 1}`;
            const status = cpu.busy ? ' (Busy)' : '';

            const option = document.createElement('option');
            option.value = cpu.name || '';
            option.textContent = `${cpuName}${status}`;
            cpuSelect.appendChild(option);
        });
    }

    const modal = document.getElementById('craft-modal');
    modal.classList.add('active');
    document.getElementById('craft-amount').focus();
}

export function closeModal() {
    document.getElementById('craft-modal').classList.remove('active');
    state.selectedItem = null;
}

function setAmount(val) {
    document.getElementById('craft-amount').value = val;
}

async function submitCraft() {
    if (!state.selectedItem) return;

    const amount = parseInt(document.getElementById('craft-amount').value);
    const cpuSelect = document.getElementById('craft-cpu-select');
    const cpuName = cpuSelect ? cpuSelect.value : '';

    try {
        const body = { id: state.selectedItem.id, type: state.selectedItem.type, amount };
        if (cpuName) {
            body.cpuName = cpuName;
        }
        if (state.currentTerminalId) {
            body.terminalId = state.currentTerminalId;
        }

        await apiRequest('/api/craft', {
            method: 'POST',
            body: JSON.stringify(body)
        });

        showToast(`Request sent: ${amount}x ${state.selectedItem.name}`, 'success');
        closeModal();
        loadCPUs();
    } catch (e) {
        showToast("Craft request failed", 'error');
    }
}
