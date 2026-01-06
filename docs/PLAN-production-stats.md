# 产量统计（Production Stats）Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

> 状态: **前端已完成** (Task 1-5)，后端可选任务 (Task 6-8) 待定
> 优先级: 中
> 预计复杂度: 中等（前端）~ 偏高（加后端历史与持久化）

**Goal:** 在 `webui/` 的库存界面里，为每个物品/流体显示“产量/消耗速率”，并提供历史趋势图（短期本地 + 可选服务器长期），且浏览器关闭后历史数据不丢失。

**Architecture:** 以现有的库存 SSE（`/api/events/inventory`）为实时数据源；前端负责实时速率计算与短期历史持久化；后端可选实现周期采样 + `/api/history` 用于长周期趋势。

**Tech Stack:** SvelteKit（Svelte 5）SPA：`webui/`；服务端 Forge/Netty：`src/main/java/...`；图表库见“图表库候选”（另有调研文档 `docs/research/2026-01-05_svelte-chart-libraries.md`）。

---

## 关键约束与统一口径（必须先定）

### 1) “同一个物品”的唯一标识

**必须使用 `uniqueId`，不要用 `id`**。

- `id`（如 `minecraft:diamond`）会把不同 NBT 变体/不同类型混在一起，不适合作为历史 key。
- 现有前后端库存对象已经提供 `uniqueId`：`item:<iconKey>` / `fluid:<iconKey>`（参考 `webui/src/lib/api/types.ts` 与服务端 `WebServer.buildUniqueId()`）。

计划中所有“itemId”字段/参数一律替换为 `uniqueId`。

### 2) 维度：按终端还是按网络？

本项目 UI 当前是“选择某个 terminalId”。因此默认统计维度为：

- **Key = `{terminalId, uniqueId}`**

（后续若要“跨终端聚合”，需明确 AE2 网络拓扑与去重策略，暂不做。）

### 3) 速率定义（单位与展示）

- `ratePerSecond = (amount[t] - amount[t0]) / (t - t0)`（t0 为窗口内最早点或上一点）
- **展示单位**：  
  - 绝对值 ≥ 1：`+/s` 或 `-/s`（可用 K 缩写）  
  - 绝对值 < 1：换算成 `+/m`（减少“0.0/s”抖动）
- **流体**：
  - **口径确认**：前端 `formatFluidAmount(milliBuckets)` 将 `amount / 1000` 显示为 `B`，因此服务端传到前端的流体 `amount` 单位是 **mB（milliBuckets）**。
  - **速率单位**：内部计算用 **mB/s**；展示时建议换算为 **B/s**（或 B/m），保持与库存数量显示一致。

---

## 总体架构（更新：基于 SSE，而非轮询）

```
┌──────────────────────────────────────────────────────────────┐
│                         前端（实时）                          │
│  ├── 数据源：Inventory SSE (/api/events/inventory)            │
│  ├── 速率：对“每次 amount 更新”做滑动窗口斜率估计               │
│  ├── 短期历史：仅对“最近 30s 有变化 / Pin / 打开图表”的 key 存序列 │
│  │   └── 可选：如仍担心内存，进一步限制为“当前视口可见 + Pin + 图表打开” │
│  ├── 持久化：IndexedDB（推荐）或节流 localStorage               │
│  └── 图表：按需懒加载（仅打开模态框时加载图表库）               │
├──────────────────────────────────────────────────────────────┤
│                      后端（可选：长期）                        │
│  ├── InventoryHistoryService：按 terminalId 周期采样            │
│  ├── 存储：环形缓冲 + 逐级降采样（避免 24h 全量爆内存）          │
│  ├── 可选：写入 config/ae2web/... JSON（服务器重启可恢复）       │
│  └── API：GET /api/history?terminalId=...&uniqueId=...&range=1h │
└──────────────────────────────────────────────────────────────┘
```

---

## 图表库候选（先选一个，避免实现漂移）

结论（推荐顺序，按“美观 + 易用 + 维护活跃 + SvelteKit 适配”）：

1. **Apache ECharts + `svelte-echarts`（推荐）**：功能强、样式成熟、支持 tree-shaking；适合时间序列折线 + tooltip + dataZoom。
2. **`@carbon/charts-svelte`**：组件化完整、文档好、主题支持；但视觉风格偏 Carbon Design，可能与现有 UI 不一致。
3. **ApexCharts（`@bn3t/svelte-apexcharts` 等 wrapper）**：默认很好看；注意 SSR/`window is not defined`，需要 `browser/onMount` 防护。
4. **LayerCake / LayerChart**：Svelte 原生、可 SSR；但“做出同样精致的交互”工作量更大，适合定制化强的场景。
5. **uPlot**：性能极好；但默认不“美观”，需要额外主题/样式投入。

调研详情见：`docs/research/2026-01-05_svelte-chart-libraries.md`

---

## 前端实现（SvelteKit / `webui/`）

### Task 1：定义生产统计的数据结构与算法（纯函数 + 单测）

**Files:**
- Create: `webui/src/lib/utils/productionStats.ts`
- Test: `webui/src/lib/utils/productionStats.test.ts`

**Step 1: 写 failing tests（窗口斜率、过期归零、snapshot 重置 + 边界 case）**

覆盖边界 case（至少）：
- 单点：无法计算斜率 → 不显示（`null`/`undefined`）
- 时间戳相同的多点：跳过/去重，避免 `dt=0`
- `amount` 未变化：不追加点（或追加但不影响斜率），rate 保持 0/不显示
- 负 `amount`（理论不应发生）：当成无效输入（忽略该点）

**Step 2: 运行测试确认失败**

Run: `cd webui && npm test`  
Expected: FAIL（函数/模块不存在）

**Step 3: 实现最小版本**

- 维护 `lastPoint`（timestamp, amount）
- 维护 `windowPoints`（按时间裁剪 + 点数上限，防止高频更新堆积）
- 默认窗口：最近 **30s**（已确认：B）
- `MAX_WINDOW_POINTS = 100`（超出则从最旧开始丢弃；仍需按时间裁剪）
- `rate = (latest.amount - oldestInWindow.amount) / dt`
- 若 `now - latest.timestamp > STALE_MS`：rate 视为 0/不显示
- 内存上限（前端）：entry 只为“最近 30s 有变化 / Pin / 打开图表”的 key 保留 `windowPoints`；其余 key 在超出窗口后直接 prune（或只保留一条基线）。
- 可选极限保护：全局最多保留 `MAX_ENTRIES`（例如 2000）个 entry，按 LRU/lastUpdate 淘汰最旧的非 pinned/非图表打开条目。

**Step 4: 运行测试确认通过**

Run: `cd webui && npm test`  
Expected: PASS

### Task 2：把统计接入库存 SSE 流（水位线：只在客户端更新）

**Files:**
- Modify: `webui/src/routes/+page.svelte`

**Implementation notes:**
- SSE `snapshot`：**清空 `productionStats`，再用 snapshot 中的 `uniqueId -> amount` 重建基线**（避免 resync/重连导致“第一跳”算出巨大速率）。  
  - snapshot 中不存在的 `uniqueId` 视为“不再追踪”，无需保留旧条目。
- SSE `delta.updated`：在写入 `inventoryState` 之前读取旧 amount，并在 `set` 前记录更新（只在 `amount` 真变化时记录）：
  - `productionStats.recordUpdate(terminalId, uniqueId, prev.amount, patch.amount, Date.now())`
- SSE `delta.added` / `delta.removed`：不要把 `0 -> 大数` 当成“生产速率”，应该 **重置基线**：
  - `added`：`productionStats.resetBaseline(terminalId, uniqueId, item.amount, Date.now())`
  - `removed`：`productionStats.resetBaseline(terminalId, uniqueId, 0, Date.now())`（或直接删除该 entry）
- 切换 terminal 时：清空 `productionStats`（terminalId 是 key 的一部分）。

### Task 3：在库存网格显示速率 badge

**Files:**
- Modify: `webui/src/lib/components/InventoryGrid.svelte`
- (Optional) Create: `webui/src/lib/components/ProductionRateBadge.svelte`
- Modify: `webui/src/app.css`（或组件内 Tailwind 类）

**Behavior:**
- badge 仅在 rate 绝对值超过阈值时显示（默认 0 不显示，避免 UI 噪音）
  - 默认阈值（可后续做成配置）：items `0.1/s`；fluids `0.1B/s`（即 `100mB/s`）
- 绿色为正、红色为负
- hover 显示 tooltip（“最近 30s 平均：+12.3/s”等）
  - 降级策略：无法计算（点数不足/`dt<=0`/非法输入）→ 直接不显示 badge（而不是显示 0 或上一值）
  - 位置规范（避免遮挡 name/icon）：建议放在 `.item-slot` **左上角**（镜像 `.item-amount` 右上角）

### Task 4：图表模态框（懒加载图表库）

**Files:**
- Create: `webui/src/lib/components/modals/ProductionChartModal.svelte`
- Modify: `webui/src/routes/+page.svelte`（管理 open/close）

**Implementation notes:**
- 只在模态框打开时 `await import(...)` 加载图表组件/库（减少初始 bundle）
- 图表数据源：  
  - 本地短期：`productionStats` 中该 `uniqueId` 的 points  
  - 可选服务器长期：调用 `/api/history`
- 合并策略：以 timestamp 合并并去重（同一 timestamp 以“更近的数据”为准）
- 实时更新节流：SSE 高频更新时不要每次 setOption/重建图表  
  - 建议图表刷新节流 `500ms`（250ms~1s 均可），批量合并更新再刷新一次
  - 图表实例保持复用（只更新数据），避免反复 new chart

### Task 5：跨会话持久化（IndexedDB 优先）

**Files:**
- Create: `webui/src/lib/storage/productionHistory.ts`
- Modify: `webui/src/routes/+page.svelte`

**Rules:**
- 仅持久化“被 pin/最近打开过图表”的条目（避免全量写爆）
- 写入节流（例如 1~5s 一次），并做 TTL 裁剪（例如 24h）
- 避免“全量覆写一个大对象”：
  - IndexedDB 建议用 object store 按 `{terminalId, uniqueId}` 分条目存
  - 写入仅更新变化条目（增量写入），而不是每次把所有条目序列整体覆盖
  - 可选：进一步把点存成 `{terminalId, uniqueId, timestampMs} -> amount` 的多记录结构，便于 append/TTL 清理

---

## 后端实现（可选：长周期历史 + API）

> 如果只需要“关闭浏览器不丢数据”，前端 IndexedDB 已可满足；下面是为“跨设备/更长周期”准备的可选项。

### Task 6：新增 InventoryHistoryService（按 `{terminalId, uniqueId}` 存储）

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/web/history/InventoryHistoryService.java`
- (Optional) Test: `src/test/java/.../InventoryHistoryServiceTest.java`

**Design:**
- `record DataPoint(long timestampMs, long amount) {}`
- `Map<String /*terminalId*/, Map<String /*uniqueId*/, RingBuffer<DataPoint>>>`
- 采样间隔：默认 30s（可配置）
- 内存限制：  
  - 每条序列最多 `MAX_POINTS`（环形缓冲）  
  - 可选逐级降采样（例如：最近 1h 30s 点，1h~24h 2min 点）

**Threading (IMPORTANT):**
- 读取 `terminal.getNetworkInventory(...)` 必须在 **server thread** 执行
- 周期触发可用 `WebServer.getInstance().getIoExecutor().scheduleAtFixedRate(...)`，但每次采样要 `ServerLifecycleHooks.getCurrentServer().execute(...)` 再读库存
- 并发设计：推荐让 **采样写入** 与 **/api/history 读取** 都在 server thread 上执行，这样 RingBuffer 无需额外并发容器；若未来引入跨线程读取，则对每条序列加 `synchronized`（或使用线程安全容器）。

### Task 7：新增 /api/history

**Files:**
- Create: `src/main/java/com/kokoda/ae2web/web/handlers/HistoryHandler.java`
- Modify: `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java`

**Endpoint:**
- `GET /api/history?terminalId=<id>&uniqueId=<item:...>&range=10m|1h|6h|24h`

**Response (统一为一个 JSON 结构，不要返回裸数组):**
```json
{
  "terminalId": "xxx",
  "uniqueId": "item:minecraft:diamond@h1_...",
  "range": "1h",
  "points": [
    { "timestamp": 1703980800000, "amount": 1000 },
    { "timestamp": 1703980830000, "amount": 1050 }
  ]
}
```

**Error handling:**
- 缺参数/非法 range：400
- terminal 不存在或未连接：503
- 无数据：200 + points=[]

### Task 8（可选）：服务端文件持久化

参考 `PrerenderStorageService` 的原子写入方式，把历史写到 `config/ae2web/production-stats/*.json`，并加写入节流（例如 30s~2min）与启动加载。

---

## 验证方式（最小闭环）

### 前端
- Run: `cd webui && npm test`
- Run: `cd webui && npm run check`（可选）

### 集成
- Run: `./gradlew webuiBuild`（确保构建产物进入 `build/resources/main/web/`）
- 在游戏内打开 web UI：  
  - 观察库存变化时 badge 变化  
  - 打开图表：折线随时间增长  
  - 关掉浏览器再打开：历史仍在（IndexedDB/localStorage）

---

## 已确认

- 速率计算窗口：最近 **30s**（B）
