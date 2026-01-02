# AE2 Web Terminal - Task List

## Pending

## In Progress

## Completed

- [x] MINOR-8: Add inventory sorting (amount/name)
- [x] FIX-23: Auto refresh missing icons when prerender completes (retry continues)
- [x] FIX-22: Smooth inventory tooltip follow (rAF throttle + cached size)
- [x] TASTE-16: Split CraftingHandler/PrerenderHandler into dedicated sub-handlers (reduce file size and responsibilities)
- [x] TASTE-15: Split frontend JS into `web/js/*` modules and load via `type="module"`
- [x] TASTE-14: Don’t swallow `InterruptedException` in prerender HTTP retries (restore interrupt + fail fast)
- [x] TASTE-13: Serve all `web/` static assets generically (binary-safe; no hardcoded filenames)
- [x] MINOR-7: Preserve terminalId/customName on wrench pickup (store BlockEntityTag)
- [x] MINOR-6: Await Netty shutdown termination in WebServer.stop()
- [x] MINOR-2: Allow '/' in prerender icon paths (sanitizePathWithSlashes + recursive scan)
- [x] HIGH-10: Add real-time inventory refresh via SSE (EventSource) instead of fixed polling
- [x] HIGH-9: Implement inventory infinite scroll and remove 3s full refresh loop (explicit refresh + chunked DOM render)
- [x] HIGH-8: Move prerender upload/image disk I/O off server tick thread (use WebServer IO executor + preinitialize storage)
- [x] HIGH-7: Make prerender completion mean server-saved icons (wait for upload completion, avoid early complete)
- [x] MED-3: Ensure client icon rendering restores GL state on exceptions (try/finally)
- [x] HIGH-6: Fix prerender claim/render completion race (avoid early-complete then restart)
- [x] HIGH-5: Respect `terminalId` for `/api/cpus` and `/api/craft` (backend + frontend)
- [x] MED-2: Make `uniqueId` stable (no page/index dependency); base it on an immutable key
- [x] HIGH-4: Shut down long-lived executors on stop/logout (CraftingHandler, PrerenderHttpClient)
- [x] HIGH-3: Fix `/api/status` running flag to reflect actual WebServer state
- [x] HIGH-2: Avoid HTTP 413 by increasing Netty HttpObjectAggregator limit and/or sizing prerender claim batches
- [x] HIGH-1: Replace inline onclick handlers with addEventListener in web UI (inventory + CPUs)
- [x] FIX-19: Add retry ceiling + per-iconKey failure tracking + use ScheduledExecutorService instead of new Thread in PrerenderTrigger:227-310 - Priority 2, Complexity MED
- [x] FIX-20: Make translations NBT-aware - store translated names under iconKey (namespace:path@hash) in PrerenderStorageService - Priority 3, Complexity MED
- [x] FIX-21: Improve render failure visibility - change debug to warn level in RenderScheduler:211-212 - Priority 4, Complexity LOW
- [x] FIX-18: Fix frontend cache key to use iconKey consistently (app.js:220-226, 312-317) - Priority 1, Complexity LOW
- [x] TASK-3-INVESTIGATION: 调查翻译显示问题 - 结论：API和前端代码正确，可能是浏览器缓存或静默错误
- [x] Phase 1: 修改 ItemStackDTO/FluidStackDTO 添加 iconKey 字段
- [x] Phase 1: 修改 PrerenderStorageService 支持 @nbtHash 格式
- [x] Phase 2: 修改 PrerenderHandler 从 ME 网络获取物品（带NBT）
- [x] Phase 3: 修改 PrerenderHttpClient 解析新的 missing icons 响应格式
- [x] Phase 3: 修改 PrerenderTrigger 传递 NBT 数据
- [x] Phase 4: 修改 RenderScheduler 接受 NBT 数据并应用到 ItemStack
- [x] Phase 4: 修改 ItemIconRenderer 应用 NBT（已在 RenderScheduler 中完成）
- [x] Phase 5: 修改 app.js 使用 iconKey 字段渲染图标

- [x] UI-4: Display translated item names directly on slots (not just on hover)
- [x] UI-3: Remove pagination, use scroll-based inventory view (load all items at once)
- [x] FIX-16: Add uniqueId field to distinguish items with different NBT (enchanted vs normal)
- [x] FIX-17: Cache loaded icons for instant display on page navigation
- [x] FEAT-4: Add craftable indicator on items/fluids (⚒ icon) and show zero-stock craftables

- [x] FIX-15: Add fluid support to prerender API (missing icons, stats, full render)
- [x] FEAT-3: Add fluid icon prerendering support (client-side texture rendering)
- [x] FEAT-2: Add fluid display support with filter toggle (items/fluids/all)
- [x] UI-2: Redesign frontend to fullscreen dark industrial terminal (960x540 fixed → 100vh responsive)
- [x] REFACTOR-10: Split ApiHandler (933→150 lines) into StatusHandler, InventoryHandler, CraftingHandler, PrerenderHandler
- [x] REFACTOR-9: Create HandlerUtils.java with shared utility methods
- [x] REFACTOR-8: Refactor executeCraftRequest using CompletableFuture chain (reduced 5 levels of nesting)
- [x] REFACTOR-7: Simplify WebTerminalBlockEntity - remove redundant null checks (trust isConnectedToGrid())
- [x] FIX-14: Remove unused iconCache variable from app.js
- [x] FIX-13: Fix IntersectionObserver lifecycle (single global observer with disconnect)
- [x] FIX-12: Add request deduplication to prevent race conditions in frontend
- [x] FIX-11: Implement incremental DOM updates (only update amounts when items unchanged)
- [x] FIX-10: Add Authorization header support with URL parameter (?token=xxx)
- [x] FIX-9: Add retry logic for 5xx and network errors in PrerenderHttpClient
- [x] FIX-8: Fix RenderScheduler submitBatch() progress counter reset bug
- [x] FIX-7: Remove stale style.css route from ApiHandler
- [x] FIX-6: Add Cache-Control no-cache header for 404 responses in handlePrerenderImage
- [x] FIX-5: Fix IPv6 address parsing in PrerenderTrigger
- [x] FIX-4: Fix server URL comparison in PrerenderTrigger callbacks (handle reconnect to different server)
- [x] FIX-3: Prevent negative missingCount in stats (handle stale cache entries)
- [x] FIX-2: Add periodic expired lock cleanup (every 30 seconds instead of every claim)
- [x] FIX-1: Fix non-atomic claim/lock acquisition using ConcurrentHashMap.compute()
- [x] UI-1: Complete frontend redesign with Sci-Fi terminal aesthetic (Orbitron + JetBrains Mono fonts, CRT scanlines, cyan glow effects)
- [x] REFACTOR-6: Remove GTCEu materialColor fallback - prerender handles all icons now
- [x] REFACTOR-5: Remove server-side TranslationService - translations now come from client prerendering
- [x] REFACTOR-1: Remove CDN fallback from app.js - prerender is the only icon source (564 → 357 lines)
- [x] REFACTOR-2: Delete ItemIconService.java - replaced by client-side prerendering
- [x] REFACTOR-3: Remove /api/item-icon endpoint - no longer needed
- [x] SEC-1: Fix thread safety in PrerenderStorageService - use synchronized blocks instead of read/write locks with LRU LinkedHashMap
- [x] SEC-2: Fix path traversal vulnerability in PrerenderStorageService - reject ".." and add path normalization check
- [x] SEC-3: Fix HTTP connection leaks in PrerenderHttpClient - use try-finally with disconnect()
- [x] SEC-4: Fix NativeImage resource leak in ItemIconRenderer - use try-with-resources
- [x] SEC-5: Fix thread scheduling in RenderScheduler - remove unnecessary mc.execute() since tick runs on render thread
- [x] SEC-6: Fix race condition with serverUrl in PrerenderTrigger - capture serverUrl at start of async ops, add volatile
- [x] SEC-7: Move prerender disk I/O off Netty thread - wrap upload/image handlers with runOnServerThread()
- [x] FEAT-1: Implement client-side prerender architecture - auto-render item icons on player join, upload to server
- [x] HIGH-4: Implement GTCEu materialColor icon fallback in web UI - use colored placeholder when server icon missing
- [x] HIGH-2: Add materialColor field to ItemStackDTO - extract RGB color for GTCEu materials
- [x] TASTE-12: Cache getMaterialRGB reflection method in GtceuMaterialColor
- [x] TASTE-11: Fix DTO post-mutation in ApiHandler.filterItems()
- [x] TASTE-10: Extract translateTranslatable() and formatArg() helper methods
- [x] TASTE-1: Delete dead code
- [x] TASTE-2: Fix hardcoded URL/port in WebTerminalBlock
- [x] TASTE-3: Extract terminal check helper in ApiHandler
- [x] TASTE-4: Refactor WebServer from static to singleton pattern
- [x] TASTE-5: Split handleItems() responsibilities
- [x] TASTE-6: Reduce nesting in getNetworkInventory()
- [x] TASTE-7: Extract message building in WebTerminalBlock.use()
- [x] HIGH-1: Fix thread safety - wrap Netty handler world/AE2 access in server thread execution
- [x] HIGH-1: Fix AE2 cable connection - WebTerminalBlockEntity implements IInWorldGridNodeHost
- [x] HIGH-4: Implement /api/craft endpoint - beginCraftingCalculation + submitJob
- [x] HIGH-1: Fix getNetworkInventory() returning empty

## Blocked
