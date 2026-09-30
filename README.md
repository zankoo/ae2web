# AE2 Web Terminal

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Forge-47.2.0-orange)
![AE2](https://img.shields.io/badge/AE2-15.2.1-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**通过浏览器访问你的 AE2 ME 网络**

[功能特性](#功能特性) • [安装](#安装) • [使用方法](#使用方法) • [配置](#配置) • [API 文档](#api-文档) • [开发](#开发)

</div>

---

## 简介

AE2 Web Terminal 是一个 Minecraft Forge 模组，为 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 添加了一个 Web 终端方块。该方块连接到 ME 网络，并通过内嵌的 HTTP 服务器暴露网络物品库存，允许玩家从任何设备的浏览器中浏览物品、搜索和提交合成请求。

## 功能特性

### 核心功能

- **实时物品库存** - 通过 Server-Sent Events (SSE) 实时同步 ME 网络物品变化
- **物品搜索** - 支持中文拼音搜索、物品 ID 搜索
- **合成请求** - 直接从浏览器提交自动合成请求
- **CPU 监控** - 实时查看合成 CPU 状态和进度
- **多终端支持** - 支持多个 Web 终端同时连接

### Web UI 功能

- **现代化界面** - 使用 SvelteKit + Tailwind CSS + DaisyUI 构建的响应式 UI
- **物品图标预渲染** - 客户端渲染物品图标并上传至服务器，支持 NBT 物品
- **物品分类与分组** - 自定义白名单/黑名单过滤，支持正则表达式分类
- **生产统计** - 追踪物品产量/消耗量变化趋势
- **Dashboard 仪表板** - 可自定义的小组件布局，包括：
  - 网络概览 (Network Overview)
  - 生产/消耗排行榜 (Top Items)
  - 物品追踪器 (Tracked Items)
  - 历史趋势图表 (History Trend)

### 安全特性

- **Token 认证** - 可选的 API 访问令牌认证
- **玩家独立 Token** - 每个玩家可以生成独立的访问令牌
- **可配置绑定地址** - 支持限制只允许本地访问

## 安装

### 要求

- Minecraft 1.20.1
- Minecraft Forge 47.2.0+
- Applied Energistics 2 15.2.1+
- Java 17+

### 安装步骤

1. 下载最新版本的 `ae2web-x.x.x.jar`
2. 将 JAR 文件放入 `.minecraft/mods/` 目录
3. 启动 Minecraft

## 使用方法

### 放置 Web 终端

1. 合成 Web Terminal 方块
2. 将其放置在 ME 网络中（需要连接到 ME 控制器或有能量的网络）
3. 方块需要一个频道才能正常工作

### 访问 Web 界面

1. 服务器启动后，HTTP 服务器会自动运行（默认端口 8080）
2. 在浏览器中访问 `http://<服务器IP>:8080`
3. 如果启用了认证，输入访问令牌

### 获取访问令牌

在游戏中使用命令：

```
/ae2web token
```

这将生成一个玩家专属的访问令牌并复制到剪贴板。

### 重命名终端

使用扳手右键点击 Web Terminal 方块可以打开重命名界面。

## 配置

配置文件位于 `config/ae2web-server.toml`

### 服务器配置

```toml
[server]
# HTTP 服务器端口 (1024-65535)
httpPort = 8080

# 绑定地址 (0.0.0.0 = 所有接口, 127.0.0.1 = 仅本地)
bindAddress = "0.0.0.0"

# 是否启用 Token 认证
enableAuth = false

# 共享认证令牌（请设置为安全的随机字符串）
# 保持默认值或留空时共享令牌不生效，玩家需使用 /ae2web token 获取的个人令牌
authToken = "change-me-to-a-secure-token"

# 额外允许跨域访问 API 的浏览器来源，例如 ["https://example.com"]
# 同源请求始终允许；"*" 表示允许任意来源（不推荐）
allowedOrigins = []
```

> 服务器会拒绝来自其他网站的浏览器请求（按 `Origin` 与 `Host` / `X-Forwarded-Host` 比对），
> 防止恶意网页借玩家的浏览器调用 API。使用反向代理时请转发 `Host` 或 `X-Forwarded-Host`，或把域名加入 `allowedOrigins`。
>
> 客户端的预渲染会在进服时自动从服务器获取 HTTP 端口、预渲染开关和玩家个人令牌，无需在客户端配置。

### 预渲染配置

```toml
[prerender]
# 启用客户端物品图标预渲染
enabled = true

# PNG 图标最大上传大小 (字节)
maxUploadSize = 65536

# 内存缓存的图标数量
cacheSize = 1024

# 玩家加入时自动渲染的最大物品数 (0 = 无限制)
autoMaxItems = 0

# NBT 变体图标的 GC TTL (秒, 0 = 禁用)
gcTtlSeconds = 604800

# 预渲染维护间隔 (秒, 0 = 禁用)
gcIntervalSeconds = 300
```

## API 文档

### 端点概览

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/status` | GET | 服务器状态和连接信息 |
| `/api/terminals` | GET | 已连接终端列表 |
| `/api/items` | GET | 分页物品库存，支持搜索/过滤 |
| `/api/events/inventory` | GET | SSE 实时库存更新流 |
| `/api/craft` | POST | 提交合成请求 |
| `/api/cpus` | GET | 合成 CPU 状态列表 |
| `/api/cpus/process` | GET | 详细 CPU 合成进度 |
| `/api/user/inventoryConfig` | GET/POST | 用户物品视图配置 |
| `/api/user/dashboardConfig` | GET/POST | 用户仪表板配置 |
| `/api/prerender/*` | Various | 图标预渲染相关 |

### 认证

如果启用了认证，需要在请求中携带 Token：

**Header 方式：**
```
Authorization: Bearer <token>
```

**Query 参数方式：**
```
?token=<token>
```

### 示例请求

#### 获取物品列表

```bash
curl "http://localhost:8080/api/items?terminalId=xxx&page=0&pageSize=50&search=iron"
```

**响应：**
```json
{
  "items": [
    {
      "uniqueId": "minecraft:iron_ingot",
      "id": "minecraft:iron_ingot",
      "type": "item",
      "name": "Iron Ingot",
      "amount": 12345,
      "craftable": true
    }
  ],
  "page": 0,
  "pageSize": 50,
  "total": 1
}
```

#### 提交合成请求

```bash
curl -X POST "http://localhost:8080/api/craft" \
  -H "Content-Type: application/json" \
  -d '{
    "terminalId": "xxx",
    "id": "minecraft:iron_ingot",
    "type": "item",
    "amount": 64
  }'
```

#### SSE 库存流

```bash
curl "http://localhost:8080/api/events/inventory?terminalId=xxx"
```

**事件类型：**
- `hello` - 连接确认
- `snapshot` - 完整库存快照
- `delta` - 增量更新（添加/删除/修改）

## 开发

### 环境要求

- Java 17 (JDK)
- Node.js 18+
- Gradle 8.x

### 构建项目

```bash
# 构建完整 mod JAR
./gradlew build

# 仅运行后端测试
./gradlew test

# 仅运行前端测试
./gradlew webuiTest
```

### 开发模式

**后端开发：**
```bash
# 启动 Minecraft 客户端（带热重载）
./gradlew runClient

# 启动 Minecraft 服务器
./gradlew runServer
```

**前端开发：**
```bash
cd webui

# 安装依赖
npm install

# 启动开发服务器 (HMR)
npm run dev

# 类型检查
npm run check

# 代码格式化
npm run format
```

### 项目结构

```
ae2web/
├── src/
│   ├── main/java/com/kokoda/ae2web/
│   │   ├── AE2WebMod.java          # 模组入口
│   │   ├── block/                   # 方块定义
│   │   ├── blockentity/             # 方块实体 (ME 网络连接)
│   │   ├── config/                  # 配置系统
│   │   ├── web/                     # HTTP 服务器
│   │   │   ├── WebServer.java       # Netty HTTP 服务器
│   │   │   ├── handlers/            # API 处理器
│   │   │   ├── auth/                # 认证系统
│   │   │   └── prerender/           # 图标预渲染
│   │   └── client/                  # 客户端代码 (图标渲染)
│   └── test/                        # JUnit 测试
├── webui/                           # SvelteKit 前端
│   ├── src/
│   │   ├── routes/                  # 页面路由
│   │   └── lib/
│   │       ├── api/                 # API 客户端
│   │       ├── components/          # UI 组件
│   │       ├── stores/              # Svelte stores
│   │       └── utils/               # 工具函数
│   └── tests/                       # Vitest 测试
└── docs/                            # 文档和计划
```

### 技术栈

**后端：**
- Minecraft Forge 47.2.0
- Applied Energistics 2 15.2.1
- Netty HTTP (内嵌于 Minecraft)
- Gson 2.10.1

**前端：**
- SvelteKit 2.x
- Svelte 5.x
- Tailwind CSS 4.x
- DaisyUI 5.x
- TypeScript
- Vite

## 许可证

MIT License

## 作者

Kokoda

## 致谢

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) - AE2 模组
- [Minecraft Forge](https://minecraftforge.net/) - 模组加载器
