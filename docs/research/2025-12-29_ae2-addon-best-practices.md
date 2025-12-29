# AE2 1.20.1 附属模组良好实践调研报告

**日期**: 2025-12-29
**调研目标**: AE2 (Applied Energistics 2) 1.20.1 版本及其附属模组的代码实践

---

## Core Finding

AE2 附属模组应继承 `AENetworkedBlockEntity` 而非直接实现 `IGridNodeListener`，使用 `BlockEntityNodeListener.INSTANCE` 作为默认监听器，并通过 `grid.getStorageService()` / `grid.getCraftingService()` 等快捷方法访问服务。

---

## 1. BlockEntity 与 Grid 的连接模式

### 官方推荐模式

**继承 `AENetworkedBlockEntity`** (来自 `appeng.blockentity.grid`)：

```java
// 来源: AppliedEnergistics/Applied-Energistics-2 - InterfaceBlockEntity.java
public class InterfaceBlockEntity extends AENetworkedBlockEntity
        implements IPriorityHost, IUpgradeableObject, IConfigurableObject {

    private static final IGridNodeListener<InterfaceBlockEntity> NODE_LISTENER =
        new BlockEntityNodeListener<>() {
            @Override
            public void onGridChanged(InterfaceBlockEntity nodeOwner, IGridNode node) {
                nodeOwner.logic.gridChanged();
            }
        };

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }
}
```

**核心要点**:
- `AENetworkedBlockEntity` 已内置 GridNode 生命周期管理、NBT 序列化
- 使用 `BlockEntityNodeListener.INSTANCE` 作为基础监听器
- 通过 `createMainNode()` 方法覆写自定义节点配置

### 附属模组实践

**GregTech-Modern (1.20.1)**:
```java
// 来源: GregTechCEu/GregTech-Modern - GridNodeHostTrait.java
this.proxy = GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE)
        .setInWorldNode(true)
        .setVisualRepresentation(machine.getDefinition().getItem());
```

**AE2CCBridge**:
```java
// 来源: TheMrMilchmann/AE2CCBridge - AE2CCAdapterBlockEntity.java
public final class AE2CCAdapterBlockEntity extends AENetworkBlockEntity
        implements ICraftingRequester, IGridConnectedBlockEntity, IGridTickable {
    // 继承自 AENetworkBlockEntity，自动获得 Grid 连接能力
}
```

### 状态变化响应

使用 `onMainNodeStateChanged` 回调而非手动 tick 检查：

```java
// 来源: AppliedEnergistics/Applied-Energistics-2 - WirelessAccessPointBlockEntity.java
@Override
public void onMainNodeStateChanged(IGridNodeListener.State reason) {
    if (reason != IGridNodeListener.State.GRID_BOOT) {
        this.markForUpdate();  // 仅在非启动阶段更新
    }
}
```

---

## 2. 存储服务访问方式

### 快捷方法 vs getService()

```java
// 推荐写法 (IGrid 接口提供的快捷方法)
IStorageService storageService = grid.getStorageService();
ICraftingService craftingService = grid.getCraftingService();
IEnergyService energyService = grid.getEnergyService();

// 避免使用
IStorageService storageService = grid.getService(IStorageService.class);
```

### 获取库存

```java
// 来源: AppliedEnergistics/Applied-Energistics-2 - PlotTestHelper.java
var storage = grid.getStorageService().getInventory();
var stacks = storage.getAvailableStacks();
var count = stacks.get(AEItemKey.of(Items.STICK));
```

### 缓存 vs 实时数据

```java
// 对于实时性要求不高的场景，使用缓存（每 tick 更新一次）
KeyCounter cached = grid.getStorageService().getCachedInventory();

// 对于需要精确数据的场景（如合成计算）
KeyCounter realtime = grid.getStorageService().getInventory().getAvailableStacks();
```

来源: `IStorageService.java` 注释:
> "The cache is updated at most once per tick. Changes to network inventory will not be reflected by the cache until the next tick."

### 插入/提取物品

```java
// 来源: GregTechCEu/GregTech-Modern - MEOutputBusPartMachine.java
grid.getStorageService().getInventory().insert(
    entry.getKey(),           // AEKey
    entry.getLongValue(),     // amount
    Actionable.MODULATE,      // 实际执行
    actionSource              // IActionSource
);
```

---

## 3. 合成任务提交和 CPU 状态查询

### 获取 Crafting CPUs

```java
// 来源: AppliedEnergistics/Applied-Energistics-2 - MEStorageMenu.java
int activeJobs = 0;
for (var cpu : grid.getCraftingService().getCpus()) {
    if (cpu.isBusy()) {
        activeJobs++;
    }
}

// 来源: TheMrMilchmann/AE2CCBridge - AE2CCAdapterBlockEntity.java
Set<ICraftingCPU> cpus = grid.getCraftingService().getCpus();
cpus.stream().map(cpu -> {
    cpu.isBusy();               // 是否忙碌
    cpu.getAvailableStorage();  // 可用存储
    cpu.getCoProcessors();      // 协处理器数量
    cpu.getSelectionMode();     // ANY, MACHINE_ONLY, PLAYER_ONLY
    cpu.getJobStatus();         // 当前任务状态
    cpu.cancelJob();            // 取消任务
});
```

### 提交合成任务

```java
// 来源: AppliedEnergistics/Applied-Energistics-2 - CraftConfirmMenu.java & AE2CCBridge
ICraftingService craftingService = grid.getCraftingService();

// 1. 开始计算合成计划
Future<ICraftingPlan> future = craftingService.beginCraftingCalculation(
    level,
    () -> actionSource,           // ICraftingSimulationRequester
    AEItemKey.of(Items.STONE),    // AEKey - 要合成的物品
    64,                           // 数量
    CalculationStrategy.REPORT_MISSING_ITEMS
);

// 2. 等待计算完成 (异步)
ICraftingPlan plan = future.get();  // 注意：不要在主线程阻塞等待

// 3. 提交任务
IActionSource actionSource = IActionSource.ofMachine(this);
ICraftingSubmitResult result = craftingService.submitJob(
    plan,
    this,              // ICraftingRequester (可选，用于接收完成通知)
    targetCpu,         // 指定 CPU (可选，null 表示自动选择)
    true,              // prioritizePower
    actionSource
);

if (result.successful()) {
    ICraftingLink link = result.link();  // 用于跟踪任务
} else {
    // 处理错误: result.errorCode()
    // INCOMPLETE_PLAN, NO_CPU_FOUND, CPU_BUSY, etc.
}
```

### IActionSource 创建

```java
// 来源: GregTechCEu/GregTech-Modern
this.actionSource = IActionSource.ofMachine(nodeHolder.getMainNode()::getNode);

// 来源: TheMrMilchmann/AE2CCBridge
IActionSource actionSource = IActionSource.ofMachine(this);
```

---

## 4. 类似 Web 集成项目参考

### kuba6000/AE2-Web-Integration

**特点**:
- 服务器端模组，无游戏内方块
- 通过无线接入点 (`IWirelessAccessPoint`) 识别网络
- 多用户支持，每玩家独立账户
- PBKDF2WithHmacSHA1 密码哈希，有时效令牌认证

**网络识别机制**:
- 新版本: 通过无线接入点识别
- 旧版本: 通过安全终端和生物卡识别

### TheMrMilchmann/AE2CCBridge

**架构**:
```java
public final class AE2CCAdapterBlockEntity extends AENetworkBlockEntity
        implements ICraftingRequester, IGridConnectedBlockEntity, IGridTickable {

    // 实现 ICraftingRequester 以接收合成完成通知
    // 实现 IGridTickable 以定期处理任务
}
```

**功能暴露**:
- `getAvailableStacks()` - 获取所有可用物品
- `getCraftingCPUs()` - 获取 CPU 状态
- `scheduleCrafting(type, id, amount)` - 调度合成任务
- 事件通知: `ae2cc:crafting_started`, `ae2cc:crafting_done`, `ae2cc:crafting_cancelled`

---

## Recommended Approach

### 对当前项目的改进建议

| 当前实现 | 建议改进 |
|---------|---------|
| 直接实现 `IGridNodeListener` | 继承 `AENetworkedBlockEntity` |
| `grid.getService(IStorageService.class)` | `grid.getStorageService()` |
| 手动 tick 检查连接状态 | 使用 `onMainNodeStateChanged` 回调 |
| 自定义库存缓存逻辑 | 考虑使用 `getCachedInventory()` |
| TODO: 合成请求 | 参考 `beginCraftingCalculation` + `submitJob` |
| TODO: CPU 状态 | 参考 `getCraftingService().getCpus()` |

### 重构 WebTerminalBlockEntity 示例

```java
public class WebTerminalBlockEntity extends AENetworkedBlockEntity
        implements ICraftingRequester {

    private static final IGridNodeListener<WebTerminalBlockEntity> NODE_LISTENER =
        new BlockEntityNodeListener<>() {
            @Override
            public void onGridChanged(WebTerminalBlockEntity owner, IGridNode node) {
                // 网络变化时的处理
                owner.onNetworkChanged();
            }
        };

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModBlocks.WEB_TERMINAL.get());
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason == IGridNodeListener.State.POWER ||
            reason == IGridNodeListener.State.CHANNEL) {
            updateConnectionStatus();
        }
    }

    public Map<AEKey, Long> getNetworkInventory() {
        var grid = getMainNode().getGrid();
        if (grid == null) return Collections.emptyMap();

        // 使用缓存库存，性能更好
        return grid.getStorageService().getCachedInventory()
                .stream()
                .filter(e -> e.getKey() instanceof AEItemKey)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getLongValue));
    }

    public Set<ICraftingCPU> getCraftingCPUs() {
        var grid = getMainNode().getGrid();
        return grid != null ? grid.getCraftingService().getCpus() : Set.of();
    }
}
```

---

## Watch Out For

### API 可用性警告

**重要发现**：以下类是 AE2 **内部实现类**，不对 addon 开发者公开：

| 类名 | 实际包路径 | 状态 |
|-----|-----------|------|
| `AENetworkedBlockEntity` | `appeng.blockentity.grid` | ❌ 内部类 |
| `BlockEntityNodeListener` | `appeng.me.helpers` | ❌ 内部类 |
| `AEBaseBlockEntity` | `appeng.blockentity` | ❌ 内部类 |

**Addon 开发者只能使用 `appeng.api.*` 包中的类**。正确的模式是：
```java
// 正确：addon 开发者可用的模式
public class MyBlockEntity extends BlockEntity
        implements IGridNodeListener<MyBlockEntity> {

    private IManagedGridNode mainNode;

    private void initializeGridNode() {
        mainNode = GridHelper.createManagedNode(this, this)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
        mainNode.create(level, worldPosition);
    }
}
```

### Anti-patterns

1. **不要在主线程阻塞等待 `Future<ICraftingPlan>`**
   - 合成计算是异步的，使用回调或定期检查

2. **不要频繁调用 `getAvailableStacks()`**
   - 对于非关键场景使用 `getCachedInventory()`

3. **不要忘记处理节点生命周期**
   - 使用 `AENetworkedBlockEntity` 自动管理
   - 或确保调用 `mainNode.create()` 和 `mainNode.destroy()`

4. **不要忽略 `IGridNodeListener.State.GRID_BOOT`**
   - 网络启动时会触发，通常不需要更新 UI

### 版本兼容性

- AE2 1.20.1 使用统一存储 (items + fluids)
- `AEKey` 替代了旧版本的 `IStack` 变体
- 使用 `GenericStack` 表示数量

---

## 参考项目

| 项目 | 功能 | GitHub |
|-----|------|--------|
| Applied-Mekanistics | 官方 Mekanism 集成 | AppliedEnergistics/Applied-Mekanistics |
| ExtendedAE | 36槽 Pattern Provider | GlodBlock/ExtendedAE |
| AE2CCBridge | ComputerCraft 集成 | TheMrMilchmann/AE2CCBridge |
| AE2-Web-Integration | Web 界面集成 | kuba6000/AE2-Web-Integration |
| AdvancedAE | 高级功能扩展 | pedroksl/AdvancedAE |
| lazierae2 | 自动合成请求器 | AlmostReliable/lazierae2-forge |
| GregTech-Modern | GTCEu AE2 集成 | GregTechCEu/GregTech-Modern |
| expandedae | 扩展 AE2 功能 | ko-lja/expandedae |

---

## 调研方法

- **GitHub 代码搜索**: `mcp__grep__searchGitHub`
- **Web 搜索**: `mcp__exa__web_search_exa`
- **代码上下文**: `mcp__exa__get_code_context_exa`
- **主要仓库**: AppliedEnergistics/Applied-Energistics-2 (main branch)
