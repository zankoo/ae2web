# AE2 Web Terminal - Task List

## Pending

## In Progress

## Completed

- [x] REFACTOR-1: Remove CDN fallback from app.js - prerender is the only icon source (564 → 357 lines)
- [x] REFACTOR-2: Delete ItemIconService.java - replaced by client-side prerendering
- [x] REFACTOR-3: Remove /api/item-icon endpoint - no longer needed
- [x] REFACTOR-4: Extract GtceuMaterialColor to separate service class
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
