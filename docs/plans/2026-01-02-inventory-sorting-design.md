# Inventory Sorting Design

**Goal:** Make the existing inventory sort toggle work, allowing users to sort the inventory grid by **Amount** or **Name** without reloading the page.

## Approach

- Implement sorting **client-side** (no backend changes) because the full inventory snapshot is already loaded in the browser and the UI toggle is purely a view concern.
- Track sort mode in `state.currentSort` (default: `amount` to match the default active button in the UI).
- Apply sorting at the single source of truth for rendering order: `syncInventory()` in `web/js/inventory.js`, so all code paths (initial load, SSE refresh, terminal switch, manual resort) behave consistently.

## Sorting Rules

- `amount`: sort by `amount` **descending**, then by display name **ascending**.
- `name`: sort by display name **ascending**, then by `amount` **descending**.
- Always use a deterministic tie-breaker (`uniqueId`/`id`) to keep ordering stable across refreshes.
- Strip Minecraft legacy formatting codes (`§x`) from names when comparing.

## UX + Performance

- Clicking the sort toggle updates active state within `.sort-toggle` only (does not interfere with the existing filter toggle).
- Resort locally via DOM reordering logic already used by incremental refresh; avoid network refetch for sorting.

## Testing

- Add a Node unit test (`node --test`) covering the comparator behavior for both sort modes.

## Non-goals

- Sort direction toggle (asc/desc), persistence across reloads, or server-side sorting.

