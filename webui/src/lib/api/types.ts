export type ApiErrorResponse = {
	error?: string;
};

export type StatusResponse = {
	running: boolean;
	terminals: number;
	version: string;
};

export type TerminalInfo = {
	id: string;
	name?: string | null;
	displayName: string;
	position?: { x: number; y: number; z: number };
	dimension?: string;
	connected: boolean;
};

export type TerminalsResponse = {
	terminals: TerminalInfo[];
};

export type ItemType = 'item' | 'fluid';

export type InventoryItem = {
	type: ItemType;
	id: string;
	uniqueId: string;
	iconKey: string;
	name: string;
	translationKey: string;
	namespace: string;
	amount: number;
	craftable: boolean;
	tags?: string[];
};

export type InventoryResponse = {
	items: InventoryItem[];
	total: number;
};

export type InventoryCategoryRule = {
	name: string;
	tagRegex: string;
};

export type InventoryViewConfig = {
	whitelist: string[];
	blacklist: string[];
	categories: InventoryCategoryRule[];
	collapsed: Record<string, boolean>;
};

export type GenericStackDto = {
	id: string;
	iconKey: string;
	name: string;
	translationKey: string;
	amount: number;
	type?: 'item' | 'fluid';
};

export type CpuJobStatusDto = {
	crafting?: GenericStackDto;
	totalItems: number;
	progress: number;
	outputTotal?: number;
	outputDone?: number;
	outputRemaining?: number;
	elapsedTimeNanos: number;
};

export type CpuDto = {
	cpuId: string;
	busy: boolean;
	availableStorage: number;
	coProcessors: number;
	selectionMode: string;
	name: string | null;
	jobStatus?: CpuJobStatusDto;
};

export type CpusResponse = {
	cpus: CpuDto[];
	total: number;
};

export type CraftRequest = {
	id: string;
	type: ItemType;
	amount: number;
	cpuName?: string;
	terminalId?: string;
};

export type CraftOkResponse = {
	status: 'submitted';
};

export type CraftMissingResponse = {
	error: string;
	missing: GenericStackDto[];
};

export type TooltipResponse = {
	iconKey: string;
	tooltip: string[];
};

export type CpuProcessSummaryDto = {
	finalOutput?: GenericStackDto;
	outputTotal?: number;
	outputDone?: number;
	outputRemaining?: number;
	totalItems?: number;
	progress?: number;
	elapsedTimeNanos?: number;
};

export type CpuProcessEntryDto = {
	stack: GenericStackDto;
	stored: number;
	waiting: number;
	active: number;
};

export type CpuProcessPatternInputDto = {
	multiplier: number;
	choices: GenericStackDto[];
};

export type CpuProcessPatternDto = {
	patternId: string;
	remainingRuns: number;
	outputs: GenericStackDto[];
	inputs: CpuProcessPatternInputDto[];
};

export type CpuProcessResponse = {
	cpuId: string;
	busy: boolean;
	name: string | null;
	summary: CpuProcessSummaryDto;
	entries: CpuProcessEntryDto[];
	patterns: CpuProcessPatternDto[];
};

// Dashboard Widget Types

export type WidgetType = 'network-overview' | 'top-items' | 'tracked-items' | 'history-trend';

export type NetworkOverviewOptions = {
	showProduction: boolean;
	showConsumption: boolean;
	showNetChange: boolean;
};

export type TopItemsOptions = {
	mode: 'production' | 'consumption';
	count: number;
	filter: 'items' | 'fluids' | 'all';
};

export type TrackedItemsOptions = {
	trackedIds: string[];
};

export type HistoryTrendOptions = {
	itemId: string;
	timeRange: '1h' | '6h' | '24h' | '7d';
};

export type WidgetOptions =
	| NetworkOverviewOptions
	| TopItemsOptions
	| TrackedItemsOptions
	| HistoryTrendOptions;

export type WidgetConfig = {
	id: string;
	type: WidgetType;
	title?: string;
	w: number;
	h: number;
	x: number;
	y: number;
	options: WidgetOptions;
};

export type DashboardConfig = {
	widgets: WidgetConfig[];
};
