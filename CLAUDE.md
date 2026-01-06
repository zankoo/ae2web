# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Preference / 语言偏好

Always respond in **Chinese (中文)**. All explanations, summaries, and communications should be in Chinese.

## Project Overview

AE2 Web Terminal is a Minecraft Forge mod (1.20.1) that adds a Web Terminal block connecting to Applied Energistics 2 ME networks. It exposes network inventory through an embedded HTTP server, allowing players to browse items, search, and submit crafting requests from any web browser.

## Build Commands

```bash
./gradlew build              # Build the mod JAR (output: build/libs/ae2web-*.jar)
./gradlew runClient          # Run Minecraft client with mod loaded
./gradlew runServer          # Run Minecraft server with mod loaded
./gradlew runGameTestServer  # Run game tests
./gradlew runData            # Run data generators
./gradlew test               # Run backend JUnit tests only
./gradlew webuiTest          # Run frontend Vitest tests only
./gradlew clean              # Clean build outputs and .tmp directory
```

### Frontend Development

```bash
cd webui
npm run dev          # Start Vite dev server with HMR
npm run build        # Build static SPA to webui/build/
npm run test         # Run Vitest tests
npm run test:watch   # Run tests in watch mode
npm run check        # TypeScript/Svelte type checking
npm run lint         # ESLint
npm run format       # Prettier formatting
```

## Architecture

### Backend (Java/Forge)

**Main Entry Point:** `src/main/java/com/kokoda/ae2web/AE2WebMod.java`
- Registers blocks/items/block entities via deferred registries
- Manages WebServer lifecycle on server start/stop events
- Registers `/ae2web token` command

**Core Components:**

| Component | Purpose |
|-----------|---------|
| `block/WebTerminalBlock.java` | Placeable block, wrench interaction for rename GUI |
| `blockentity/WebTerminalBlockEntity.java` | AE2 ME network connection via `IGridNodeListener`, provides `getNetworkInventory()` and `getCraftingService()`, implements `IStorageWatcherNode` and `ICraftingWatcherNode` for real-time inventory updates |
| `web/WebServer.java` | Singleton Netty HTTP server (default port 8080), manages terminal registry, SSE inventory broadcasting, and IO executor pool |
| `web/handlers/ApiHandler.java` | HTTP request router, serves static files from classpath `web/` and routes `/api/*` to specialized handlers |
| `config/AE2WebConfig.java` | Server config via `ae2web-server.toml` (httpPort, bindAddress, enableAuth, authToken, prerender settings) |

**Handler Organization:**
- `StatusHandler` - `/api/status`, `/api/terminals`
- `InventoryHandler` - `/api/items` with pagination, search, filtering
- `CraftingHandler` - `/api/craft`, `/api/cpus`, `/api/cpus/process`
- `SseHandler` - `/api/events/inventory` (Server-Sent Events for real-time updates)
- `PrerenderHandler` - `/api/prerender/*` (client-rendered icon upload/retrieval)
- `UserConfigHandler` - `/api/user/inventoryConfig` (per-player inventory view settings)

**Authentication:**
- `web/auth/PlayerAuthService.java` - Per-player token management
- `web/auth/AuthTokenExtractor.java` - Extracts token from Authorization header or query param

**Prerender System:**
- Client-side: `client/render/ItemIconRenderer.java`, `client/PrerenderTrigger.java`
- Server-side: `web/prerender/PrerenderStorageService.java` (disk cache with TTL-based GC)

### Frontend (SvelteKit SPA)

**Location:** `webui/` - SvelteKit with TypeScript, Tailwind CSS v4, DaisyUI v5

**Key Paths:**
- `src/routes/+page.svelte` - Main inventory view
- `src/lib/api/` - Typed API clients (inventory, craft, cpus, terminals, status)
- `src/lib/stores/session.ts` - Session state (token, terminalId, view config)
- `src/lib/components/` - Reusable UI components
- `src/tests/` - Vitest unit tests

**Build Integration:** Gradle `processResources` runs `webuiBuild` task, copying `webui/build/**` into classpath `web/` for the mod JAR.

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/status` | GET | Server status and connection info |
| `/api/terminals` | GET | List of connected terminals |
| `/api/items` | GET | Paginated inventory with search/filter |
| `/api/events/inventory` | GET | SSE stream for real-time inventory updates |
| `/api/craft` | POST | Submit crafting request |
| `/api/cpus` | GET | Crafting CPU status list |
| `/api/cpus/process` | GET | Detailed CPU crafting process (entries + patterns) |
| `/api/user/inventoryConfig` | GET/POST | Per-player inventory view config |
| `/api/prerender/*` | Various | Icon prerendering (missing, claim, upload, image, tooltip) |

## Key Dependencies

- Minecraft Forge 47.2.0 (MC 1.20.1)
- Applied Energistics 2 15.2.1
- Java 17
- Netty (bundled with Minecraft, codec-http embedded via jarJar)
- Gson 2.10.1
- SvelteKit 2.x, Svelte 5.x, Tailwind CSS 4.x, DaisyUI 5.x

## Version Control

Version is defined in `gradle.properties` (`mod_version`). Follow semantic versioning:
- MAJOR: Breaking API/config changes
- MINOR: New features (backward compatible)
- PATCH: Bug fixes, refactoring

## Testing

Backend tests use JUnit 5 + Mockito. Frontend tests use Vitest + Testing Library.

```bash
./gradlew test                    # Backend tests
cd webui && npm run test          # Frontend tests
./gradlew build                   # Runs both (check depends on webuiTest)
```
## Development Workflow

### Superpowers 技能系统（强制要求）

**核心规则：在任何响应或操作之前，必须检查是否有适用的技能。**

- 即使只有 1% 的可能性技能适用，也**必须**调用该技能
- 使用 `Skill` 工具调用技能，而不是直接读取技能文件
- 如果技能有 checklist，必须用 `TodoWrite` 为每个项目创建待办事项
- 技能是强制性工作流程，不是建议

**警告信号** - 如果你有以下想法，立即停止，你在找借口：
- "这只是一个简单问题" → 问题也是任务，检查技能
- "我先需要更多上下文" → 技能检查在澄清问题之前
- "让我先探索代码库" → 技能告诉你如何探索
- "这不需要正式的技能" → 如果技能存在，就使用它
- "我记得这个技能" → 技能会更新，读取当前版本
- "这个技能太过头了" → 简单的事情会变复杂，使用它

**技能优先级：**
1. 流程技能优先（brainstorming, debugging）- 决定如何处理任务
2. 实现技能其次（frontend-design, mcp-builder）- 指导执行

### 技能触发表

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `brainstorming` | Before writing code | Refines rough ideas through questions, explores alternatives, presents design in sections for validation. Saves design document. |
| `using-git-worktrees` | After design approval | Creates isolated workspace on new branch, runs project setup, verifies clean test baseline. |
| `writing-plans` | With approved design | Breaks work into bite-sized tasks (2-5 minutes each). Every task has exact file paths, complete code, verification steps. |
| `subagent-driven-development` or `executing-plans` | With plan | Dispatches fresh subagent per task with two-stage review (spec compliance, then code quality), or executes in batches with human checkpoints. |
| `test-driven-development` | During implementation | Enforces RED-GREEN-REFACTOR: write failing test, watch it fail, write minimal code, watch it pass, commit. Deletes code written before tests. |
| `requesting-code-review` | Between tasks | Reviews against plan, reports issues by severity. Critical issues block progress. |
| `finishing-a-development-branch` | When tasks complete | Verifies tests, presents options (merge/PR/keep/discard), cleans up worktree. |
