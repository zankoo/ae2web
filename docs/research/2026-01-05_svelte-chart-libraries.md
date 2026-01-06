# Svelte 图表库调研（面向 AE2Web 产量统计）

Date: 2026-01-05

## 【Research Results】

### Core Finding
对本项目（SvelteKit SPA + 只需要时间序列折线图）而言，**Apache ECharts（推荐搭配 `svelte-echarts`）**在“美观度、功能完整度、生态、可定制性”之间最均衡；如果想要更“组件化一把梭”且接受 Carbon 视觉风格，可以选 **`@carbon/charts-svelte`**。

---

## 背景与约束（本项目）

- 前端：`webui/`（SvelteKit / Svelte 5，SPA，Tailwind + DaisyUI）
- 需求：时间序列（amount vs time），tooltip，缩放/平移可选，暗色主题一致性，最好能**按需懒加载**（只在打开图表模态框时加载库）
- 注意：SvelteKit 环境中很多图表库会遇到 `window is not defined`，需要 `onMount`/`browser` 保护或动态 import

---

## 候选库一览（结论导向）

| 库 | 美观 | 功能 | 体积/性能 | SvelteKit 适配 | 备注 |
|---|---:|---:|---:|---:|---|
| **Apache ECharts + `svelte-echarts`** | 高 | 很高 | 中（可 tree-shake） | 好（需 client-only） | 推荐默认方案 |
| **`@carbon/charts-svelte`** | 高（统一风格） | 高 | 中 | 好 | 依赖/风格更“重” |
| ApexCharts（Svelte wrapper） | 高 | 中~高 | 中 | 中（易踩 SSR） | 生态大但 wrapper 分散 |
| LayerCake / LayerChart | 中~高（靠设计） | 中~高 | 取决于实现 | 很好 | 更像“可视化框架”，实现成本高 |
| Chart.js（直连或 wrapper） | 中 | 中 | 中 | 中（需 client-only） | wrapper 维护度参差 |
| uPlot（+ Svelte wrapper） | 低~中（默认朴素） | 中 | **高** | 好 | 偏“性能优先”，美观要自己做 |
| TradingView lightweight-charts（+ wrapper） | 高（金融风格） | 高（金融向） | 高 | 中 | 非金融场景可能风格不搭 |

---

## Evidence from Real Code（真实项目/仓库）

### 1) `svelte-echarts`（Apache ECharts wrapper）
- Repo: https://github.com/bherbruck/svelte-echarts
- 特点：需要手动 `use([...])` 注册组件以获得 tree-shaking；通过 `options` 传入 ECharts 配置。

### 2) Carbon Charts（Svelte 官方 wrapper）
- Docs: https://charts.carbondesignsystem.com/
- Repo: https://github.com/carbon-design-system/carbon-charts
- Package: https://www.npmjs.com/package/@carbon/charts-svelte
- 特点：组件库形态（多 chart 类型），文档与示例完善，主题支持明显。

### 3) ApexCharts（Svelte wrapper 示例）
- Repo（之一）: https://github.com/bn3t/svelte-apexcharts
- 示例站： https://bn3t.github.io/svelte-apexcharts/
- 典型坑：SvelteKit/SSR 下需要 `browser`/`onMount`，否则可能出现 `window is not defined`。

### 4) uPlot（Svelte wrapper）
- Repo: https://github.com/skalinichev/uplot-wrappers
- 特点：非常适合大量点的时间序列（性能强），但外观要靠自定义主题/样式补齐。

---

## Official Context（官方/权威文档要点）

### Apache ECharts
- Handbook: https://echarts.apache.org/handbook/en/
- Import / tree-shaking: https://apache.github.io/echarts-handbook/en/basics/import/
- 关键点：ECharts 支持按模块导入与注册组件（charts/components/renderer），利于控制 bundle 体积。

### Chart.js
- Integration: https://chartjs.org/docs/latest/getting-started/integration
- 关键点：Chart.js 本身是通用方案，但在框架中要注意生命周期与 canvas resize。

---

## Recommended Approach（给 AE2Web 的建议）

### 推荐：ECharts（`svelte-echarts`）+ “打开模态框再加载”

理由：
- 时间序列折线（tooltip、缩放、主题）开箱即用，默认观感成熟
- tree-shaking 可控，适合“只用一两种图”的场景
- 方案可扩展：后续想加多 series、标注、区间、对比都方便

落地要点：
1. 图表组件放在 `webui/src/lib/components/modals/ProductionChartModal.svelte`
2. 在模态框打开时 `await import(...)`（或动态 import 图表组件），避免首屏加载图表库
3. 在 `onMount` 中初始化/渲染（或用 wrapper 自带组件），避免 SSR/预渲染触发 `window` 问题
4. 用统一主题变量（与 Tailwind/DaisyUI 对齐）设置线条/网格/tooltip 样式

---

## Watch Out For（常见坑）

- **SSR / “window is not defined”**：很多图表库依赖 DOM；SvelteKit 里要 client-only（`onMount`/`browser`/动态 import）。
- **bundle 体积**：优先选支持 tree-shaking 的方案；图表只在模态框用就应该懒加载。
- **时间轴格式**：不要把 `timestamp` 直接当字符串拼 labels；建议统一在图表层做格式化（并注意时区）。
- **数据量与刷新频率**：SSE 高频更新时不要每次都完整 setOption/重建图；需要“增量更新”或节流刷新（例如 250ms~1s）。

