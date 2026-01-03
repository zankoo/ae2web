# CPU 合成过程查看（列表 / 状态树 / 依赖树）设计

**日期：** 2026-01-03  
**目标：** 在 Web UI 中点击「正在合成的 Crafting CPU」即可查看该 CPU 内当前任务的详细过程与进度，并支持 3 种视图切换：列表、状态树（快速）、依赖树（实验）。

---

## 1. 背景

AE2 的 Crafting CPU 在执行合成时，内部会同时处理多层嵌套的子合成与等待队列。仅展示「最终产物 + 进度条」不足以定位卡住原因（缺料/等待/输出堆积等）。

AE2 运行时已经维护了相关状态（CraftingStatus、CraftingCpuLogic、ExecutingCraftingJob 等）。本功能的核心是：**把 AE2 的 CPU 内部执行状态转成可视化 JSON，并在 UI 弹窗中呈现**。

---

## 2. 设计目标与非目标

### 2.1 目标

1) CPU 面板中，点击忙碌 CPU 打开“合成过程”弹窗。  
2) 弹窗支持视图切换：
   - **列表**：类似 AE2 原生 Crafting Status 的扁平表格（stored / waiting / active）。  
   - **状态树（快速）**：将条目按状态分组，折叠展示，方便浏览（不承诺严格依赖关系）。  
   - **依赖树（实验）**：尽可能根据任务内的 pattern 信息推导父子关系，提供层级展开；遇到歧义/循环时做标注与保护。
3) 弹窗打开期间按 1~2 秒轮询刷新；关闭弹窗停止刷新。
4) 兼容鉴权（`/api/*` Bearer + `?token=`）。

### 2.2 非目标

- 不保证依赖树 100% 与 AE2 内部真实依赖一致（标注实验性）。
- 不做跨 CPU / 全网 crafting job 汇总（仅针对指定 CPU 当前 job）。

---

## 3. 后端 API 设计

### 3.1 现有接口（保持）

`GET /api/cpus?terminalId=<id>`：返回 CPU 列表与简要 jobStatus，用于 CPU 面板 2s 轮询。

### 3.2 新增接口：CPU 合成过程

`GET /api/cpus/process?terminalId=<id>&cpuId=<cpuId>`

返回结构（示例）：

```json
{
  "cpuId": "...",
  "busy": true,
  "name": "CPU 1",
  "summary": {
    "finalOutput": { "id": "...", "iconKey": "...", "name": "...", "translationKey": "...", "amount": 64 },
    "outputTotal": 64,
    "outputRemaining": 12,
    "outputDone": 52,
    "elapsedTimeNanos": 1234567890,
    "totalItems": 2000,
    "progress": 1500
  },
  "entries": [
    { "stack": { ... }, "stored": 10, "waiting": 5, "active": 2 }
  ],
  "patterns": [
    {
      "patternId": "namespace:path@h1_xxx",
      "remainingRuns": 123,
      "outputs": [ { ... } ],
      "inputs": [ { "choices": [ { ... } ], "multiplier": 1 } ]
    }
  ]
}
```

说明：
- `entries` 对齐 AE2 的 CraftingStatusEntry：`stored` / `waiting` / `active` 三列。
- `patterns` 来自 CPU 当前 job 的 task 列表（pattern → remainingRuns），用于前端构建“依赖树（实验）”。

---

## 4. CPU 标识（cpuId）

前端必须能稳定定位某个 CPU（不能只靠数组 index）。

策略（按优先级）：
1) 若底层 CPU 为 AE2 `CraftingCPUCluster`：反射读取 cluster bounds（min/max BlockPos），生成 `cpuId = "bounds:minX,minY,minZ~maxX,maxY,maxZ"`。  
2) 否则降级：`cpuId = "fallback:name=<name>|storage=<n>|coproc=<n>|mode=<m>|h=<identityHash>"`（同一次运行期间稳定）。

同时在 `/api/cpus` 返回中增加 `cpuId` 字段，供 UI 点击时传参。

---

## 5. 前端交互设计（弹窗）

### 5.1 触发

- CPU 卡片在 `busy && jobStatus` 时可点击，打开“合成过程”弹窗。

### 5.2 弹窗布局

1) Header：标题 + CPU 名称 + 视图 Tabs + 自动刷新开关/刷新按钮。  
2) Summary：最终产物 icon+名称+数量 + 进度条（优先 outputDone/outputTotal）+ elapsed。  
3) Body（按 Tab）：
   - 列表：条目表格（可按 waiting/active/stored 排序），支持搜索过滤。  
   - 状态树：根节点=最终产物；子节点=分组（Active/Waiting/Stored）；叶子=条目。  
   - 依赖树（实验）：根节点=最终产物；根据 patterns 推导“output → input choices”关系构造树；循环/歧义标注。

### 5.3 轮询策略

- 弹窗打开时立刻拉取一次；默认每 1500ms 刷新。
- 弹窗关闭立即 stop timer。
- 请求失败展示错误态但不阻塞关闭；下一轮继续重试（有上限/退避可后续优化）。

---

## 6. 测试策略

### 后端（JUnit + Mockito）
- 新增 `CpuProcessHandlerTest`：
  - stub 一个假的 CPU（包含 craftingLogic.job 与内部字段）；
  - 验证 `/api/cpus/process` 的 JSON 结构与关键字段（entries/patterns/summary）；
  - 验证未找到 CPU/无 job 时的错误返回。

### 前端（Vitest）
- 新增轻量测试：打开弹窗时会调用 process API、关闭时停止轮询（mock fetch）。
