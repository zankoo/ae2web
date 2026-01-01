import { AUTH_TOKEN, state, PLACEHOLDER_ICON } from './state.js';
import { apiRequest } from './api.js';
import { escapeHtml, formatAmount, showToast } from './utils.js';

export async function loadCPUs() {
    try {
        const params = new URLSearchParams();
        if (state.currentTerminalId) {
            params.append('terminalId', state.currentTerminalId);
        }
        const data = await apiRequest(`/api/cpus?${params}`);
        state.cpuData = data.cpus;
        renderCPUs();
    } catch (e) {
        console.error('Failed to load CPUs', e);
    }
}

export function bindCpuClickHandlers() {
    const container = document.getElementById('cpu-list');
    if (!container) return;

    container.addEventListener('click', (e) => {
        const card = e.target.closest('.cpu-card');
        if (!card || !container.contains(card)) return;

        const index = Number(card.dataset.cpuIndex);
        if (!Number.isFinite(index)) return;
        showCPUDetail(index);
    });
}

export function renderCPUs() {
    const container = document.getElementById('cpu-list');
    const countEl = document.getElementById('cpu-count');
    if (!container || !countEl) return;

    if (!state.cpuData || state.cpuData.length === 0) {
        container.innerHTML = `
            <div class="cpu-empty">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="4 2"/>
                    <path d="M9 9h6M9 12h6M9 15h3"/>
                </svg>
                <span>No CPUs detected</span>
            </div>`;
        countEl.textContent = '0';
        state.lastCpuIds = null;
        return;
    }

    countEl.textContent = String(state.cpuData.length);

    const currentCpuIds = state.cpuData.map((cpu, i) => cpu.name || `cpu-${i}`).join(',');
    const needsFullRebuild = state.lastCpuIds !== currentCpuIds;
    state.lastCpuIds = currentCpuIds;

    if (needsFullRebuild) {
        container.innerHTML = state.cpuData.map((cpu, index) => buildCpuCardHtml(cpu, index)).join('');
    } else {
        state.cpuData.forEach((cpu, index) => {
            const card = container.children[index];
            if (!card || !card.classList.contains('cpu-card')) return;

            const isBusy = cpu.busy;
            card.classList.toggle('busy', isBusy);
            card.classList.toggle('idle', !isBusy);

            const statusEl = card.querySelector('.cpu-status');
            if (statusEl) statusEl.textContent = isBusy ? 'BUSY' : 'IDLE';

            const existingJob = card.querySelector('.cpu-job');
            if (isBusy && cpu.jobStatus) {
                const job = cpu.jobStatus;
                const progress = job.totalItems > 0 ? Math.round((job.progress / job.totalItems) * 100) : 0;
                const itemName = job.crafting?.name || 'Unknown';
                const itemId = job.crafting?.id || '';
                const iconKey = job.crafting?.iconKey || '';

                if (existingJob) {
                    const nameEl = existingJob.querySelector('.cpu-job-name');
                    if (nameEl) nameEl.textContent = itemName;
                    const progressBar = existingJob.querySelector('.cpu-progress-bar');
                    if (progressBar) progressBar.style.width = `${progress}%`;
                    const progressText = existingJob.querySelector('.cpu-progress-text');
                    if (progressText) progressText.textContent = `${job.progress}/${job.totalItems}`;
                } else {
                    const statsEl = card.querySelector('.cpu-stats');
                    if (statsEl) {
                        statsEl.insertAdjacentHTML('beforebegin', buildJobHtml(job, itemName, itemId, iconKey));
                    }
                }
            } else if (existingJob) {
                existingJob.remove();
            }

            const storageEl = card.querySelector('.cpu-stat-value');
            if (storageEl) storageEl.textContent = formatAmount(cpu.availableStorage);
            const coProcEl = card.querySelectorAll('.cpu-stat-value')[1];
            if (coProcEl) coProcEl.textContent = String(cpu.coProcessors);
        });
    }

    bindCpuJobIconErrorHandlers(container);
}

function bindCpuJobIconErrorHandlers(container) {
    container.querySelectorAll('img.cpu-job-icon').forEach(img => {
        if (img.dataset.boundErrorHandler) return;
        img.dataset.boundErrorHandler = '1';
        img.addEventListener('error', () => { img.style.display = 'none'; }, { once: true });
    });
}

function buildJobHtml(job, itemName, itemId, iconKey) {
    const progress = job.totalItems > 0 ? Math.round((job.progress / job.totalItems) * 100) : 0;
    const iconUrl = iconKey
        ? (AUTH_TOKEN
            ? `/api/prerender/image?id=${encodeURIComponent(iconKey)}&token=${encodeURIComponent(AUTH_TOKEN)}`
            : `/api/prerender/image?id=${encodeURIComponent(iconKey)}`)
        : PLACEHOLDER_ICON;
    return `
        <div class="cpu-job">
            <div class="cpu-job-icon-wrapper">
                <img class="cpu-job-icon" src="${iconUrl}">
            </div>
            <div class="cpu-job-details">
                <div class="cpu-job-name">${escapeHtml(itemName)}</div>
                <div class="cpu-progress-wrapper">
                    <div class="cpu-progress">
                        <div class="cpu-progress-bar" style="width: ${progress}%"></div>
                    </div>
                    <span class="cpu-progress-text">${job.progress}/${job.totalItems}</span>
                </div>
            </div>
        </div>`;
}

function buildCpuCardHtml(cpu, index) {
    const name = cpu.name || `CPU ${index + 1}`;
    const status = cpu.busy ? 'busy' : 'idle';
    const statusText = cpu.busy ? 'BUSY' : 'IDLE';
    const storage = formatAmount(cpu.availableStorage);
    const coProcs = cpu.coProcessors;

    let jobHtml = '';
    if (cpu.busy && cpu.jobStatus) {
        const job = cpu.jobStatus;
        const itemName = job.crafting?.name || 'Unknown';
        const itemId = job.crafting?.id || '';
        const iconKey = job.crafting?.iconKey || '';
        jobHtml = buildJobHtml(job, itemName, itemId, iconKey);
    }

    return `
        <div class="cpu-card ${status}" data-cpu-index="${index}">
            <div class="cpu-header">
                <div class="cpu-identity">
                    <div class="cpu-led"></div>
                    <span class="cpu-name">${escapeHtml(name)}</span>
                </div>
                <span class="cpu-status">${statusText}</span>
            </div>
            ${jobHtml}
            <div class="cpu-stats">
                <div class="cpu-stat">
                    <span class="cpu-stat-label">Storage</span>
                    <span class="cpu-stat-value">${storage}</span>
                </div>
                <div class="cpu-stat">
                    <span class="cpu-stat-label">Co-Proc</span>
                    <span class="cpu-stat-value">${coProcs}</span>
                </div>
            </div>
        </div>`;
}

function showCPUDetail(index) {
    const cpu = state.cpuData[index];
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
