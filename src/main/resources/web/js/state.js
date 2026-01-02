export const API_BASE = '';
export const AUTH_TOKEN = new URLSearchParams(window.location.search).get('token') || '';

export const PLACEHOLDER_ICON =
    'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PHJlY3Qgd2lkdGg9IjMyIiBoZWlnaHQ9IjMyIiBmaWxsPSIjMGQxMTE3Ii8+PHRleHQgeT0iMjAiIHg9IjE2IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjNjQ3NDhiIiBmb250LWZhbWlseT0ibW9ub3NwYWNlIj4/PC90ZXh0Pjwvc3ZnPg==';

export const state = {
    currentSearch: '',
    currentFilter: 'all',
    currentSort: 'amount',
    currentTerminalId: '',
    selectedItem: null,

    currentRequestId: 0,
    lastItemsHash: null,

    cpuData: [],
    lastCpuIds: null,

    inventoryEventSource: null,
    inventoryReloadTimer: null,
    lastInventoryEventVersion: null,
};
