# 【Research Results】 DaisyUI 组件审计（ae2web/webui）

### Core Finding
`webui/` 已引入 daisyUI（`daisyui@^5.5.14`），但之前在多个位置仍用自定义组件/手写结构实现了 daisyUI 已提供的 UI 组件（button/card/badge/stat/status/toast）。本次按你选择的策略 **B**：改为直接使用 daisyUI 组件类，并移除对应封装组件。

### Evidence from Real Code
- 自定义封装（已移除）：`webui/src/lib/components/Button.svelte`、`webui/src/lib/components/Card.svelte`、`webui/src/lib/components/Badge.svelte`
- 改为直接使用 daisyUI class 的位置（示例）：
  - `webui/src/routes/+page.svelte`：统计区改用 `stats/stat`，按钮改用 `btn`
  - `webui/src/lib/components/TerminalSelector.svelte`：在线状态点改用 `status`
  - `webui/src/lib/components/ToastHost.svelte`：改用 `toast` wrapper + `alert`

### Official Context (from daisyUI llms.txt)
以下组件在 daisyUI 中有对应规范（class 名）：
- Button: `btn` + `btn-primary|btn-ghost|btn-outline|btn-sm|btn-circle|btn-square|...`
- Card: `card` + `card-body|card-title|card-actions|...`
- Badge: `badge` + `badge-*`
- Stat: `stats` + `stat|stat-title|stat-value|...`
- Status: `status` + `status-success|status-error|status-sm|...`
- Toast: `toast` + `toast-top|toast-end|...`

### Recommended Approach
1. 优先使用 daisyUI “组件类 + 修饰类”的组合（例如 `btn btn-ghost btn-sm`），避免再引入只做 class 拼接的自定义封装。
2. 仅保留“有明显业务/交互逻辑”的组件封装（例如 `IconImage` 的 lazy/retry、`ItemTooltip` 的定位与 MC 文本渲染），这些不是 daisyUI 目标覆盖范围。

### Watch Out For
- icon-only `<button>` 需要 `aria-label`/`title`（svelte-check a11y 会提示）。
- `toast` 作为 wrapper 时，测试/选择器不要把 `.toast` 当作“单条 toast”；若需要兼容旧测试，可在无 toast 时不渲染 wrapper。

