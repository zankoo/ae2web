# 产量统计功能规划

> 状态: 待实现
> 优先级: 中
> 预计复杂度: 中等

## 功能目标

1. **实时产量显示** - 根据库存变化自动计算每个物品的产量/消耗量
2. **历史趋势图表** - 漂亮的可视化图表展示物品数量变化
3. **跨会话持久化** - 关闭浏览器后历史数据不丢失

## 架构设计：混合方案

```
┌────────────────────────────────────────────────────────┐
│                      前端 (实时)                         │
│  ├── 利用现有3秒轮询数据                                  │
│  ├── 本地计算瞬时产量: (当前量-上次量) / 间隔秒数           │
│  ├── localStorage存储短期数据 (最近10分钟)                │
│  └── Chart.js渲染实时图表                                │
├────────────────────────────────────────────────────────┤
│                      后端 (长期)                         │
│  ├── InventoryHistoryService: 30秒采样一次               │
│  ├── 内存存储: LinkedHashMap (LRU, 最大24小时)           │
│  ├── 可选: 文件持久化 (服务器重启后恢复)                   │
│  └── API: /api/history?itemId=xxx&range=1h              │
└────────────────────────────────────────────────────────┘
```

## 后端实现

### 1. 新建 InventoryHistoryService.java

```java
package com.kokoda.ae2web.web.history;

public class InventoryHistoryService {
    private static final int SAMPLE_INTERVAL_SECONDS = 30;
    private static final int MAX_HISTORY_HOURS = 24;

    // itemId -> List<DataPoint>
    private final Map<String, LinkedList<DataPoint>> history = new ConcurrentHashMap<>();

    public record DataPoint(long timestamp, long amount) {}

    // 定时任务: 每30秒从所有terminal采样
    public void sampleInventory(WebTerminalBlockEntity terminal) {
        Map<AEKey, Long> inventory = terminal.getNetworkInventory(KeyFilter.ALL);
        long now = System.currentTimeMillis();

        for (var entry : inventory.entrySet()) {
            String itemId = entry.getKey().getId().toString();
            long amount = entry.getValue();

            history.computeIfAbsent(itemId, k -> new LinkedList<>())
                   .addLast(new DataPoint(now, amount));

            // 清理超过24小时的数据
            pruneOldData(itemId);
        }
    }

    public List<DataPoint> getHistory(String itemId, long fromTimestamp) {
        // 返回指定时间范围的历史数据
    }
}
```

### 2. 新建 HistoryHandler.java

```java
// GET /api/history?itemId=minecraft:diamond&range=1h
public static void handleHistory(ChannelHandlerContext ctx, FullHttpRequest request,
                                  Map<String, List<String>> params, HttpHeaders headers) {
    String itemId = params.get("itemId").get(0);
    String range = params.getOrDefault("range", List.of("1h")).get(0);

    long fromTimestamp = parseRange(range); // 1h -> now - 3600000
    List<DataPoint> data = historyService.getHistory(itemId, fromTimestamp);

    sendJsonResponse(ctx, HttpResponseStatus.OK, GSON.toJson(data), headers);
}
```

### 3. 定时采样调度

```java
// 在 AE2WebMod.java 或 WebServer.java 中
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(() -> {
    for (WebTerminalBlockEntity terminal : WebServer.getInstance().getTerminals()) {
        historyService.sampleInventory(terminal);
    }
}, 0, 30, TimeUnit.SECONDS);
```

## 前端实现

### 1. 添加 Chart.js (CDN)

```html
<!-- index.html -->
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
```

### 2. 本地实时数据追踪 (app.js)

```javascript
// 新增: 产量追踪
const productionHistory = new Map(); // itemId -> {timestamps: [], amounts: [], rate: 0}
const HISTORY_RETENTION_MS = 10 * 60 * 1000; // 保留10分钟

function trackProduction(items) {
    const now = Date.now();

    for (const item of items) {
        let history = productionHistory.get(item.id);
        if (!history) {
            history = { timestamps: [], amounts: [], rate: 0 };
            productionHistory.set(item.id, history);
        }

        history.timestamps.push(now);
        history.amounts.push(item.amount);

        // 计算产量 (每秒)
        if (history.amounts.length >= 2) {
            const timeDelta = (now - history.timestamps[history.timestamps.length - 2]) / 1000;
            const amountDelta = item.amount - history.amounts[history.amounts.length - 2];
            history.rate = amountDelta / timeDelta;
        }

        // 清理旧数据
        while (history.timestamps.length > 0 &&
               now - history.timestamps[0] > HISTORY_RETENTION_MS) {
            history.timestamps.shift();
            history.amounts.shift();
        }
    }

    // 持久化到localStorage
    saveToLocalStorage();
}

// 在 renderInventory() 中调用
function renderInventory(data) {
    trackProduction(data.items);
    // ... 现有渲染逻辑
}
```

### 3. 产量显示UI

```javascript
// 在物品槽位上显示产量
function formatRate(rate) {
    if (rate === 0) return '';
    const sign = rate > 0 ? '+' : '';
    if (Math.abs(rate) >= 1000) return sign + (rate/1000).toFixed(1) + 'K/s';
    if (Math.abs(rate) >= 1) return sign + rate.toFixed(1) + '/s';
    return sign + (rate * 60).toFixed(1) + '/m';
}

// 渲染时添加产量标签
const history = productionHistory.get(item.id);
const rateDisplay = history ? formatRate(history.rate) : '';
const rateClass = history?.rate > 0 ? 'rate-positive' : (history?.rate < 0 ? 'rate-negative' : '');

return `
<div class="item-slot...">
    <img class="item-icon" ...>
    <span class="item-name">${escapeHtml(displayName)}</span>
    <span class="item-amount">${formatAmount(item.amount)}</span>
    ${rateDisplay ? `<span class="item-rate ${rateClass}">${rateDisplay}</span>` : ''}
</div>`;
```

### 4. 图表模态框

```javascript
function openChartModal(itemId, itemName) {
    // 获取本地短期数据
    const localHistory = productionHistory.get(itemId);

    // 获取后端长期数据
    const serverHistory = await apiRequest(`/api/history?itemId=${encodeURIComponent(itemId)}&range=1h`);

    // 合并数据并渲染图表
    renderChart(itemId, itemName, localHistory, serverHistory);
}

function renderChart(itemId, itemName, localData, serverData) {
    const ctx = document.getElementById('chart-canvas').getContext('2d');
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: [...serverData.map(d => formatTime(d.timestamp)), ...localData.timestamps.map(formatTime)],
            datasets: [{
                label: itemName,
                data: [...serverData.map(d => d.amount), ...localData.amounts],
                borderColor: '#00d4aa',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            scales: {
                x: { title: { display: true, text: '时间' } },
                y: { title: { display: true, text: '数量' } }
            }
        }
    });
}
```

### 5. CSS样式

```css
/* 产量标签 */
.item-rate {
    position: absolute;
    bottom: 12px;
    left: 50%;
    transform: translateX(-50%);
    font-family: 'Share Tech Mono', monospace;
    font-size: 8px;
    padding: 1px 3px;
    border-radius: 3px;
    background: rgba(0,0,0,0.6);
}

.item-rate.rate-positive {
    color: #10b981;
}

.item-rate.rate-negative {
    color: #ef4444;
}

/* 图表模态框 */
.chart-modal {
    /* 类似现有craft-modal样式 */
}

.chart-container {
    width: 100%;
    height: 300px;
}
```

## API设计

### GET /api/history

**请求参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| itemId | string | 是 | 物品ID, 如 `minecraft:diamond` |
| range | string | 否 | 时间范围: `10m`, `1h`, `6h`, `24h`, 默认 `1h` |

**响应:**
```json
{
    "itemId": "minecraft:diamond",
    "dataPoints": [
        {"timestamp": 1703980800000, "amount": 1000},
        {"timestamp": 1703980830000, "amount": 1050},
        {"timestamp": 1703980860000, "amount": 1120}
    ],
    "calculatedRate": 4.0  // 计算的平均产量/秒
}
```

## 文件修改清单

### 新建文件
- `src/main/java/com/kokoda/ae2web/web/history/InventoryHistoryService.java`
- `src/main/java/com/kokoda/ae2web/web/handlers/HistoryHandler.java`

### 修改文件
- `src/main/java/com/kokoda/ae2web/AE2WebMod.java` - 添加定时采样调度
- `src/main/java/com/kokoda/ae2web/web/handlers/ApiHandler.java` - 添加 /api/history 路由
- `src/main/resources/web/index.html` - 添加Chart.js, 图表模态框, CSS
- `src/main/resources/web/app.js` - 添加产量追踪逻辑, 图表渲染

## 实现阶段

### Phase 1: 前端实时产量 (最小可用)
- [ ] 本地产量计算和显示
- [ ] 物品槽位显示 +X/s 或 -X/s
- [ ] localStorage持久化

### Phase 2: 图表可视化
- [ ] 添加Chart.js
- [ ] 图表模态框UI
- [ ] 本地数据图表渲染

### Phase 3: 后端历史存储
- [ ] InventoryHistoryService
- [ ] /api/history 端点
- [ ] 定时采样调度

### Phase 4: 完整集成
- [ ] 前后端数据合并
- [ ] 长期趋势图表
- [ ] 可选: 文件持久化

## 注意事项

1. **内存管理** - 后端需要限制历史数据大小,使用LRU淘汰
2. **性能** - 大量物品时采样可能耗时,考虑异步处理
3. **精度** - 采样间隔决定精度,30秒是平衡点
4. **UI空间** - 产量标签不能遮挡物品名称
