# AE2 Web Terminal - Task List

## Pending

- [ ] TASTE-13: Simplify translation key detection in app.js translateItemName() - current heuristic (dots + lowercase) is fragile, consider backend flag

## In Progress

## Completed

- [x] FIX-1: Update CDN URLs to match mod target (1.20.1) - lang JSON uses 1.20.1; item icon CDN uses 1.20.2 as closest available dataset
- [x] PERF-2: Avoid double request for missing icons - app.js uses image onerror + server error TTL, no extra fetch to detect 404
- [x] BUG-2: Fix translation loading race condition - loadMinecraftTranslations() returns null while loading, store promise instead
- [x] BUG-1: Fix icon cache "stuck" on placeholder after transient failures - add cache expiry or retry mechanism in app.js
- [x] PERF-1: Defer materialColor computation until after pagination - currently computed for all items before pagination in ApiHandler.filterItems()
- [x] TASTE-12: Cache getMaterialRGB reflection method in GtceuMaterialColor - currently only getMaterial is cached, rgbMethod is looked up on every call
- [x] TASTE-11: Fix DTO post-mutation in ApiHandler.filterItems() - pass materialColor to ItemStackDTO constructor instead of setting field after construction
- [x] MED-2: Remove debug logging from TranslationService and ItemIconService - change info level back to debug after fixes confirmed working
- [x] MED-1: Add frontend CDN fallback for Minecraft item name translations - fetch language.json in app.js and translate translation keys client-side
- [x] HIGH-4: Implement GTCEu materialColor icon fallback in web UI - use colored placeholder when server icon missing
- [x] HIGH-3: Add GTCEu base texture templates to web resources - copy dust.png, ingot.png, gem.png etc. from GTCEu JAR to src/main/resources/web/textures/gtceu/ (skipped: using colored placeholder instead)
- [x] HIGH-2: Add materialColor field to ItemStackDTO - extract RGB color for GTCEu materials using item NBT or registry lookup, return as hex string (e.g., "FFE650")
- [x] HIGH-1: Add frontend CDN fallback for Minecraft item icons - when /api/icon returns 404, fetch from unpkg CDN (https://unpkg.com/minecraft-assets@1.15.0/minecraft-assets/data/1.20.2/items/{path}.png) in app.js
- [x] TASTE-10: Extract translateTranslatable() and formatArg() helper methods from TranslationService.translateComponent() to reduce nesting
- [x] TASTE-9: Add ResourceLocator.assetExists() method and refactor ItemIconService.tryTexture() to use it instead of opening stream just to check existence
- [x] TASTE-8: Delete redundant minecraft namespace branch in TranslationService.openLangStream() - both branches do the same thing
- [x] HIGH-1: Fix AE2 cable connection - WebTerminalBlockEntity implements IInWorldGridNodeHost and exposes getGridNode(Direction)
- [x] HIGH-2: Register IN_WORLD_GRID_NODE_HOST capability type - register IInWorldGridNodeHost via RegisterCapabilitiesEvent
- [x] HIGH-3: Update grid node creation - in initializeGridNode() add .setInWorldNode(true) to the GridHelper.createManagedNode() chain
- [x] HIGH-1: Change debug log to info level in getNetworkInventory() in WebTerminalBlockEntity.java - change LOGGER.debug to LOGGER.info for inventory fetch logging
- [x] MED-1: Update version string in ApiHandler.java handleStatus() from hardcoded "1.0.0" to read from mod metadata
- [x] TASTE-1: Delete dead code - removed ItemStackDTO.craftable field, deleted CraftRequestDTO.java, cleaned up web UI
- [x] TASTE-2: Fix hardcoded URL/port in WebTerminalBlock - now uses AE2WebConfig.getHttpPort(), ChatFormatting
- [x] TASTE-3: Extract terminal check helper in ApiHandler - created getConnectedTerminal() method
- [x] TASTE-4: Refactor WebServer from static to singleton pattern - converted static fields/methods to singleton instance
- [x] TASTE-5: Split handleItems() responsibilities - extracted filterItems(), sortByAmount(), paginate(), matchesSearch()
- [x] TASTE-6: Reduce nesting in getNetworkInventory() - refactored to stream-based inventory collection
- [x] TASTE-7: Extract message building in WebTerminalBlock.use() - created sendTerminalStatus() helper
- [x] HIGH-1: Fix thread safety - wrap Netty handler world/AE2 access in server thread execution
- [x] MED-1: Fix memory leak - unregister terminal in onChunkUnloaded
- [x] MED-2: Register config - call AE2WebConfig.register() in mod constructor
- [x] MED-3: Add input validation for page/pageSize params with bounds checking
- [x] MED-4: Fix information leakage - return generic error messages to clients
- [x] MED-5: Add null check in getActionSource()
- [x] MED-6: Fix blocking bind().sync() - run in separate thread
- [x] MED-7: Optimize inventory query - add caching
- [x] MED-8: Implement /api/craft endpoint or return 501 Not Implemented
- [x] MED-9: Implement /api/cpus endpoint or return 501 Not Implemented
- [x] HIGH-4: Implement /api/craft endpoint - beginCraftingCalculation + submitJob with missing-items reporting
- [x] MED-10: Implement /api/cpus endpoint - return crafting CPU status
- [x] HIGH-1: Fix getNetworkInventory() returning empty - change from getCachedInventory() to getInventory().getAvailableStacks() in WebTerminalBlockEntity.java
- [x] HIGH-2: Add debug logging in getNetworkInventory() to log inventory size before returning

## Blocked
