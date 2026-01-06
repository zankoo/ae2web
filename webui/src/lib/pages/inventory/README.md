# inventory 页面级逻辑

这里放 **inventory 页面专用** 的 controller / helper（SSE、tooltip、CPU 进程轮询、滚动锚点等）。

约定：
- 不放通用工具函数（优先放到 `webui/src/lib/utils/`）
- 不依赖 Svelte 5 runes（这些文件是 `.ts`，保持为普通 TS 模块）
- 通过参数注入依赖与回调，页面负责把 state 写回（便于测试、避免隐藏副作用）

