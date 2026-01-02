# Inventory Infinite Scroll (No 3s Full Refresh) Implementation Plan

> Note (2026-01-02): The legacy vanilla web UI under `src/main/resources/web/` has been replaced by the SvelteKit SPA in `webui/`. This plan targets the legacy frontend and should be reworked against `webui/` before reuse.

> **For Codex:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** Remove the 3-second full `/api/items` refresh and implement a true infinite-scroll UI that can still display the full inventory snapshot (data volume is controllable), without introducing server-side `page` parameters into the UI contract.

**Non-goals (for this change):**
- Add WebSocket/SSE push updates.
- Add server-side pagination/search indexing for pinyin.
- Add new backend endpoints (unless we hit an unavoidable blocker).

**Constraints / context**
- Frontend must keep pinyin search (`PinyinMatch`) working → requires access to the full item list for latin-input searches.
- Project not published → breaking internal changes are acceptable, but prefer keeping `/api/items` backward compatible.
- Verification available: `./gradlew build --offline`.

---

## Step 1: Add infinite-scroll state + observers (frontend)

Files:
- `src/main/resources/web/app.js`

Changes:
- Introduce inventory rendering state:
  - `inventoryItems` (current list after filters/search)
  - `inventoryRenderedCount`
  - `inventoryListObserver` + `inventorySentinelEl`
- Add helper functions:
  - `resetInventoryView(items, total)`
  - `appendInventoryChunk()`
  - `ensureInventorySentinel(grid)`
  - `ensureInventoryListObserver(scrollEl)`
  - Refactor icon lazy-loading so it can observe newly appended images without recreating the observer.

Verification:
- Manual sanity: load page, inventory renders initial chunk; scrolling loads more items.

---

## Step 2: Switch rendering pipeline to infinite scroll + remove “full rebuild” dependency

Files:
- `src/main/resources/web/app.js`

Changes:
- Update `renderInventory(data)` to:
  - Stop doing `grid.innerHTML = data.items.map(...)` for the entire list.
  - Use `resetInventoryView(...)` + chunk appends instead.
- Keep existing pinyin client-side filtering logic, but ensure it applies before `resetInventoryView`.
- Ensure stats (`total-items`, `item-types`) show total filtered count (even before all chunks are rendered).

Verification:
- Manual sanity:
  - Searching updates list and resets to top (renders first chunk of results).
  - Terminal switch resets view and loads from the new terminal.

---

## Step 3: Remove 3-second full refresh + keep explicit refresh action

Files:
- `src/main/resources/web/app.js`
- `src/main/resources/web/style.css` (optional: add `.inventory-sentinel` styling)
- `docs/DEEP-LOGIC-REVIEW.md` (update Major #2 status to reflect new architecture)

Changes:
- In `initTerminal()`:
  - Remove `setInterval(refreshData, 3000)`.
  - Keep `checkConnection()` and other lightweight timers (terminals/CPUs) as-is.
- Ensure the existing “Quick Actions → Refresh” still performs an explicit refresh (`refreshData()`).
- Add a small CSS rule for sentinel:
  - `.inventory-sentinel { grid-column: 1 / -1; height: 1px; }`

Verification:
- Run: `./gradlew build --offline`
- Expected: `BUILD SUCCESSFUL`
