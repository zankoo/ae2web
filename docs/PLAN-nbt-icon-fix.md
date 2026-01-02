# NBT 变体物品图标修复计划

> Note (2026-01-02): The legacy vanilla web UI under `src/main/resources/web/` has been removed. The frontend is now in `webui/` (SvelteKit SPA). Any references below to `src/main/resources/web/*` are historical.

## 问题描述

当前预渲染系统使用 `ResourceLocation`（如 `gtceu:material_basic_core`）作为存储键，不区分 NBT 变体。导致具有相同 ID 但不同 NBT 的物品（如不同材料的基础核心）显示相同图标。

## 现有架构

```
游戏启动 → 遍历注册表 → 渲染每个物品 → 存储到 prerender/{item_id}.png
API 请求 → 返回 item_id → 前端请求 /prerender/{item_id}.png → 显示图标
```

**问题**：注册表只有基础物品，没有 NBT 变体。

## 修复方案

### 方案 A：NBT 感知预渲染（推荐）

**原理**：从 ME 网络获取实际物品，使用 NBT 哈希扩展存储键。

**存储格式**：`prerender/{item_id}@{nbt_hash}.png`

**实现步骤**：

1. **PrerenderStorageService 修改**
   - 存储键格式：`{item_id}@{nbt_hash}` 或 `{item_id}@0`（无 NBT）
   - 新增方法：`hasIcon(String key)`, `getIconPath(String key)`

2. **预渲染触发修改**
   - 监听 ME 网络变化，收集新出现的 AEItemKey
   - 对每个 AEItemKey，计算其 NBT 哈希并检查是否已预渲染
   - 批量渲染新物品

3. **ItemIconRenderer 修改**
   - 接收 AEItemKey 而非 Item
   - 使用 `itemKey.toStack()` 获取完整 ItemStack（含 NBT）进行渲染

4. **API 响应修改（ItemStackDTO）**
   - 新增 `iconKey` 字段：`{item_id}@{nbt_hash}`
   - 前端使用此键请求图标

5. **前端修改**
   - 图标 URL：`/prerender/${iconKey}.png`

### 方案 B：动态渲染（备选）

**原理**：不预渲染，API 请求时实时渲染并缓存。

**优点**：无需监听 ME 网络，按需渲染。
**缺点**：首次请求延迟高。

## 影响文件

| 文件 | 修改内容 |
|------|----------|
| `PrerenderStorageService.java` | 支持 NBT 哈希键 |
| `ItemIconRenderer.java` | 接收 AEItemKey，渲染含 NBT 的 ItemStack |
| `RenderScheduler.java` | 从 ME 网络收集物品 |
| `PrerenderTrigger.java` | 触发 ME 网络物品渲染 |
| `ItemStackDTO.java` | 新增 iconKey 字段 |
| `FluidStackDTO.java` | 新增 iconKey 字段 |
| `app.js` | 使用 iconKey 请求图标 |

## 实施建议

1. 先验证名称修复效果
2. 分阶段实施图标修复
3. 保持向后兼容（无 NBT 物品仍用原格式）

## 相关代码位置

- 预渲染存储：`src/main/java/com/kokoda/ae2web/web/prerender/PrerenderStorageService.java`
- 图标渲染器：`src/main/java/com/kokoda/ae2web/client/render/ItemIconRenderer.java`
- 渲染调度：`src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java`
- DTO：`src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java`
- 前端：`src/main/resources/web/app.js`
