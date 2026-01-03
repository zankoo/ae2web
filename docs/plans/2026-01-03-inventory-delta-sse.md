# Inventory Delta SSE Design

**Goal:** Make inventory updates truly event-driven: when AE2 inventory/craftables change, the server pushes small incremental deltas to the browser via SSE (instead of forcing `/api/items` full reloads).

## SSE Protocol

Endpoint: `GET /api/events/inventory?terminalId=<id>`

Events:

### `hello`

Sent once after subscribing.

```json
{ "terminalId": "fc6b30bd", "version": 123 }
```

### `snapshot`

Sent after `hello` to establish the initial inventory state.

```json
{
  "terminalId": "fc6b30bd",
  "version": 123,
  "items": [ /* same shape as /api/items */ ],
  "total": 2048
}
```

### `delta`

Sent whenever the inventory/craftable status changes. Batches changes and increments version by 1 per published batch.

```json
{
  "terminalId": "fc6b30bd",
  "version": 124,
  "added": [ /* full InventoryItem objects */ ],
  "updated": [ { "uniqueId": "item:...", "amount": 123, "craftable": true, "name": "..." } ],
  "removed": [ "fluid:..." ]
}
```

Client rules:
- Maintain `inventoryVersion` (last applied version).
- On `snapshot`: replace local state and set version.
- On `delta`: apply if `delta.version === inventoryVersion + 1`; otherwise request a new `snapshot` (or full refresh via `/api/items`).

## Backend Data Sources

- Storage amounts: AE2 `IStorageWatcherNode#onStackChange(AEKey, long)` (watch-all enabled only when there are SSE subscribers).
- Craftable changes: AE2 `ICraftingWatcherNode#onCraftableChange(AEKey)`; current craftable status resolved via `ICraftingService#isCraftable(AEKey)`.

## Coalescing

Changes are buffered per terminal and flushed as one `delta` at most every ~200ms to avoid event storms and UI churn.

## Translation Refresh (Prerender)

When a client uploads a newly prerendered icon and its translated name, the server publishes a `delta` update for any
connected terminals that currently contain that `uniqueId`. This allows the web UI to update display names without a
manual page refresh.
