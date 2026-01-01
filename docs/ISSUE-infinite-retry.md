# Issue: Infinite Retry Loop for Failed Item Rendering

## 问题描述

客户端在所有物品渲染完成后，仍然每 5 秒重试渲染 1 个物品，导致无限循环。

## 日志分析

### 时间线

1. **07:25:52** - 服务器状态显示所有物品已渲染完成：
   ```
   [CHAT] [AE2Web]   Total items: 20920
   [CHAT] [AE2Web]   Rendered: 20932
   [CHAT] [AE2Web]   Missing: 0
   ```

2. **07:26:59 开始** - 仍在重试渲染 1 个物品：
   ```
   [AE2Web-RetryCheck/WARN]: RETRY TRIGGERED: Found 1 items still missing after render, initiating retry...
   [Render thread/INFO]: Claiming 1 items for retry render...
   [AE2Web-PrerenderClient/INFO]: Retry claim result: 1 claimed, 0 already rendered, 0 locked by others
   [Render thread/INFO]: Render queue exhausted - completed: 1/1, queue empty
   ```

3. **持续循环** - 每 5 秒重复上述过程，无限循环

### 关键观察

- ✅ 客户端报告渲染完成：`completed: 1/1, queue empty`
- ❌ 服务器端仍有 1 个物品在 missing 列表中
- 🔄 每次重试都成功 claim，但渲染后物品仍在 missing 列表
- ⚠️ 没有看到任何渲染失败的错误日志

## 代码分析

### 1. 渲染失败处理

**文件**: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:210-218`

```java
} else {
    // Try rendering as fluid
    FluidStack fluidStack = ItemIconRenderer.getFluidStack(task.itemId);
    if (fluidStack != null && !fluidStack.isEmpty()) {
        byte[] png = renderer.renderFluidToPng(fluidStack);
        String translatedName = fluidStack.getDisplayName().getString();
        task.onComplete.accept(new RenderResult(png, translatedName));
    } else {
        AE2WebMod.LOGGER.debug("Failed to render {}: not found as item or fluid", task.itemId);
        task.onComplete.accept(null);  // ⚠️ 传递 null，不上传任何内容
    }
}
```

**问题**:
- 使用 `debug` 级别记录，用户看不到失败信息
- 传递 `null` 给 `onComplete`，导致不上传任何内容

### 2. 上传逻辑

**文件**: `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128`

```java
taskQueue.add(new RenderTask(itemId, iconKey, item.nbt(), result -> {
    if (result != null && result.png() != null) {
        // Upload with iconKey (includes NBT hash)
        PrerenderHttpClient.getInstance().uploadIcon(
                serverUrl, iconKey, result.png(), result.translatedName());
    }
    // ⚠️ 如果 result == null，什么都不做，物品留在服务器 missing 列表
}));
```

**问题**:
- 当 `result == null` 时，不执行任何操作
- 服务器端不知道这个物品渲染失败
- 物品永远留在 missing 列表中

### 3. 重试机制

**文件**: `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-260`

```java
private static void scheduleRetryCheck(String targetServerUrl) {
    AE2WebMod.LOGGER.info("Scheduling retry check in 5 seconds for server: {}", targetServerUrl);

    new Thread(() -> {
        try {
            Thread.sleep(5000);  // 等待 5 秒

            PrerenderHttpClient client = PrerenderHttpClient.getInstance();
            client.fetchMissingIcons(targetServerUrl, 100)
                .thenAccept(missingItems -> {
                    if (!missingItems.isEmpty()) {
                        // ⚠️ 无限重试，没有最大次数限制
                        AE2WebMod.LOGGER.warn("RETRY TRIGGERED: Found {} items still missing...",
                                missingItems.size());
                        // 重新 claim 并渲染
                        ...
                    }
                });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }, "AE2Web-RetryCheck").start();
}
```

**问题**:
- 没有跟踪哪些物品已经重试过多次
- 没有最大重试次数限制
- 对于永远无法渲染的物品，会无限循环

## 根本原因

1. **渲染失败但无反馈**: 某个物品（可能 NBT 数据损坏或物品不存在）无法渲染，但失败只记录在 debug 日志中
2. **没有失败通知**: 渲染失败时不通知服务器，物品永远留在 missing 列表
3. **无限重试**: 重试机制没有失败跟踪和次数限制，导致无限循环

## 可能的失败原因

1. **物品不存在**: 物品 ID 在当前游戏版本中不存在
2. **NBT 数据损坏**: 压缩的 NBT 数据无法反序列化
3. **渲染异常**: 渲染过程中抛出异常（被捕获，记录为 warn）
4. **资源缺失**: 物品的材质或模型文件缺失

## 修复方案

### 方案 1: 提升日志级别（最简单）

将渲染失败日志从 `debug` 改为 `warn`，让用户知道哪个物品有问题。

**优点**:
- 实现简单，只改一行代码
- 用户可以看到问题物品

**缺点**:
- 不解决无限重试问题
- 用户仍会被重试日志刷屏

### 方案 2: 添加失败跟踪和重试限制（推荐）

1. 跟踪每个物品的渲染失败次数
2. 超过阈值（如 3 次）后停止重试该物品
3. 提升失败日志级别为 `warn`
4. 可选：上传失败标记给服务器

**优点**:
- 彻底解决无限重试问题
- 保护性能，避免无效重试
- 用户能清楚看到问题

**缺点**:
- 需要添加状态跟踪代码
- 稍微复杂一些

### 方案 3: 服务器端添加失败标记 API（最彻底）

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

## 实现优先级

1. **立即**: 方案 1（提升日志级别）- 让问题可见
2. **短期**: 方案 2（失败跟踪）- 解决无限重试
3. **长期**: 方案 3（服务器标记）- 完善整体架构

## 调试建议

如果要定位具体是哪个物品导致问题：

1. 启用 DEBUG 日志级别
2. 查找 `"Failed to render"` 日志
3. 检查该物品的 ID 和 NBT 数据
4. 在创造模式中测试该物品是否能正常显示

## 相关代码位置

- `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:210-218` - 渲染失败处理
- `src/main/java/com/kokoda/ae2web/client/render/RenderScheduler.java:122-128` - 上传逻辑
- `src/main/java/com/kokoda/ae2web/client/PrerenderTrigger.java:227-260` - 重试机制
- `src/main/java/com/kokoda/ae2web/client/render/ItemIconRenderer.java` - 物品渲染实现
