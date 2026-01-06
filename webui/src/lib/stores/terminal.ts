import { writable, derived, get } from 'svelte/store';
import { fetchStatus } from '$lib/api/status';
import { fetchTerminals } from '$lib/api/terminals';
import type { TerminalInfo } from '$lib/api/types';
import { setAuthToken } from './session';
import { showToast } from './toast';

// Core stores
export const terminals = writable<TerminalInfo[]>([]);
export const terminalId = writable<string>('');
export const online = writable<boolean>(false);

// Derived stores
export const terminalDisplayName = derived([terminals, terminalId], ([$terminals, $terminalId]) => {
	if (!$terminalId) {
		if ($terminals.length === 0) return 'No terminals';
		const connected = $terminals.filter((t) => t.connected);
		if (connected.length === 0) return 'No connected terminals';
		return 'Loading...';
	}
	const terminal = $terminals.find((t) => t.id === $terminalId);
	return terminal?.displayName ?? 'Unknown';
});

export const connectedTerminals = derived(terminals, ($terminals) =>
	$terminals.filter((t) => t.connected)
);

// Dropdown state (UI-specific, but shared across pages)
export const terminalDropdownOpen = writable<boolean>(false);

// Polling state
let statusTimer: ReturnType<typeof setInterval> | null = null;
let terminalsTimer: ReturnType<typeof setInterval> | null = null;

/**
 * Select a terminal by its info object.
 */
export function selectTerminal(terminal: TerminalInfo): void {
	terminalId.set(terminal.id);
	terminalDropdownOpen.set(false);
}

/**
 * Toggle the terminal dropdown.
 */
export function toggleTerminalDropdown(): void {
	terminalDropdownOpen.update((v) => !v);
}

/**
 * Close the terminal dropdown.
 */
export function closeTerminalDropdown(): void {
	terminalDropdownOpen.set(false);
}

/**
 * Check if the server is reachable and validate the token.
 */
export async function checkConnection(): Promise<void> {
	try {
		await fetchStatus();
		online.set(true);
	} catch (e) {
		online.set(false);
		const message = e instanceof Error ? e.message : '';
		if (message.toLowerCase().includes('unauthorized') || message.includes('HTTP 401')) {
			showToast('Invalid token', 'error');
			setAuthToken('');
		}
	}
}

/**
 * Refresh the terminals list and update selection if needed.
 * Returns the previous terminalId if it changed, null otherwise.
 */
export async function refreshTerminals(): Promise<string | null> {
	try {
		const data = await fetchTerminals();
		const terminalList = Array.isArray(data.terminals) ? data.terminals : [];
		terminals.set(terminalList);

		const connected = terminalList.filter((t) => t.connected);
		const currentId = get(terminalId);

		if (connected.length === 0) {
			if (currentId) {
				terminalId.set('');
				return currentId;
			}
			return null;
		}

		const stillConnected = currentId && connected.some((t) => t.id === currentId);
		if (!stillConnected) {
			const previous = currentId;
			terminalId.set(connected[0].id);
			return previous || null;
		}

		return null;
	} catch (e) {
		console.error('Failed to load terminals', e);
		return null;
	}
}

/**
 * Start polling for connection status and terminals.
 */
export function startPolling(): void {
	if (statusTimer !== null) return;

	// Initial load
	void checkConnection();
	void refreshTerminals();

	// Set up intervals
	statusTimer = setInterval(() => void checkConnection(), 5000);
	terminalsTimer = setInterval(() => void refreshTerminals(), 10000);
}

/**
 * Stop polling.
 */
export function stopPolling(): void {
	if (statusTimer !== null) {
		clearInterval(statusTimer);
		statusTimer = null;
	}
	if (terminalsTimer !== null) {
		clearInterval(terminalsTimer);
		terminalsTimer = null;
	}
}

/**
 * Reset all terminal state (e.g., on logout).
 */
export function resetTerminalState(): void {
	stopPolling();
	terminals.set([]);
	terminalId.set('');
	online.set(false);
	terminalDropdownOpen.set(false);
}
