# AE2Web (AE2 Web Terminal) — AGENTS

This file is the working guide for agents operating in this repository.
Keep it aligned with the actual code (not old docs).

## Project Overview

AE2 Web Terminal is a Minecraft Forge mod (MC 1.20.1) that adds a Web Terminal block connecting to Applied Energistics 2 (AE2) ME networks.
It exposes the ME network inventory + crafting controls via an embedded Netty HTTP server and a small web UI.

## Release Status & Compatibility

- This project is not released yet; backward compatibility is NOT required.
- Behavior changes and breaking changes (API/config/UI) are OK; do not add compatibility layers unless explicitly requested.

## Java Coding Standard (Required)

- Follow Alibaba Java Development Manual (P3C, 黄山版) strictly for all Java code in this repo:
  `https://github.com/alibaba/p3c/blob/master/Java%E5%BC%80%E5%8F%91%E6%89%8B%E5%86%8C(%E9%BB%84%E5%B1%B1%E7%89%88).pdf`

## Build Commands

```bash
./gradlew clean build        # Clean + build mod JAR (default for agents)
./gradlew build              # Build mod JAR
./gradlew webuiBuild         # Build Web UI (SvelteKit → webui/build)
./gradlew runClient          # Dev client with mod loaded
./gradlew runServer          # Dev server
./gradlew runGameTestServer  # Game tests
./gradlew runData            # Data generators
./gradlew test               # Unit tests
```

Web UI (SvelteKit SPA):

```bash
npm -C webui ci        # Install frontend deps (reproducible)
npm -C webui run dev   # Vite dev server (proxies /api to 127.0.0.1:8080)
npm -C webui test      # Vitest
npm -C webui run check # svelte-check (TypeScript + Svelte)
```

Note: `./gradlew clean build` runs `webuiBuild` (Node download + `npm ci` + `npm run build`) and packages `webui/build/**` into the mod JAR.
`npm -C webui run dev` uses the system Node; if you need to change the backend target, edit `webui/vite.config.ts`.
Offline builds are no longer guaranteed after the SvelteKit migration (need pre-warmed Gradle + npm caches).

## Testing (Required)

- Unit tests MUST use JUnit + Mockito.
- Prefer AssertJ for assertions (example: `assertThat(list).hasSize(3).contains("a");`).
- Use Awaitility for async/eventual-consistency tests (avoid `Thread.sleep()`).
- Use WireMock or MockWebServer for HTTP client tests.
- Use Testcontainers when a real dependency is required (MySQL/Redis/Kafka/etc).
- After completing each task, run unit tests: `./gradlew test` (or `./gradlew clean build` for full verification, including frontend build).
- If the change touches `webui/`, also run: `npm -C webui test` (and ideally `npm -C webui run check`).
- Frontend unit tests are colocated under `webui/src/lib/**/*.test.ts` (Vitest); `webui/src/tests/setup.ts` is test setup.

If Gradle fails early with a ForgeGradle certificate pinning error like:

```
Failed to validate certificate for host 'https://libraries.minecraft.net/'
```

This is usually caused by upstream certificate rotation (or HTTPS interception). This repo defaults to disabling the pin check via `gradle.properties`:

```properties
systemProp.net.minecraftforge.gradle.check.certs=false
```

To re-enable for stricter validation, remove that line or run with:

```bash
./gradlew build -Dnet.minecraftforge.gradle.check.certs=true
```

## Architecture (Current)

**Main entry:** `src/main/java/com/kokoda/ae2web/AE2WebMod.java`

**Server-side components**
- Block + BE: `src/main/java/com/kokoda/ae2web/block/WebTerminalBlock.java`, `src/main/java/com/kokoda/ae2web/blockentity/WebTerminalBlockEntity.java`
- HTTP server: `src/main/java/com/kokoda/ae2web/web/WebServer.java` (Netty)
- Router: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`
- Inventory API: `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java`
- SSE events: `src/main/java/com/kokoda/ae2web/web/handlers/SseHandler.java`
- Crafting API: `src/main/java/com/kokoda/ae2web/web/handlers/CraftingHandler.java` + `src/main/java/com/kokoda/ae2web/web/handlers/crafting/*`
- Prerender API: `src/main/java/com/kokoda/ae2web/web/handlers/PrerenderHandler.java` + `src/main/java/com/kokoda/ae2web/web/handlers/prerender/*`
- Prerender storage (PNG + translations): `src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java`

**Client-side prerender pipeline**
- Continuous polling + auto render (while connected): `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java`
- HTTP client to server prerender endpoints: `src/main/java/com/kokoda/ae2web/client/network/PrerenderHttpClient.java`
- Render scheduler: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java`
- Manual command: `src/main/java/com/kokoda/ae2web/client/command/RenderCommand.java` (`/ae2web render ...`)

## Key System (Global, NBT-aware)

The project uses a single canonical key for icons and identity:

- `iconKey` format: `namespace:path@h1_<sha256(...)>`
- `uniqueId` (UI identity): `item:${iconKey}` or `fluid:${iconKey}`
- Source of truth: `src/main/java/com/kokoda/ae2web/util/IconKeyUtils.java`

Guideline: never use plain `id` or `hashCode()` as a UI key; NBT variants must remain distinct.

## Auth Model

- Auth is enforced only for `/api/*` paths (static assets are always public).
- Supported auth mechanisms:
  - `Authorization: Bearer <token>` header (general)
  - `?token=<token>` query parameter (required for browser `EventSource` and `<img>` because they cannot set `Authorization`)

## Web UI (Current)

- Source: `webui/` (SvelteKit SPA, TypeScript)
  - Main UI: `webui/src/routes/+page.svelte`
  - SPA config: `webui/src/routes/+layout.ts` (`ssr = false`, `prerender = true`)
  - Global CSS (ported from legacy): `webui/src/app.css`
  - Build output: `webui/build/` (adapter-static)
- Packaging: Gradle copies `webui/build/**` into classpath `web/` during `processResources` (see `build.gradle`)
- Legacy vanilla UI under `src/main/resources/web/**` has been removed (it was excluded from the JAR after the migration). Do not add new files there.

Inventory UI behavior:
- UI renders full inventory snapshot (no pagination).
- Real-time updates use SSE (`/api/events/inventory`) and refresh the view without DOM churn.

## HTTP API (Current)

Base: `/api/*`

- `GET /api/status` → `{ running, terminals, version }`
- `GET /api/terminals` → `{ terminals: [...] }`
- `GET /api/items?type=all|items|fluids&terminalId=<id>` → `{ items: [...], total }`
- `GET /api/events/inventory?terminalId=<id>&token=<token?>` → SSE events (`hello`, `inventory`)
- `POST /api/craft` → crafting requests (see `CraftRequestHandler`)
- `GET /api/cpus` → crafting CPU status

Prerender endpoints (server-side storage under `config/ae2web/prerender/`):
- `GET /api/prerender/image?id=<iconKey>` → `image/png` or `404`
- `GET /api/prerender/tooltip?id=<iconKey>` → `{ iconKey, tooltip: string[] }` or `404`
- `GET /api/prerender/missing?scope=active|registry|all&namespace=<ns?>&limit=<n>` → missing iconKeys; `active` includes ME inventory + craftables (NBT-aware, default), `registry` is base items/fluids only
- `POST /api/prerender/claim` → claim iconKeys for rendering (concurrency control)
- `POST /api/prerender/upload` → upload rendered PNG + translated name + tooltip lines
- `GET /api/prerender/stats` → cache/stats
- `POST /api/prerender/check` → check existing/missing for a list of iconKeys
- `GET /api/prerender/all?limit=<n>` → sample list of iconKeys (mostly registry-derived)

Prerender cache maintenance:
- Server persists last-seen timestamps in `config/ae2web/prerender/seen.json`
- Server GC deletes old NBT-variant icons based on `prerender.gcTtlSeconds` (runs every `prerender.gcIntervalSeconds`)

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
- 每次提交前必须运行 `./gradlew clean build` 确保编译通过

## Automation Tasks & TODO.md
File: keep a single docs/TODO.md to track phased tasks.
Sections: Pending, In Progress, Completed, Blocked.
Item format: short verb phrase + optional owner and ID. Update status by moving the line between sections and toggling the checkbox.

## 工作流

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

- 根据 TODO.md 中的 Pending 任务自动执行
- 完成任务后更新 TODO.md 状态

### 启动示例

```
用户需求 → 拆分任务 → 写入 TODO.md
  ↓
等待完成
  ↓
任务完成 → 更新 TODO.md
```

### 注意事项

- 任务可能耗时较长，需要耐心等待
- 如果 TODO.md 的 Pending 为空，先拆分任务再启动
- 定期检查 TODO.md 确认进度

## Diagnostics: NBT 图标/翻译渲染失败

目标：用 API 明确问题发生在「UI 请求」「服务端存储」「客户端 prerender 上传」哪一层。

### 1) 在 WSL 访问 Windows 上的 HTTP Server

在 WSL 里通常用默认网关访问 Windows Host（示例 `192.168.64.1`）：

```bash
WIN_HOST="$(awk '$2==\"00000000\"{g=$3} END{printf \"%d.%d.%d.%d\\n\", \"0x\"substr(g,7,2),\"0x\"substr(g,5,2),\"0x\"substr(g,3,2),\"0x\"substr(g,1,2)}' /proc/net/route)"
PORT=8080
BASE="http://${WIN_HOST}:${PORT}"
curl -sS "${BASE}/api/status"
```

如果启用了鉴权：`/api/*` 支持 `?token=...`（浏览器 `EventSource`/`img` 不能带 `Authorization` header）。

### 2) 找到实际终端与目标物品的 iconKey

```bash
curl -sS "${BASE}/api/terminals"
curl -sS "${BASE}/api/items?type=items&terminalId=<TERMINAL_ID>" > /tmp/items.json
python3 - <<'PY'
import json
items=json.load(open('/tmp/items.json')).get('items',[])
target='extendedae_plus:basic_core'  # 替换为你要查的物品
for x in items:
  if x.get('id')==target:
    print(x.get('id'), x.get('amount'), x.get('craftable'), x.get('iconKey'), x.get('name'))
PY
```

关注点：
- `iconKey` 必须是 `namespace:path@h1_<sha256...>`（NBT-aware）。
- `amount=0` 且 `craftable=true` 的条目，常见于「只有合成，没有库存」的变种（NBT 变种经常在这里）。

### 3) 判断是“没渲染/没上传”还是“前端请求错”

服务端是否已有 PNG：

```bash
ICONKEY='extendedae_plus:basic_core@h1_...'
curl -sS -D- "${BASE}/api/prerender/image?id=${ICONKEY}" -o /tmp/icon.png | head
```

- `200 image/png`：服务端已有图标（UI 若仍失败，检查请求 URL/鉴权 token）。
- `404 Icon not found`：服务端缺图标 → 继续查 prerender 流程。

批量检查哪些缺失：

```bash
python3 - <<'PY'
import json
items=json.load(open('/tmp/items.json')).get('items',[])
keys=[x['iconKey'] for x in items if x.get('id')=='extendedae_plus:basic_core']
open('/tmp/check.json','w').write(json.dumps({'itemIds':keys}))
print('keys',len(keys))
PY
curl -sS -X POST "${BASE}/api/prerender/check" -H 'Content-Type: application/json' --data-binary @/tmp/check.json
```

### 4) 判断“missing 列表是否覆盖 NBT 变种”

```bash
curl -sS "${BASE}/api/prerender/missing?namespace=extendedae_plus&limit=2000"
```

如果 `/api/prerender/check` 显示某些 `iconKey` 缺失，但 `/api/prerender/missing` 返回空或不包含它们：
- 说明这些 key 没被服务端纳入“待 prerender”的发现逻辑（典型：0 库存 craftable-only NBT 变种）。
