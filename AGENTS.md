# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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

### Worktree 强制规则

**每次代码变更或创建 subagent 时，必须先使用 `superpowers:using-git-worktrees` 技能创建隔离的 worktree。**

这是为了：
- 保证并行开发的隔离性
- 支持多个 subagent 同时工作而不冲突
- 保持主分支干净

**唯一例外：** 仅修改文档（如 AGENTS.md）或执行简单的 git 操作（如 commit）时可跳过。

### 技能触发表

#### 开发流程技能 (superpowers)

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `superpowers:brainstorming` | **创意工作前（强制）** | 通过提问细化想法，探索替代方案，分段呈现设计供验证，保存设计文档 |
| `superpowers:using-git-worktrees` | **每次代码变更或创建 subagent（强制）** | 在新分支上创建隔离工作区，运行项目设置，验证测试基线 |
| `superpowers:writing-plans` | 有规格或需求时 | 将工作拆分为小任务（每个 2-5 分钟），包含精确文件路径、完整代码、验证步骤 |
| `superpowers:executing-plans` | 有实现计划时 | 在独立会话中按任务执行计划，带审查检查点 |
| `superpowers:subagent-driven-development` | 当前会话执行计划 | 为每个任务分派 subagent，两阶段审查（规格合规 + 代码质量） |
| `superpowers:dispatching-parallel-agents` | 有 2+ 独立任务时 | 并行处理无共享状态或顺序依赖的独立任务 |
| `superpowers:test-driven-development` | 实现功能或修复 bug 时 | 强制 RED-GREEN-REFACTOR：写失败测试 → 看它失败 → 写最小代码 → 看它通过 → 提交 |
| `superpowers:systematic-debugging` | 遇到 bug、测试失败或异常行为时 | 在提出修复方案前系统性调试 |
| `superpowers:verification-before-completion` | 声称工作完成前 | 运行验证命令并确认输出后才能声称成功 |
| `superpowers:requesting-code-review` | 完成任务或重大功能后 | 根据计划审查，按严重性报告问题，关键问题阻止进度 |
| `superpowers:receiving-code-review` | 收到代码审查反馈时 | 技术严谨和验证，不盲目同意或实现 |
| `superpowers:finishing-a-development-branch` | 任务完成时 | 验证测试，提供选项（merge/PR/keep/discard），清理 worktree |
| `superpowers:writing-skills` | 创建或编辑技能时 | 创建、编辑或验证技能 |
| `superpowers:using-superpowers` | 开始任何对话时 | 建立如何查找和使用技能 |

#### 代码质量技能

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `taste-check` | 审查代码质量时 | 用 Linus Torvalds "好品味"哲学审查代码，消除防御性代码、特殊情况、深层嵌套 |
| `code-review:code-review` | 审查 PR 时 | 对 pull request 进行代码审查 |

#### Git 操作技能

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `commit-commands:commit` | 需要提交时 | 创建 git commit |
| `commit-commands:commit-push-pr` | 需要提交并创建 PR 时 | 提交、推送并打开 PR |
| `commit-commands:clean_gone` | 清理分支时 | 清理所有标记为 [gone] 的 git 分支（远程已删除但本地仍存在） |

#### 研究技能

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `research` | 需要调研库、API、模式时 | 使用 searchGitHub 和 Exa 工具研究，查找真实实现，保存结构化报告到 docs/research/ |

#### 前端技能

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `frontend-design:frontend-design` | 构建 web 组件、页面或应用时 | 创建独特、生产级的前端界面，避免通用 AI 美学 |

#### Ralph Wiggum 技能

| Skill | Trigger | Purpose |
|-------|---------|---------|
| `ralph-wiggum:ralph-loop` | 需要持续迭代时 | 在当前会话启动 Ralph Wiggum 循环 |
| `ralph-wiggum:cancel-ralph` | 需要取消循环时 | 取消活跃的 Ralph Wiggum 循环 |
| `ralph-wiggum:help` | 了解技术时 | 解释 Ralph Wiggum 技术和可用命令 |
