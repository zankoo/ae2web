# AE2 Web Terminal — Deep Logic Review

Scope: full repo review (Java backend + Java client + web frontend). Focus is correctness, concurrency, resource lifecycle, API/UI contract, and subtle failure modes.

> Note (2026-01-02): The legacy vanilla web UI under `src/main/resources/web/` has been removed. Frontend code now lives in `webui/` (SvelteKit SPA). Any references below to `src/main/resources/web/*` are historical.

---

## Critical Issues (must fix)

### 1) Icon key scheme is inconsistent → icons won’t match, “missing” never converges
- File: `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:23`
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:133`
- File: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:86`
- Problem: There are multiple competing “canonical” icon keys:
  - Inventory uses `iconKey = id + "@" + abs(key.hashCode())` (NBT-aware).
  - Registry prefill uses `id + "@0"` for everything, regardless of actual AEKey hash.
  - Non-NBT render path uploads `itemId` with no `@hash` (saved as `@0` by server fallback).
- Impact:
  - Pre-rendered icons saved under `@0` won’t be found when UI requests `@abs(hashCode)`.
  - Full re-render (`submitBatch`) can populate files that are effectively unused by the UI and by `/api/prerender/missing` phase-2 keys.
  - This also breaks lock release semantics if any flows mix `itemId` vs `iconKey`.
- Suggested fix:
  - Define a single canonical `iconKey` function and use it everywhere (DTOs, `/api/prerender/missing`, render uploads, file paths).
  - Prefer a collision-resistant key derived from full AEKey serialization (e.g., hash of AEKey “tag”/NBT), not `hashCode()`.
  - If you keep `@0` for “no-NBT”, then DTO must also emit `@0` for truly-no-NBT keys; do not use `hashCode()` for that case.
- Code example (conceptual):
  - Backend: compute `iconKey` by serializing the key, then `sha256(bytes)` → `id + "@" + hexDigest`.
  - Client: upload using exactly that `iconKey`.
- Implemented:
  - Added `com.kokoda.ae2web.util.IconKeyUtils` and switched DTOs, `/api/prerender/missing`, and client uploads to use one canonical `iconKey` format.
  - Canonical hash is now `h1_<sha256(AEKey.writeKey bytes)>` (collision-resistant; no `Math.abs()` / `Integer.MIN_VALUE` edge case and no `hashCode()` collision reliance).

### 2) Registry “missing icon” phase generates keys that will never be requested by the UI
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:133`
- Problem: Phase 1 uses `itemId + "@0"` for registry entries, but `/api/items` returns `iconKey` based on `AEKey.hashCode()` (non-zero even without NBT in most implementations).
- Impact:
  - “Pre-render everything” doesn’t actually warm the cache for future ME inventory views.
  - Wastes client render time and server storage while still showing missing icons later.
- Suggested fix:
  - For registry entries, compute the same key the inventory will use (e.g., `AEItemKey.of(item).hashCode()` or the new canonical digest).
- Implemented:
  - Registry phase now computes `iconKey` via `AEItemKey.of(item)` / `AEFluidKey.of(fluid)` and the same canonical formatter used by `/api/items`.

### 3) Batched claim + parallel rendering can complete early and then restart mid-run
- File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:223`
- File: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:194`
- Problem:
  - `processBatchedClaims` intentionally overlaps claiming the next batch with rendering the current batch.
  - `RenderScheduler` declares completion as soon as the queue is empty.
  - If rendering is faster than the next claim round-trip, the queue can drain → “complete” fires → retry scheduling runs → later another batch arrives and restarts the scheduler (and can reset counters when `isActive` is false).
- Impact:
  - Multiple “render complete” events in a single join cycle.
  - Retry loop can run while more items are still legitimately pending claim/submit.
  - Progress counts become misleading; completion toast can be wrong.
- Suggested fix:
  - Either (a) claim all batches first, then submit one consolidated batch, or (b) add an explicit “producer finished” flag and only signal completion when both `queueEmpty && producerDone`.
  - Alternatively, throttle rendering start until at least N batches are queued, or chain: claim batch → enqueue → wait for queue to fully finish that batch → claim next.
- Implemented:
  - Auto-prerender now processes claim batches sequentially (no overlap of “claim next batch while rendering current”).
  - `RenderScheduler` completion now waits for all in-flight uploads (server save confirmed by upload response) instead of “queue empty == complete”.

### 4) ItemIconRenderer can leave the GL/render state corrupted on exceptions
- File: `src/main/java/com/kokoda/ae2web/client/render/ItemIconRenderer.java:173`
- File: `src/main/java/com/kokoda/ae2web/client/render/ItemIconRenderer.java:348`
- Problem: Catch blocks unbind the framebuffer but do not reliably restore projection matrix / lighting / blend/depth state if an exception happens after state changes.
- Impact:
  - Downstream rendering glitches or crashes in the client after one bad icon render.
- Suggested fix:
  - Use `try { … } finally { … }` to restore projection, lighting, depth/blend, and framebuffer binding in all paths.
- Implemented:
  - `ItemIconRenderer` now snapshots and restores depth/blend enable + funcs in `finally`, in addition to projection/lighting/framebuffer, to avoid leaking GL state across renders.

### 5) Unsafe-by-default server exposure + disk write endpoint
- File: `src/main/java/com/kokoda/ae2web/config/AE2WebConfig.java:31`
- File: `src/main/java/com/kokoda/ae2web/config/AE2WebConfig.java:35`
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:289`
- File: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:216`
- Problem:
  - Default bind address is `0.0.0.0` and auth is disabled.
  - `/api/prerender/upload` writes attacker-controlled data to disk (within a sanitized namespace).
- Impact:
  - Anyone on the reachable network can upload many files and fill disk (DoS).
  - CORS is `*` (`src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java:27`), which compounds exposure if a token is used.
- Suggested fix:
  - Default bind to `127.0.0.1` and/or enable auth by default.
  - Gate upload/claim endpoints behind auth even if read-only endpoints are open.
  - Consider rate limiting and per-IP quotas (or disable upload entirely unless prerender is explicitly enabled + authenticated).

### 6) Frontend uses inline `onclick` with HTML-escaping → breaks on normal item names, possible XSS surface
- File: `src/main/resources/web/app.js:222`
- File: `src/main/resources/web/app.js:225`
- Problem:
  - `escapeHtml()` is used to “escape” values injected into an inline JS string literal inside an HTML attribute.
  - HTML entity decoding happens before the inline JS is evaluated; names containing `'` (common in mods) can break the handler.
- Impact:
  - Clicking craftable items can stop working depending on item names.
  - If any server-provided string becomes attacker-controlled, this pattern is brittle for XSS prevention.
- Suggested fix:
  - Remove inline JS handlers; attach `addEventListener` in JS and pass data via `data-*` attributes.
  - If inline handlers are kept, you must JS-escape for JS context, not HTML-escape.
- Implemented:
  - Removed all inline event handler attributes from `index.html` and bound UI events via `addEventListener` in `app.js`.
  - Craft modal is opened via delegated click handlers using `data-*` attributes (no string interpolation into inline JS).

---

## Major Issues (should fix)

### 1) `/api/cpus` and `/api/craft` ignore terminal selection
- File: `src/main/java/com/kokoda/ae2web/web/handlers/CraftingHandler.java:57`
- File: `src/main/resources/web/app.js:482`
- Problem: Frontend sends `terminalId` for CPUs, but backend always uses `getConnectedTerminal()` (first connected terminal), ignoring `terminalId`.
- Impact:
  - CPU status and craft submission can target the wrong ME network when multiple terminals exist.
- Suggested fix:
  - Add `terminalId` support to `/api/cpus` and `/api/craft` (query param and/or JSON body) and use `getConnectedTerminal(terminalId)`.

### 2) `/api/items` pagination exists, but UI ignores it → incomplete inventory + broken search correctness
- File: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:26`
- File: `src/main/resources/web/app.js:162`
- Problem:
  - Server defaults `pageSize=10000`; UI calls `/api/items` with no `page/pageSize` and filters client-side.
- Impact:
  - Networks with >10,000 stacks silently truncate in UI.
  - Client-side search only searches the returned subset, so results are incomplete/wrong.
  - UI refresh every 3s can cause large JSON payload + server tick cost.
- Suggested fix:
  - Implement real pagination/infinite scroll in UI, and move search/filter server-side (or fetch all pages for search).
  - If pinyin is required client-side, fetch server-side pages and apply client-side pinyin match after server-side coarse filtering.
- Implemented:
  - UI no longer does a full `/api/items` refresh every 3 seconds; inventory refresh is now explicit (startup/terminal change/search/filter/manual refresh).
  - UI renders the full snapshot via client-side infinite scroll (chunked DOM append) so large inventories don’t require a massive single `innerHTML` rebuild.
  - Backend pagination parameters remain supported for future use, but the UI does not depend on `page/pageSize`.
  - Real-time refresh no longer relies on fixed polling: UI subscribes to `/api/events/inventory` (SSE) and refreshes inventory only when the server reports a change.

### 3) `uniqueId` is unstable (depends on page/index) → incremental update optimization never converges
- File: `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:21`
- File: `src/main/java/com/kokoda/ae2web/web/dto/FluidStackDTO.java:21`
- File: `src/main/resources/web/app.js:204`
- Problem: `uniqueId` includes `#index`, which changes whenever sorting/pagination changes.
- Impact:
  - Frontend’s incremental update path (`lastItemsHash`) frequently triggers full rebuilds.
  - Animations and DOM identity become unstable.
- Suggested fix:
  - Make `uniqueId` stable for a given AEKey (e.g., `iconKey` if collision-safe), and handle true collisions with a deterministic disambiguator (e.g., include serialized NBT digest).

### 4) `Math.abs(hashCode())` is not safe for `Integer.MIN_VALUE` and is collision-prone
- File: `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:23`
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:57`
- Problem:
  - `Math.abs(Integer.MIN_VALUE)` is still negative.
  - `hashCode()` collisions can map different NBT variants to the same iconKey.
- Impact:
  - Wrong icons displayed (data corruption at the UX layer).
  - Hard-to-reproduce mismatches and “flickering” icons when collisions occur.
- Suggested fix:
  - Replace with a robust digest of actual key serialization (preferred).
  - If you must keep int hashes, use unsigned formatting and accept negatives (don’t `abs()`), and still expect collisions.

### 5) `InventoryHandler` assumes query param lists are non-empty
- File: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:33`
- Problem: `params.get("search").get(0)` and `params.get("type").get(0)` can throw if the parameter exists but is empty.
- Impact:
  - 500 responses for malformed requests.
- Suggested fix:
  - Use `getFirstParam()` consistently and default safely.
- Implemented:
  - `InventoryHandler` now uses `getFirstParam()` for `search`/`type` and lowercases with `Locale.ROOT`.

### 6) Potential overflow in inventory global index calculation
- File: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:88`
- Problem: `int startIndex = page * pageSize;` can overflow for large `page`.
- Impact:
  - Negative indices and unstable `uniqueId` generation if used.
- Suggested fix:
  - Use `long startIndex` and clamp/validate.

### 7) Netty body size limit likely conflicts with claim batching assumptions
- File: `src/main/java/com/kokoda/ae2web/web/WebServer.java:150`
- File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:31`
- Problem:
  - `HttpObjectAggregator(65536)` limits request payload size to 64 KiB.
  - `CLAIM_BATCH_SIZE = 2000` can easily exceed this depending on iconKey lengths.
- Impact:
  - Claim requests can fail with HTTP 413, causing prerender to stall or churn retries.
- Suggested fix:
  - Either raise aggregator limit for prerender endpoints, or dynamically size batches based on estimated JSON payload size (bytes), not item count.

### 8) File I/O occurs on the server tick thread in several endpoints
- File: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java:355`
- File: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:184`
- Problem: `/api/prerender/image` reads files on the server thread, and `initialize()` can scan directories on demand.
- Impact:
  - Server tick lag spikes under icon load.
- Suggested fix:
  - Move disk I/O to a dedicated executor and marshal the response back to Netty.
  - Pre-initialize storage during server start.
- Implemented:
  - `/api/prerender/image` and `/api/prerender/upload` now run disk I/O on `WebServer`’s dedicated IO executor and marshal responses back onto the Netty event loop.
  - `PrerenderStorageService.initialize()` is synchronized and is invoked during `ServerStartingEvent` to avoid on-demand directory scans during gameplay ticks.

### 9) HTTP static file serving can leak InputStream on exceptions
- File: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java:131`
- Problem: `InputStream` is closed manually; if `readAllBytes()` throws, the stream is not closed.
- Impact:
  - Minor resource leak (classloader stream) under exceptional conditions.
- Suggested fix:
  - Use try-with-resources.
- Implemented:
  - `ApiHandler.serveStaticFile()` now uses try-with-resources.

### 10) `HandlerUtils.parseIntParam` can throw on empty param list
- File: `src/main/java/com/kokoda/ae2web/web/handlers/HandlerUtils.java:74`
- Problem: Accesses `params.get(name).get(0)` after `containsKey` without checking list emptiness.
- Impact:
  - 500 errors on malformed queries.
- Suggested fix:
  - Use `getFirstParam()` and validate.
- Implemented:
  - `parseIntParam()` now uses `getFirstParam()` and rejects blank values with a clean 400 path (callers already catch `IllegalArgumentException`).

### 11) Crafting endpoint doesn’t understand fluids, but UI can open craft modal for craftable fluids
- File: `src/main/resources/web/app.js:222`
- File: `src/main/java/com/kokoda/ae2web/web/handlers/CraftingHandler.java:176`
- Problem: UI treats any `craftable` as craftable, including fluids; backend only parses items via `BuiltInRegistries.ITEM`.
- Impact:
  - Confusing UX: craft modal opens but submit fails.
- Suggested fix:
  - Either prevent craft modal for fluids or extend `/api/craft` to support AEKeys (items + fluids) explicitly.
- Implemented:
  - `/api/craft` now requires `{ type: "item"|"fluid", id, amount, cpuName?, terminalId? }` and supports crafting with `AEFluidKey` as well as `AEItemKey`.
  - Frontend no longer blocks fluid crafting and sends `type` + `id` (no legacy fields).

### 12) Thread pool lifecycle: crafting executor is never shut down
- File: `src/main/java/com/kokoda/ae2web/web/handlers/CraftingHandler.java:31`
- Problem: Static `CRAFTING_EXECUTOR` is never terminated on server stop.
- Impact:
  - Thread leak on integrated-server restarts within the same JVM; harder shutdown behavior.
- Suggested fix:
  - Hook into `ServerStoppingEvent` to `shutdownNow()` + await termination, or use a shared server-managed executor.

### 13) Client-side prerender HTTP executor is never shut down on logout
- File: `src/main/java/com/kokoda/ae2web/client/network/PrerenderHttpClient.java:36`
- Problem: `PrerenderHttpClient` has its own thread pool and provides `shutdown()`, but nothing calls it on disconnect.
- Impact:
  - Background threads persist across world/server switches.
- Suggested fix:
  - Call `PrerenderHttpClient.getInstance().shutdown()` in `PrerenderTrigger.onPlayerLogout`.

### 14) RenderScheduler cross-thread publication risks (listener + counters)
- File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:218`
- File: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:40`
- File: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:301`
- Problem:
  - `startAutoRender()` can be invoked from `CompletableFuture` callbacks (not guaranteed to be the main client/render thread).
  - `RenderScheduler.progressListener` and `totalTasks` are plain fields; `setProgressListener()` is not synchronized/volatile.
- Impact:
  - Rare “lost” completion/progress callbacks or inconsistent progress numbers under contention.
- Suggested fix:
  - Ensure scheduler mutation happens on the main client thread (`Minecraft.getInstance().execute(…)`), and/or make shared fields `volatile`/atomic or always mutate/read them under the same lock.
- Implemented:
  - `RenderScheduler.totalTasks` and `RenderScheduler.progressListener` are now `volatile`, and completion/progress callbacks are already marshaled back to the main client thread via `Minecraft.getInstance().execute(...)`.

### 15) `/api/status` always reports `running=true`
- File: `src/main/java/com/kokoda/ae2web/web/handlers/StatusHandler.java:28`
- Problem: Doesn’t reflect actual WebServer state.
- Impact:
  - Misleading monitoring/debugging.
- Suggested fix:
  - Use `WebServer.getInstance().isRunning()`.

---

## Minor Issues (nice to fix)

### 1) `PrerenderStorageService.initialize()` is not synchronized
- File: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:66`
- Problem: Multiple threads can concurrently run initialization work.
- Impact:
  - Duplicate scans/loads; small performance hit; potential log noise.
- Suggested fix:
  - Synchronize initialization or use `AtomicBoolean.compareAndSet`.
- Implemented:
  - `PrerenderStorageService.initialize()` is now synchronized and pre-initialized during server start.

### 2) `sanitizePath` rejects valid `ResourceLocation` paths containing `/`
- File: `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java:364`
- Problem: Some mods legitimately use `namespace:path/with/slash`. Current logic rejects those.
- Impact:
  - Icons for those IDs can never be stored/served.
- Suggested fix:
  - Allow `/` but treat it as a directory separator safely (resolve path segments, validate normalized path stays under root, and make scanning recursive).
- Implemented:
  - `PrerenderStorageService` now supports `/` inside the `path` portion via `sanitizePathWithSlashes(...)`, and `scanExistingIcons()` scans namespace directories recursively (`Files.walk(...)`).

### 3) `loadedIcons` cache key is inconsistent with fallback behavior
- File: `src/main/resources/web/app.js:221`
- File: `src/main/resources/web/app.js:312`
- Problem: Cache checks `loadedIcons.has(item.iconKey)` but the image URL falls back to `item.id`. On successful loads without `iconKey`, nothing is cached.
- Impact:
  - Avoidable re-fetches and flicker after rebuilds.
- Suggested fix:
  - Cache by the actual request key used (e.g., `displayIconKey = item.iconKey || item.id`).
- Implemented:
  - UI now uses `iconKey` as the only request key (global key scheme); `loadedIcons` caching and image requests are consistent.

### 4) `loadInventory()` silently ignores errors
- File: `src/main/resources/web/app.js:184`
- Problem: Catch block is empty.
- Impact:
  - Debugging becomes difficult; UI can silently stall.
- Suggested fix:
  - Show a small non-intrusive error state or log to console consistently.
- Implemented:
  - `loadInventory()` now logs errors to `console.error(...)`.

### 5) Locale-sensitive lowercasing
- File: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:210`
- Problem: `toLowerCase()` without `Locale.ROOT`.
- Impact:
  - Edge-case search mismatches in certain locales (Turkish “I”).
- Suggested fix:
  - Use `toLowerCase(Locale.ROOT)` for both search and candidate strings.
- Implemented:
  - `InventoryHandler` search comparisons now use `Locale.ROOT` consistently.

### 6) Netty shutdown doesn’t await termination
- File: `src/main/java/com/kokoda/ae2web/web/WebServer.java:109`
- Problem: Calls `shutdownGracefully()` but doesn’t wait.
- Impact:
  - Non-deterministic shutdown timing; resources can linger.
- Suggested fix:
  - `shutdownGracefully().syncUninterruptibly()` (or at least add a timeout and log).
- Implemented:
  - `WebServer.stop()` now closes the server channel and awaits boss/worker group shutdown with timeouts (and avoids deadlocking if called from a Netty event loop thread).

### 7) Terminal pickup loses identity/name
- File: `src/main/java/com/kokoda/ae2web/block/WebTerminalBlock.java:97`
- Problem: Shift+wrench drops a fresh block ItemStack without preserving `terminalId`/`customName`.
- Impact:
  - Moving terminals breaks terminal identity and any external references.
- Suggested fix:
  - Store BE data in ItemStack NBT on pickup and restore on placement.
- Implemented:
  - Wrench pickup now writes `terminalId` + `customName` into the dropped stack’s `BlockEntityTag` so placement restores the same identity/name.

---

## Notes on the “PrerenderTrigger retry logic” fix
- File: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:264`
- The per-`iconKey` retry cap and the “serverUrl capture + equality checks” are good guardrails.
- The remaining correctness risk is the batch/queue completion race described above (“complete early”), plus the fixed `fetchMissingIcons(..., 100)` cap during retry (`src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:299`) which can leave more than 100 missing items unconsidered.
- Also note `shouldRetry()` increments attempts on *detection* (`src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:265`), not on a confirmed render/upload attempt; items locked by others can burn retry budget without ever being attempted by this client.

Update:
- Retry check now fetches up to 2000 missing icons per cycle (matches claim batch size), reducing the risk of leaving large missing sets unconsidered.
