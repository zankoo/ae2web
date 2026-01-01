# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AE2 Web Terminal is a Minecraft Forge mod (1.20.1) that adds a Web Terminal block connecting to Applied Energistics 2 ME networks. It exposes network inventory through an embedded HTTP server, allowing players to browse items, search, and submit crafting requests from any web browser.

## 修改审批流程（必须严格遵守）

**所有代码修改必须先提出方案，经用户同意后再执行。**

### 流程

1. **分析问题**：收集信息、调试、理解根因
2. **提出方案**：清晰描述修改内容、影响范围、tradeoff
3. **等待审批**：用户明确同意后才能修改代码
4. **执行修改**：按照已批准的方案进行修改
5. **验证结果**：构建、测试、确认修复

### 禁止事项

- ❌ 未经同意直接修改代码
- ❌ 在分析阶段就开始写代码
- ❌ 假设用户会同意某个方案

### 例外情况

- 用户明确说"直接改"或"你决定"
- 纯粹的格式化/typo 修复
- 用户已批准的方案的后续步骤

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

## Frontend Development Guidelines

**必须使用 `frontend-design` skill 进行前端 UI 修改。**

当涉及以下前端修改时，必须调用 `frontend-design` skill：
- 新增或修改 HTML 结构
- 新增或修改 CSS 样式
- UI 组件设计（按钮、面板、模态框等）
- 布局调整
- 视觉效果和动画

这确保前端修改遵循项目的科幻终端美学风格，保持设计一致性。

**当前设计规范：**
- 主题：深色科幻终端风格
- 主字体：Rajdhani（标题）、Share Tech Mono（数据）
- 主色调：`--ae2-accent: #00d4aa`（青绿色）、`--ae2-cyan: #22d3ee`
- 支持中文显示（Microsoft YaHei 作为后备字体）

## Key Dependencies

- Minecraft Forge 47.2.0 (MC 1.20.1)
- Applied Energistics 2 15.2.1
- Java 17
- Gson 2.10.1
- Netty (bundled with Minecraft)

## Known TODOs in Codebase

- Crafting request UX: expose terminal selection / CPU selection in API + UI

## Version Control & Semantic Versioning

版本号位置: `gradle.properties` 中的 `mod_version` 字段（唯一来源，构建时自动替换到 mods.toml）

### 语义化版本规范 (SemVer)

格式: `MAJOR.MINOR.PATCH`

| 类型 | 何时更新 | 示例 |
|------|----------|------|
| **MAJOR (X.0.0)** | 不兼容的 API 变更、重大架构重构、删除公开功能 | 1.0.0 → 2.0.0 |
| **MINOR (0.X.0)** | 新增功能、新增 API 端点、新增配置项（向后兼容） | 1.0.0 → 1.1.0 |
| **PATCH (0.0.X)** | Bug 修复、性能优化、代码重构（不改变功能） | 1.0.0 → 1.0.1 |

### 版本更新规则（必须严格遵守）

1. **每次提交前必须检查是否需要更新版本号**
2. **同一版本号只能提交一次**：如果当前版本已提交到 git，必须先更新版本号再提交新更改
3. **版本号只增不减**：永远不要降低版本号
4. **MINOR 更新时 PATCH 归零**：1.2.3 → 1.3.0
5. **MAJOR 更新时 MINOR 和 PATCH 归零**：1.2.3 → 2.0.0

### 变更类型判断

**PATCH (bug 修复)**:
- 修复代码逻辑错误
- 修复 API 返回错误数据
- 修复崩溃/异常
- 修复 UI 显示问题
- 性能优化（不改变行为）
- 代码重构（不改变功能）

**MINOR (新功能)**:
- 新增 API 端点
- 新增配置选项
- 新增 UI 功能
- 新增命令/交互方式
- 增强现有功能（向后兼容）

**MAJOR (破坏性变更)**:
- 删除或重命名 API 端点
- 更改 API 响应格式
- 更改配置文件格式
- 删除功能
- 不兼容的数据格式变更

### Commit 规范

- Commit message 格式: `<type>: <description>`
- type: `feat`(新功能), `fix`(修复), `refactor`(重构), `chore`(杂项), `docs`(文档)
- 每次提交前必须运行 `./gradlew build` 确保编译通过

## Automation Tasks & TODO.md
File: keep a single docs/TODO.md to track phased tasks.
Sections: Pending, In Progress, Completed, Blocked.
Item format: short verb phrase + optional owner and ID. Update status by moving the line between sections and toggling the checkbox.

## Codex 协作工作流

### 角色分工（必须严格遵守）

| 角色 | 职责 | 禁止事项 |
|------|------|----------|
| **Claude（架构师/调度者）** | 任务拆分、架构设计、代码审查、进度监控、与用户沟通 | 不亲自编写大量代码 |
| **Codex（执行者）** | 编写代码、修复 bug、重构、运行测试、更新 TODO.md | - |

**原则：Claude 负责"想"，Codex 负责"做"**

- Claude 只做轻量操作：读取文件、分析代码、设计方案、拆分任务
- 所有代码编写工作必须交给 Codex 执行
- Claude 通过 TODO.md 向 Codex 下达具体指令

Codex 是 Claude 的 AI 伙伴工具，可以通过命令行执行任务。通过循环启动 Codex 来持续完成 TODO.md 中的任务。

### 核心命令

```bash
codex exec "continue to next task" --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox
```

### 模型配置

**必须使用 gpt-5.2 模型执行 Codex 任务**

- Codex 默认会使用 gpt-5.2 模型（如配置文件中设置）
- 如需在命令中显式指定：`codex exec "task" --model gpt-5.2`
- gpt-5.2 提供更强的推理能力和代码质量，适合复杂任务

### 工作流程（项目管理者模式）

**接收任务时必须执行：**

1. **任务拆分**: 将用户需求拆分为具体可执行的子任务
2. **写入 TODO.md**: 将子任务写入 `docs/TODO.md` 的 Pending 部分
3. **启动 Codex**: 使用 Task Tool 启动 agent 执行 codex 命令
4. **监控执行**: agent 观察 codex 任务执行情况
5. **循环重启**: 任务完成后启动新 agent 继续下一个任务

### 任务拆分规范

- 每个任务应该是独立可执行的单元
- 任务描述清晰，包含具体文件或功能点
- 使用前缀标识优先级：`HIGH-`, `MED-`, `LOW-`
- 格式：`- [ ] PREFIX-N: 动词短语描述`

示例：
```markdown
## Pending
- [ ] HIGH-1: Fix inventory display bug in WebTerminalBlockEntity
- [ ] MED-1: Add pagination to /api/items endpoint
- [ ] LOW-1: Optimize CSS loading in web UI
```

### 执行规范

- 每次启动 codex 和监控都使用新的 agent（避免主 agent 上下文过长）
- Codex 根据 TODO.md 中的 Pending 任务自动执行
- 完成任务后 Codex 会更新 TODO.md 状态
- 主 agent 作为项目管理者，负责调度和监督

### 启动示例

```
用户需求 → 拆分任务 → 写入 TODO.md
  ↓
Task Tool → agent 执行 codex
  ↓
监控输出，等待完成
  ↓
任务完成 → 启动新 agent 重复
```

### 注意事项

- Codex 任务可能耗时较长，需要耐心等待
- 如果 TODO.md 的 Pending 为空，先拆分任务再启动 Codex
- 定期检查 TODO.md 状态确认进度
