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
};

export type InventoryResponse = {
	items: InventoryItem[];
	total: number;
};

export type GenericStackDto = {
	id: string;
	iconKey: string;
	name: string;
	translationKey: string;
	amount: number;
};

export type CpuJobStatusDto = {
	crafting?: GenericStackDto;
	totalItems: number;
	progress: number;
	elapsedTimeNanos: number;
};

export type CpuDto = {
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

