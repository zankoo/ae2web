# Issue: Missing Icons in Frontend Despite Successful Rendering

## 问题描述

用户报告：所有物品都已渲染完成（服务器状态显示 Missing: 0），但前端仍有大量物品图标不可见，例如：
- `ae2:dense_energy_cell`
- `extendedae_plus:basic_core`

同时，物品的翻译名称也没有显示。

## 根本原因分析

经过代码分析，发现**两个独立但相关的问题**：

### 问题 1: 前端缓存键不匹配（Frontend Cache Key Mismatch）

**位置**: `src/main/resources/web/app.js`

#### 后端数据结构

从 `ItemStackDTO.java` 可以看到：
```java
// 对于有 NBT 数据的物品
this.id = "ae2:dense_energy_cell";                    // 纯物品 ID
this.iconKey = "ae2:dense_energy_cell@123456789";     // ID + NBT hash
```

- `id`: 纯物品 ID（不包含 NBT）
- `iconKey`: 物品 ID + NBT 哈希值（用于区分不同 NBT 变种）

#### 前端缓存逻辑错误

**Line 220-221** (图标 URL 构建):
```javascript
const iconUrl = `/api/prerender/image?id=${encodeURIComponent(item.iconKey || item.id)}`;
const isCached = loadedIcons.has(item.id);  // ❌ 使用 item.id 检查缓存
```

**Line 225** (设置 data 属性):
```javascript
data-icon-id="${escapeHtml(item.id)}"  // ❌ 使用 item.id 而不是 iconKey
```

**Line 312-316** (缓存存储):
```javascript
const iconId = img.closest('.item-slot')?.dataset.iconId;  // 获取 item.id
img.src = img.dataset.src;
img.onload = () => {
    if (iconId) loadedIcons.add(iconId);  // ❌ 缓存 item.id 而不是 iconKey
};
```

#### 问题场景

**场景 A - 无 NBT 物品**（正常工作）:
1. `id = "ae2:foo"`, `iconKey = "ae2:foo@0"` (hash 为 0)
2. URL: `/api/prerender/image?id=ae2:foo@0`
3. 缓存键: `"ae2:foo"`
4. ✅ 工作正常

**场景 B - 有 NBT 物品**（缓存失效）:
1. 先渲染 `"ae2:foo"` (无 NBT), 缓存为 `"ae2:foo"` ✅
2. 后渲染 `"ae2:foo@123456"` (有 NBT):
   - 缓存检查: `loadedIcons.has("ae2:foo")` → `true` ✅
   - 但实际应该检查 `loadedIcons.has("ae2:foo@123456")`
   - 尝试加载 URL: `/api/prerender/image?id=ae2:foo@123456`
   - 如果这个特定的 NBT 变种未渲染 → **图标加载失败**
3. 即使后来 `"ae2:foo@123456"` 加载成功，也缓存为 `"ae2:foo"`
4. 导致无法区分不同的 NBT 变种

#### 影响

- 有 NBT 数据的物品图标可能无法正确显示
- 缓存系统无法区分同一物品的不同 NBT 变种
- 可能出现"缓存显示已加载，但实际图标未找到"的情况

---

### 问题 2: 缺失翻译名称（Missing Translations）

**位置**: 后端预渲染系统

#### 后端翻译获取逻辑

`InventoryHandler.java:173-182`:
```java
private static String getTranslatedName(AEItemKey itemKey, PrerenderStorageService storage) {
    // 尝试从预渲染缓存获取翻译（客户端语言）
    String itemId = itemKey.getId().toString();
    String cached = storage.getTranslation(itemId);
    if (cached != null && !cached.isBlank()) {
        return cached;
    }
    // 回退到服务器端名称（可能是服务器语言）
    return stripMinecraftFormatting(itemKey.getDisplayName().getString());
}
```

#### 翻译上传逻辑

`RenderScheduler.java:122-128`:
```java
taskQueue.add(new RenderTask(itemId, iconKey, item.nbt(), result -> {
    if (result != null && result.png() != null) {
        // 上传图标和翻译
        PrerenderHttpClient.getInstance().uploadIcon(
                serverUrl, iconKey, result.png(), result.translatedName());
    }
    // ❌ 如果 result == null，翻译永远不会上传
}));
```

#### 问题关联

此问题与 **ISSUE-infinite-retry.md** 中记录的渲染失败问题直接相关：

1. 某些物品渲染失败（可能因为 NBT 数据损坏或物品不存在）
2. `RenderScheduler.java:212` 调用 `task.onComplete.accept(null)`
3. 上传逻辑 `if (result != null && result.png() != null)` 跳过上传
4. **图标和翻译都未上传到服务器**
5. 后续 API 请求 `/api/items`:
   - `storage.getTranslation(itemId)` 返回 `null`
   - 回退到服务器端名称（可能是英文或服务器语言）
   - 如果服务器语言与客户端不同，显示错误语言

#### 影响

- 渲染失败的物品缺失客户端语言翻译
- 前端可能显示英文名称或空白名称
- 用户体验下降（无法识别物品）

---

## 修复方案

### 方案 1: 修复前端缓存键（立即修复，独立于重试问题）

**文件**: `src/main/resources/web/app.js`

**修改内容**:

1. **Line 221** - 使用 `iconKey` 检查缓存:
   ```javascript
   // 修改前
   const isCached = loadedIcons.has(item.id);

   // 修改后
   const isCached = loadedIcons.has(item.iconKey || item.id);
   ```

2. **Line 225** - 使用 `iconKey` 设置 data 属性:
   ```javascript
   // 修改前
   data-icon-id="${escapeHtml(item.id)}"

   // 修改后
   data-icon-id="${escapeHtml(item.iconKey || item.id)}"
   ```

**优点**:
- 简单直接，只修改 2 行代码
- 修复缓存键不匹配问题
- 独立于后端渲染系统
- 可立即部署

**缺点**:
- 不解决渲染失败导致的翻译缺失问题
- 如果图标确实未渲染，仍会显示占位符

---

### 方案 2: 修复渲染失败处理（解决翻译缺失，参考 ISSUE-infinite-retry.md）

此问题的根本解决需要修复 `ISSUE-infinite-retry.md` 中记录的渲染失败问题。

参考 ISSUE-infinite-retry.md 的修复方案：

#### 方案 2A: 添加失败跟踪和重试限制（推荐）

1. 跟踪每个物品的渲染失败次数
2. 超过阈值（如 3 次）后停止重试该物品
3. 提升失败日志级别为 `warn`
4. 可选：上传失败标记给服务器

**优点**:
- 彻底解决无限重试问题
- 避免渲染失败物品浪费资源
- 用户能清楚看到问题

**缺点**:
- 需要添加状态跟踪代码
- 稍微复杂一些

#### 方案 2B: 服务器端添加失败标记 API（最彻底）

1. 客户端渲染失败时，调用服务器 API 标记该物品为"无法渲染"
2. 服务器从 missing 列表移除该物品
3. 客户端不再重试已标记的物品

**优点**:
- 最彻底的解决方案
- 服务器状态准确
- 避免其他客户端也重试该物品

**缺点**:
- 需要服务器和客户端同时修改
- 实现复杂度最高

---

## 实施建议

### 优先级 1 - 立即修复（前端缓存键）

**方案 1**: 修改 `app.js` 缓存逻辑（2 行代码修改）
- 解决 NBT 物品图标缓存失效问题
- 不影响后端，风险低
- 可立即部署测试

### 优先级 2 - 中期修复（渲染失败处理）

**方案 2A 或 2B**: 修复渲染失败和无限重试问题
- 解决翻译缺失的根本原因
- 需要详细测试
- 参考 ISSUE-infinite-retry.md 的详细分析

---

## 相关文件

- `src/main/resources/web/app.js:220-226` - 前端缓存逻辑
- `src/main/resources/web/app.js:304-324` - 图标懒加载
- `src/main/java/com/kokoda/ae2web/web/dto/ItemStackDTO.java:17-30` - 后端 DTO 构造
- `src/main/java/com/kokoda/ae2web/web/handlers/InventoryHandler.java:173-182` - 翻译获取
- `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128` - 图标上传
- `docs/ISSUE-infinite-retry.md` - 渲染失败和无限重试问题分析

---

## 调试建议

### 验证缓存键问题

1. 打开浏览器开发者工具 → Console
2. 执行: `console.log(loadedIcons)`
3. 检查缓存的键是否为 `iconKey` 格式（如 `"ae2:foo@123456"`）

### 验证图标是否存在

1. 检查缺失图标的物品，获取其 `iconKey`
2. 浏览器访问: `http://localhost:8080/api/prerender/image?id=<iconKey>`
3. 如果返回 404 → 图标确实未渲染
4. 如果返回图标 → 前端缓存逻辑有问题

### 验证翻译是否上传

1. 查看服务器端预渲染存储
2. 检查翻译数据是否包含对应的 `itemId`
3. 如果缺失 → 渲染时未上传翻译（参考 ISSUE-infinite-retry.md）
