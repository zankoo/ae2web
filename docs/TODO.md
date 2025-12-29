# AE2 Web Terminal - Task List

## Pending

## In Progress

## Completed

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
- [x] HIGH-1: Fix getNetworkInventory() returning empty - change from getCachedInventory() to getInventory().getAvailableStacks() in WebTerminalBlockEntity.java
- [x] HIGH-2: Add debug logging in getNetworkInventory() to log inventory size before returning

## Blocked
