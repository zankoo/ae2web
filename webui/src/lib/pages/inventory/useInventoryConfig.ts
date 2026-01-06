import debounce from 'lodash-es/debounce';

import type { InventoryViewConfig, TerminalInfo } from '$lib/api/types';
import type { CategoryDraft } from '$lib/components/modals/InventorySettingsModal.svelte';

type InventoryViewStateAccess = {
	get config(): InventoryViewConfig;
	set config(value: InventoryViewConfig);

	get terminalId(): string | null;
	set terminalId(value: string | null);
};

type InventorySettingsStateAccess = {
	get open(): boolean;
	set open(value: boolean);

	get terminalId(): string | null;
	set terminalId(value: string | null);

	get terminalName(): string | null;
	set terminalName(value: string | null);

	get whitelistText(): string;
	set whitelistText(value: string);

	get blacklistText(): string;
	set blacklistText(value: string);

	get categories(): CategoryDraft[];
	set categories(value: CategoryDraft[]);

	get saving(): boolean;
	set saving(value: boolean);

	get error(): string | null;
	set error(value: string | null);
};

export type InventoryConfigController = {
	loadForTerminal(nextTerminalId: string): Promise<void>;
	toggleSectionCollapsed(key: string): void;
	openSettingsModalForTerminal(terminal: TerminalInfo): Promise<void>;
	closeSettingsModal(): void;
	saveSettings(whitelistText: string, blacklistText: string, categories: CategoryDraft[]): Promise<void>;
	destroy(): void;
};

const DEFAULT_CONFIG: InventoryViewConfig = { whitelist: [], blacklist: [], categories: [], collapsed: {} };

function normalizeInventoryViewConfig(raw: InventoryViewConfig | null | undefined): InventoryViewConfig {
	const whitelist = Array.isArray(raw?.whitelist)
		? raw.whitelist.filter((x) => typeof x === 'string' && x.trim())
		: [];
	const blacklist = Array.isArray(raw?.blacklist)
		? raw.blacklist.filter((x) => typeof x === 'string' && x.trim())
		: [];
	const categories = Array.isArray(raw?.categories)
		? raw.categories
				.filter(
					(x) =>
						x &&
						typeof x.name === 'string' &&
						x.name.trim() &&
						typeof x.tagRegex === 'string' &&
						x.tagRegex.trim()
				)
				.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
		: [];
	const collapsed =
		raw?.collapsed && typeof raw.collapsed === 'object'
			? { ...(raw.collapsed as Record<string, boolean>) }
			: {};
	return { whitelist, blacklist, categories, collapsed };
}

function isUnauthorizedError(e: unknown): boolean {
	const message = e instanceof Error ? e.message : String(e ?? '');
	return message.toLowerCase().includes('unauthorized') || message.includes('HTTP 401');
}

function parseRegexLines(text: string): string[] {
	return String(text)
		.split('\n')
		.map((x) => x.trim())
		.filter(Boolean);
}

function validateRegex(pattern: string): string | null {
	try {
		new RegExp(pattern, 'i');
		return null;
	} catch {
		return `Invalid regex: ${pattern}`;
	}
}

export function createInventoryConfigController({
	view,
	settings,
	getCurrentTerminalId,
	fetchInventoryViewConfig,
	saveInventoryViewConfig,
	showToast,
	setAuthToken
}: {
	view: InventoryViewStateAccess;
	settings: InventorySettingsStateAccess;
	getCurrentTerminalId(): string | null | undefined;
	fetchInventoryViewConfig(terminalId: string): Promise<InventoryViewConfig>;
	saveInventoryViewConfig(terminalId: string, cfg: InventoryViewConfig): Promise<InventoryViewConfig>;
	showToast(message: string, kind: 'success' | 'error'): void;
	setAuthToken(token: string): void;
}): InventoryConfigController {
	let categoryIdSeq = 0;

	function createCategoryDraft(rule?: { name?: string; tagRegex?: string }): CategoryDraft {
		categoryIdSeq += 1;
		return {
			id: `cat_${categoryIdSeq}`,
			name: typeof rule?.name === 'string' ? rule.name : '',
			tagRegex: typeof rule?.tagRegex === 'string' ? rule.tagRegex : ''
		};
	}

	const saveDebounced = debounce((targetTerminalId: string, cfg: InventoryViewConfig) => {
		void (async () => {
			if (!targetTerminalId) return;
			try {
				const saved = await saveInventoryViewConfig(targetTerminalId, cfg);
				if (getCurrentTerminalId() === targetTerminalId) {
					view.config = normalizeInventoryViewConfig(saved);
					view.terminalId = targetTerminalId;
				}
			} catch (e) {
				if (isUnauthorizedError(e)) {
					showToast('Invalid token', 'error');
					setAuthToken('');
				}
			}
		})();
	}, 600);

	async function loadForTerminal(nextTerminalId: string): Promise<void> {
		if (!nextTerminalId) {
			view.config = DEFAULT_CONFIG;
			view.terminalId = null;
			return;
		}
		if (view.terminalId === nextTerminalId) return;

		try {
			const cfg = await fetchInventoryViewConfig(nextTerminalId);
			view.config = normalizeInventoryViewConfig(cfg);
			view.terminalId = nextTerminalId;
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			view.config = DEFAULT_CONFIG;
			view.terminalId = nextTerminalId;
		}
	}

	function toggleSectionCollapsed(key: string): void {
		if (!key) return;

		const current = view.config.collapsed ?? {};
		const next = { ...current, [key]: current[key] !== true };
		view.config = { ...view.config, collapsed: next };

		const terminalId = getCurrentTerminalId();
		if (terminalId) {
			view.terminalId = terminalId;
			saveDebounced(terminalId, view.config);
		}
	}

	async function openSettingsModalForTerminal(terminal: TerminalInfo): Promise<void> {
		if (!terminal?.id) return;

		settings.open = true;
		settings.terminalId = terminal.id;
		settings.terminalName = terminal.displayName;
		settings.saving = false;
		settings.error = null;

		try {
			const cfg =
				terminal.id === view.terminalId ? view.config : await fetchInventoryViewConfig(terminal.id);
			const normalized = normalizeInventoryViewConfig(cfg);
			settings.whitelistText = normalized.whitelist.join('\n');
			settings.blacklistText = normalized.blacklist.join('\n');
			settings.categories = normalized.categories.map((x) => createCategoryDraft(x));
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			settings.whitelistText = '';
			settings.blacklistText = '';
			settings.categories = [];
			settings.error = e instanceof Error ? e.message : 'Failed to load settings';
		}
	}

	function closeSettingsModal(): void {
		settings.open = false;
		settings.terminalId = null;
		settings.terminalName = null;
		settings.saving = false;
		settings.error = null;
	}

	async function saveSettings(
		whitelistText: string,
		blacklistText: string,
		categories: CategoryDraft[]
	): Promise<void> {
		if (!settings.terminalId) return;

		settings.saving = true;
		settings.error = null;

		const whitelist = parseRegexLines(whitelistText);
		const blacklist = parseRegexLines(blacklistText);
		for (const p of [...whitelist, ...blacklist]) {
			const err = validateRegex(p);
			if (err) {
				settings.error = err;
				settings.saving = false;
				return;
			}
		}

		const cats = categories
			.map((x) => ({ name: x.name.trim(), tagRegex: x.tagRegex.trim() }))
			.filter((x) => x.name && x.tagRegex);
		for (const c of cats) {
			const err = validateRegex(c.tagRegex);
			if (err) {
				settings.error = err;
				settings.saving = false;
				return;
			}
		}

		const nextCfg: InventoryViewConfig = {
			whitelist,
			blacklist,
			categories: cats,
			collapsed: view.terminalId === settings.terminalId ? view.config.collapsed : {}
		};

		try {
			const saved = await saveInventoryViewConfig(settings.terminalId, nextCfg);
			if (getCurrentTerminalId() === settings.terminalId) {
				view.config = normalizeInventoryViewConfig(saved);
				view.terminalId = settings.terminalId;
			}
			closeSettingsModal();
		} catch (e) {
			if (isUnauthorizedError(e)) {
				showToast('Invalid token', 'error');
				setAuthToken('');
				return;
			}
			settings.error = e instanceof Error ? e.message : 'Failed to save settings';
		} finally {
			settings.saving = false;
		}
	}

	function destroy(): void {
		saveDebounced.cancel();
	}

	return {
		loadForTerminal,
		toggleSectionCollapsed,
		openSettingsModalForTerminal,
		closeSettingsModal,
		saveSettings,
		destroy
	};
}

