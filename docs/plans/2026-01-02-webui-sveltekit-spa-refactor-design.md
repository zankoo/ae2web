# Web UI 重构方案（SvelteKit 单页 + lodash + Gradle(B) 集成构建）

**目标：** 将现有 `src/main/resources/web/` 下的原生 JS 模块式前端重构为现代前端工程（SvelteKit + TypeScript），保持“Netty 只负责静态资源 + `/api/*`”的部署形态不变，提升可维护性/可测试性/性能与工程效率。  
**决策：** 单页应用（无浏览器路径路由，仅组件切换）。  
**约束变更：** 不再要求离线构建（允许 Gradle 过程中联网拉取 Node/npm 依赖）。  

---

## 1. 背景与现状

当前 Web UI 位于：
- 静态资源入口：`src/main/resources/web/index.html`
- JS：`src/main/resources/web/js/*`（原生模块化）
- CSS：`src/main/resources/web/style.css`
- 服务器：Java/Netty (`src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`) 直接从 classpath `web/` 读取并返回静态资源。

现状优点：
- 部署简单（随 mod JAR 带资源）
- 无额外运行时（无需 Node/SSR）
- API + SSE 已实现基本能力

主要痛点：
- UI/状态/副作用耦合偏高，难以扩展与重构
- 类型缺失导致 API 字段变更不易发现
- 测试缺失（前端侧几乎无法自动化）
- 复杂交互（SSE、筛选、去抖、图标加载、模态框）难以保持一致性与可读性

---

## 2. Netty 承载的关键限制（必须在方案中贯彻）

1) **静态资源托管（Classpath web/）**
- Netty 只适合做：静态文件 + `/api/*` JSON + SSE + PNG/二进制接口。
- 不适合引入依赖 Node SSR 运行时的形态（SvelteKit SSR/Server Actions/服务端路由）。

2) **鉴权能力限制**
- `/api/*` 需要鉴权；但浏览器 `EventSource` / `<img>` 不能设置 `Authorization` header：必须支持 `?token=`（现有后端已支持）。
- 前端必须实现“同一 token，同时用于 fetch header 与 SSE/img query”的统一策略。

3) **SPA 路由限制**
- 单页方案不依赖浏览器路径，因此 Netty 不需要实现 history fallback。
- 视图切换通过内部状态（tab/按钮）完成，可选使用 `?view=` 仅做记忆（非必须）。

4) **静态资源 Content-Type**
- SvelteKit 输出会包含目录（例如 `_app/immutable/...`）与多文件类型；后端 content-type 需覆盖 `.js/.css/.svg/.png/.woff2/...`，必要时补齐 `.map/.webp` 等（是否启用 sourcemap 可在构建中关闭）。

---

## 3. 技术选型（已确定）

**框架与工程**
- SvelteKit（Vite 驱动）
- TypeScript
- `@sveltejs/adapter-static`（纯静态产物）
- `lodash-es`（按函数导入，tree-shaking 友好）

**请求与状态**
- 方案 A（推荐默认）：Svelte stores + 手写 API client（轻量、可控、足够）
- 方案 B（可选）：`@tanstack/svelte-query` 管理请求缓存/重试（引入后复杂度上升，但对缓存一致性更好）

**测试**
- Vitest + `@testing-library/svelte`（组件/逻辑测试）
- MSW（可选，用于 mock `/api/*`）
- Playwright（可选，E2E；依赖运行中的 Netty/或 MSW 模拟）

**样式**
- 延续现有 `style.css` 设计语言（CSS variables + 工业终端风），逐步迁移为“组件局部样式 + 全局 tokens”的结构。
- 不强依赖 Tailwind（避免二次复杂度）；若后续需要快速迭代可再评估。

---

## 4. 目标架构（前端内部）

### 4.1 单页信息架构（UI IA）

单页包含若干“面板/视图”：
- Inventory（默认）：列表/搜索/过滤（items/fluids/all）、图标、tooltip、craftable、数量
- Craft Modal：提交 craft 请求（沿用现有 API）
- CPUs：CPU 状态列表（沿用现有 API）
- Settings（可选）：token/终端选择/调试信息等

### 4.2 模块划分（建议目录）

```
webui/
  src/
    lib/
      api/
        client.ts
        inventory.ts
        terminals.ts
        cpus.ts
        prerender.ts
      stores/
        session.ts
        terminal.ts
        inventory.ts
        cpus.ts
      components/
        Header/
        TerminalSelect/
        Inventory/
        Craft/
        Cpus/
      utils/
        format.ts
        pinyin.ts
    routes/
      +page.svelte
      +layout.svelte
```

原则：
- API 层与 UI 层严格分离（API 返回类型在 `lib/api` 定义）
- 全局状态尽量少：只保留 token、当前 terminal、当前 filter/search、以及拉取到的数据
- SSE 事件只触发“刷新/增量更新”的动作，不直接操作 DOM

---

## 5. 与后端 API 的对接策略

### 5.1 token 获取与注入

- token 来源：`new URLSearchParams(location.search).get('token')`
- fetch：统一通过 `apiClient` 注入 `Authorization: Bearer <token>`
- SSE：`/api/events/inventory?terminalId=...&token=<token>`
- 图标：`/api/prerender/image?id=<iconKey>&token=<token>`

### 5.2 SSE 策略（Inventory）

- 建议：保持现有语义（`hello`、`inventory`），收到 `inventory` 时触发 `reloadInventory()`。
- 防抖：短时间多事件合并为一次刷新（可用 `lodash-es/debounce`）。
- 终端切换：先关闭旧 EventSource，再建立新连接。

---

## 6. Gradle(B) 构建集成（方案要点）

目标：执行 `./gradlew clean build` 时自动构建 webui 并将静态产物打包进 mod JAR；不再手动维护 `src/main/resources/web/js/*`。

### 6.1 Node 插件建议

使用 `com.github.node-gradle.node`（或等价方案），在 Gradle 中完成：
- 固定 Node 版本（保证 CI/本机一致）
- `npm ci`（可重复、稳定）
- `npm run build`
- 将输出目录复制进 `build/resources/main/web/`（由 `processResources` 或专用 Copy task 完成）

### 6.2 构建产物目录

SvelteKit adapter-static 默认可输出到 `webui/build/`（或自定义）。

复制策略：
- `copyWebUi`：从 `webui/build/**` 复制到 `build/resources/main/web/**`
- `processResources.dependsOn(copyWebUi)`，保证 jar 内有最新 UI

### 6.3 开发体验（本地 dev）

- `npm run dev` 使用 Vite dev server（`localhost:5173`）
- 通过 Vite proxy 转发 `/api/*` 到 `http://localhost:8080`（Netty 端口可配置）
- 玩家运行 `./gradlew runClient` 启动 mod + Netty；前端在浏览器访问 Vite dev server 即可热更新 UI。

---

## 7. 迁移策略（分阶段，避免一次性大爆炸）

> 单页重构仍然建议分阶段进行，保证每一步都能跑通核心链路。

### Phase 0：基建与契约
- 建 `webui/` SvelteKit 工程，跑通 `npm run build`（产物可被 Netty served）
- 引入 TypeScript、eslint/prettier（可选，但建议）
- 定义前端 DTO 类型（按后端 JSON 结构建类型）

### Phase 1：最小可用壳（Skeleton）
- 单页布局（header + 左 inventory + 右侧 cpus/信息区）
- `/api/status` 联通，展示 ONLINE/OFFLINE
- token 注入策略落地（fetch header + SSE/img query）

### Phase 2：终端选择（Terminals）
- `/api/terminals` 拉取列表
- 选择 terminalId 后刷新 Inventory + SSE

### Phase 3：Inventory（核心）
- `/api/items?type=...&terminalId=...` 拉取全量
- 搜索（lodash debounce）
- 过滤（items/fluids/all）
- 统计：Total Items（amount 求和）、Types（条目数）
- 图标加载（`<img>` + token query），失败兜底 placeholder
- tooltip：`/api/prerender/tooltip?id=...`
- SSE：收到 inventory 事件触发刷新（带防抖与 requestId 去重）

### Phase 4：Craft + CPUs
- 复用 `/api/craft`、`/api/cpus`，实现现有交互
- CPU 列表可做增量更新（按 id 比较）减少 DOM 抖动

### Phase 5：性能与体验收尾
- 列表规模大时评估虚拟列表（可选）
- 图标缓存策略（内存 set + 浏览器 cache-control）
- 错误提示与重试策略统一（toast）

---

## 8. 风险与对策

- **构建链路引入 Node**：CI/本机差异导致失败  
  - 对策：Gradle 固定 Node 版本 + `npm ci`；CI 增加 npm cache
- **JAR 体积变大**：字体/图标/依赖打包  
  - 对策：按需引入（lodash-es）；字体可改为系统字体或 subset；关闭 sourcemap
- **Netty 静态 content-type 不全**  
  - 对策：构建阶段避免输出未知类型；必要时后端扩展 contentType 映射
- **SSE 连接风暴**：终端切换/重连频繁  
  - 对策：集中管理 EventSource 生命周期 + debounce 刷新

---

## 9. 示例代码（片段）

### 9.1 `webui/svelte.config.js`（静态适配）

```js
import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  kit: {
    adapter: adapter({
      // 单页不依赖 path 路由；这里不强制 fallback
      // 如未来要支持 path 路由，再配合 Netty 的 history fallback
      fallback: null
    })
  }
};

export default config;
```

### 9.2 `webui/vite.config.ts`（dev 代理到 Netty）

```ts
import { defineConfig } from 'vite';
import { sveltekit } from '@sveltejs/kit/vite';

export default defineConfig({
  plugins: [sveltekit()],
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
});
```

### 9.3 `webui/src/lib/stores/session.ts`（token 统一来源）

```ts
import { readable } from 'svelte/store';

function readTokenFromUrl(): string {
  return new URLSearchParams(window.location.search).get('token') ?? '';
}

export const authToken = readable<string>(readTokenFromUrl());
```

### 9.4 `webui/src/lib/api/client.ts`（fetch + Authorization）

```ts
import { get } from 'svelte/store';
import { authToken } from '$lib/stores/session';

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');

  const token = get(authToken);
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const resp = await fetch(path, { ...init, headers });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return (await resp.json()) as T;
}

export function withTokenQuery(url: string): string {
  const token = get(authToken);
  if (!token) return url;
  const u = new URL(url, window.location.href);
  u.searchParams.set('token', token);
  return u.toString();
}
```

### 9.5 `webui/src/lib/api/inventory.ts`（SSE + debounce）

```ts
import debounce from 'lodash-es/debounce';
import { apiJson, withTokenQuery } from './client';

export type ItemDto = {
  id: string;
  uniqueId: string;
  iconKey: string;
  name: string;
  translationKey: string;
  amount: number;
  type: 'item' | 'fluid';
  craftable: boolean;
};

export async function fetchInventory(terminalId: string, type: string) {
  const qs = new URLSearchParams({ type });
  if (terminalId) qs.set('terminalId', terminalId);
  return apiJson<{ items: ItemDto[]; total: number }>(`/api/items?${qs}`);
}

export function openInventorySse(terminalId: string, onInvalidate: () => void): EventSource {
  const debounced = debounce(onInvalidate, 250);
  const url = withTokenQuery(`/api/events/inventory?terminalId=${encodeURIComponent(terminalId)}`);
  const es = new EventSource(url);
  es.addEventListener('inventory', () => debounced());
  return es;
}
```

### 9.6 `webui/src/routes/+page.svelte`（单页骨架示意）

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import { fetchInventory } from '$lib/api/inventory';

  let terminalId = '';
  let filter: 'all' | 'items' | 'fluids' = 'all';
  let items = [];

  onMount(async () => {
    // TODO: 这里在完整实现时会先拉 terminals 并选中默认 terminalId
    const data = await fetchInventory(terminalId, filter);
    items = data.items;
  });
</script>

<main class="terminal-container">
  <!-- Header/Stats/Selector... -->
  <section>
    <h2>Inventory</h2>
    {#each items as item (item.uniqueId)}
      <div>{item.name} x {item.amount}</div>
    {/each}
  </section>
</main>
```

---

## 10. 交付标准（Definition of Done）

- `./gradlew clean build` 一键构建包含新 UI（无需手动拷贝 web 资源）
- UI 在 Netty 服务器中正常加载（同源），并能完成：终端选择、inventory 显示、搜索过滤、SSE 刷新、craft、cpus
- 前端有基础单测（Vitest）覆盖核心工具：token 解析、api client、SSE 触发逻辑（至少）
- 不依赖外部 CDN（字体/第三方脚本尽量打包进 JAR）

