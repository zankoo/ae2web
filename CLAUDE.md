# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AE2 Web Terminal is a Minecraft Forge mod (1.20.1) that adds a Web Terminal block connecting to Applied Energistics 2 ME networks. It exposes network inventory through an embedded HTTP server, allowing players to browse items, search, and submit crafting requests from any web browser.

## Build Commands

```bash
./gradlew build              # Build the mod JAR (output: build/libs/ae2web-1.0.0.jar)
./gradlew runClient          # Run Minecraft client with mod loaded
./gradlew runServer          # Run Minecraft server with mod loaded
./gradlew runGameTestServer  # Run game tests
./gradlew runData            # Run data generators
```

## Architecture

**Main Entry Point:** `src/main/java/com/kokoda/ae2web/AE2WebMod.java` - Registers blocks/items/block entities and manages WebServer lifecycle on server start/stop events.

**Core Components:**

- `block/WebTerminalBlock.java` - Placeable block that creates the block entity
- `blockentity/WebTerminalBlockEntity.java` - Manages AE2 ME network connection via `IGridNodeListener`, provides `getNetworkInventory()` and `getCraftingService()`
- `web/WebServer.java` - Netty HTTP server (default port 8080), maintains thread-safe registry of connected terminals
- `web/handlers/ApiHandler.java` - HTTP request router serving static files and API endpoints
- `web/dto/` - Data transfer objects for JSON serialization (ItemStackDTO)
- `registry/` - Forge deferred registries for blocks, items, block entities
- `config/AE2WebConfig.java` - Server config (httpPort, enableAuth, authToken)

**API Endpoints:**
- `GET /api/status` - Server status
- `GET /api/terminals` - Connected terminals list
- `GET /api/items` - Paginated items with search
- `POST /api/craft` - Crafting requests (TODO: not fully implemented)
- `GET /api/cpus` - Crafting CPU status (TODO: not fully implemented)

**Static Web Resources:** `src/main/resources/web/` contains index.html, app.js, style.css

## Key Dependencies

- Minecraft Forge 47.2.0 (MC 1.20.1)
- Applied Energistics 2 15.2.1
- Java 17
- Gson 2.10.1
- Netty (bundled with Minecraft)

## Known TODOs in Codebase

- Crafting request implementation in ApiHandler.java (currently returns 501)
- CPU status implementation in ApiHandler.java (currently returns 501)

## Version Control & Semantic Versioning

**Current Version:** 1.0.0 (established at first commit)

版本号遵循语义化版本规范 (SemVer):
- **MAJOR (X.0.0)**: 不兼容的 API 变更
- **MINOR (0.X.0)**: 向后兼容的新功能
- **PATCH (0.0.X)**: 向后兼容的 bug 修复

版本号位置:
- `build.gradle` 中的 `version` 字段
- `src/main/resources/META-INF/mods.toml` 中的 `version` 字段

**Commit 规范:**
- 在合适的时机提交代码（功能完成、bug 修复、重构完成等）
- 根据变更类型更新版本号
- Commit message 应清晰描述变更内容
- 使用 Co-Authored-By 标注 AI 工具的贡献

## Automation Tasks & TODO.md
File: keep a single docs/TODO.md to track phased tasks.
Sections: Pending, In Progress, Completed, Blocked.
Item format: short verb phrase + optional owner and ID. Update status by moving the line between sections and toggling the checkbox.
