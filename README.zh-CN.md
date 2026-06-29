# Remote Inventory Next

[English](README.md) | **中文**

客户端-服务端 Fabric 模组，提供远程容器物品管理功能。作为 **Litematica Printer** 等建造辅助模组的后端使用。

客户端发送物品交换请求 → 服务端验证距离、容器状态、物品匹配 → 执行原子化的取物+还物操作，附带背包增量同步。支持容器扫描实现高效批量取物，客户端缓存支持磁盘持久化。

## 功能

- **物品交换** (`exchange`) — 原子化双向物品操作：从容器取物 + 还物到容器，单个数据包完成，附带背包增量同步消除竞态条件
- **容器扫描** (`scan_container`) — 一次请求扫描整个容器的所有非空槽位
- **客户端缓存** — 扫描结果本地缓存（30秒 TTL），断开连接时持久化到磁盘。按物品类型和容器位置双重索引，O(1) 查询
- **FIFO 归还追踪** — 记录取物来源，背包满时按 FIFO 顺序自动归还
- **可配置距离** — `/remoteinv distance <1-256>` 设置最大交互距离
- **白名单 / 黑名单** — `/remoteinv whitelist|blacklist add|remove|list|clear <方块>`

## 支持的版本

| Minecraft | Java | Loom 插件 |
|-----------|------|-----------|
| 1.18.2, 1.19.4 | Java 17 | `fabric-loom-remap` |
| 1.20.1 – 1.20.6 | Java 21 | `fabric-loom-remap` |
| 1.21.1 – 1.21.11 | Java 21 | `fabric-loom-remap` |
| 26.1.2, 26.2 | Java 25 | `fabric-loom`（无混淆） |

> 单一代码库，17 个版本子项目，[ReplayMod preprocessor](https://github.com/ReplayMod/preprocessor) 处理所有版本差异。
> 构建结构遵循 [fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template)。

## 安装

- **服务端**：在 `mods/` 文件夹中安装 `remote-inventory-next`。远程容器功能必需。
- **客户端**：模组提供客户端容器缓存。如果依赖的建造模组需要，依赖关系会自动处理。
- **双端**：`environment: "*"` — 客户端和服务端均可运行。

## 命令（服务端）

```
/remoteinv distance <1-256>        设置或查看最大交互距离
/remoteinv distance enable|disable  启用/禁用距离限制

/remoteinv whitelist add <id>      添加方块到白名单
/remoteinv whitelist remove <id>
/remoteinv whitelist enable|disable 启用/禁用仅白名单模式
/remoteinv whitelist list|clear    显示/清空白名单

/remoteinv blacklist add <id>      添加方块到黑名单
/remoteinv blacklist remove|list|clear

/remoteinv config                  显示当前所有设置
```

> 白名单模式：仅列表中的方块允许远程交互。
> 黑名单模式：列表中的方块被排除在外。
> 空黑名单（默认）允许所有容器交互。

## 网络协议

### C2S — `RemoteExchangePayload`

| 字段 | 类型 | 说明 |
|------|------|------|
| `takePos` | `BlockPos` | 取物容器坐标 |
| `takeItemId` | `string` | 物品标识（如 `minecraft:diamond`） |
| `takeSlot` | `int` | 取物槽位索引 |
| `returnPos` | `BlockPos` | 还物容器坐标 |
| `returnItemId` | `string` | 归还物品标识 |
| `returnCount` | `int` | 归还物品数量 |

### S2C — `RemoteExchangeResultPayload`

| 字段 | 类型 | 说明 |
|------|------|------|
| `pos` | `BlockPos` | 回显容器坐标 |
| `takeResult` | `ResultType` | 取物结果 |
| `takenCount` | `int` | 实际取物数量 |
| `returnedCount` | `int` | 实际还物数量 |
| `inventoryDelta` | `List<SlotSnapshot>` | 背包变动的槽位快照 |

### C2S — `ScanContainerPayload`

| 字段 | 类型 | 说明 |
|------|------|------|
| `pos` | `BlockPos` | 要扫描的容器坐标 |

### S2C — `ScanContainerResultPayload`

| 字段 | 类型 | 说明 |
|------|------|------|
| `pos` | `BlockPos` | 回显容器坐标 |
| `entries` | `List<SlotEntry>` | 非空槽位列表：`(slot, itemId, count)` |

### 结果类型

| 返回值 | 含义 |
|--------|------|
| `SUCCESS` | 已从容器取出物品并给予玩家 |
| `PARTIAL` | 仅部分物品被取出（背包空间限制） |
| `INVENTORY_FULL` | 玩家背包已满 |
| `PLAYER_TOO_FAR` | 超出交互范围 |
| `CONTAINER_NOT_LOADED` | 目标区块未加载 |
| `CONTAINER_NOT_FOUND` | 坐标处无方块实体 |
| `NOT_A_CONTAINER` | 方块实体不是容器 |
| `SLOT_EMPTY` | 槽位为空或超出范围 |
| `ITEM_NOT_MATCH` | 槽位中的物品与请求不匹配 |
| `INTERNAL_ERROR` | 服务端意外错误 |
| `UNKNOWN` | 无法识别的结果 |

## 工作流程

```
客户端                              服务端
  │                                   │
  ├── exchange: 取物+还物 ───────────►│
  │                                   ├── 快照玩家背包
  │                                   ├── 还物到容器
  │                                   ├── 从容器取物
  │                                   ├── 再次快照背包
  │                                   ├── 计算变动槽位增量
  │                                   └── 发送结果 + 背包增量
  │◄── 结果 + 增量 ──────────────────┤
  │       应用背包增量                  │
  │       更新缓存（recordTake）        │
  │                                   │
  ├── scan_container: 坐标 ──────────►│
  │                                   ├── 验证距离/区块/容器
  │                                   ├── 遍历所有槽位
  │                                   └── 返回非空 (槽位,物品ID,数量)
  │◄── 坐标 + [槽位条目] ────────────┤
  │       更新缓存                     │
  │       从缓存查询物品               │
```

### 客户端缓存

模组内置客户端缓存（`ContainerItemCache`），提供：
- 按物品类型和容器位置双重索引
- 乐观记录取物/还物数量（无需重新扫描）
- 断开连接时持久化到 `remote_inventory_cache.json`
- 加入游戏时从磁盘加载
- 每个容器条目 30 秒 TTL
- 使用调色板压缩 JSON 格式，高效存储

## 客户端 API（供依赖模组使用）

`dev.blinkwhite.remoteinventory.client` 包提供公共 API：

- `RemoteInventoryClient` — 注册回调和发送 C2S 数据包
- `ContainerItemCache` — 查询和更新缓存的容器数据
- `ContainerReturnTracker` — 追踪已取物品用于 FIFO 归还
- `ContainerCachePersister` — 管理缓存持久化生命周期

### 使用示例

```
// 在客户端初始化时注册回调
RemoteInventoryClient.setExchangeCallback((pos, result, taken, returned, delta) -> {
    // 处理交换结果
});
RemoteInventoryClient.setScanResultCallback(payload -> {
    ContainerItemCache.INSTANCE.updateContainer(payload.getPos(), payload.getEntries());
});

// 发送请求
RemoteInventoryClient.sendExchange(takePos, itemId, slot, returnPos, returnItemId, count);
RemoteInventoryClient.sendScanContainerRequest(containerPos);
```

## 构建

```bash
# 构建所有版本 + 聚合版本包
./gradlew fabricWrapper:build

# 构建单个版本
./gradlew :1.21.11:buildAndCollect

# 发布到本地 Maven（供依赖模组使用）
./gradlew :1.21.11:publishToMavenLocal

# 运行单个版本的服务端
./gradlew :1.21.11:runServer
```

构建产物：`fabricWrapper/build/libs/`（版本包）及各 `versions/*/build/libs/`（单个版本）。

## 依赖

- **Java 21+**（26.x 需要 Java 25）
- **Fabric Loader** ≥0.19.2
- **Fabric API**（版本匹配你的 MC 版本）

## 项目结构

```
remote-inventory-next/
├── src/main/java/
│   └── dev/blinkwhite/remoteinventory/
│       ├── RemoteInventoryMod.java           # 服务端入口、命令注册
│       ├── Reference.java                    # 常量（MOD_ID = "remote-inventory-next"）
│       ├── client/
│       │   ├── ClientRemoteInventoryMod.java # 客户端入口、网络设置
│       │   ├── ContainerItemCache.java       # 客户端容器缓存（30 秒 TTL）
│       │   ├── ContainerCachePersister.java  # 缓存持久化（JSON，调色板压缩）
│       │   ├── ContainerReturnTracker.java   # FIFO 归还追踪
│       │   └── RemoteInventoryClient.java    # 客户端网络、收发回调
│       ├── command/RemoteInvCommand.java     # /remoteinv 命令
│       ├── config/RemoteInvConfig.java       # 服务端配置
│       ├── container/ContainerItemResolver.java  # 服务端取物逻辑
│       ├── enums/ResultType.java             # 结果枚举
│       ├── network/
│       │   ├── NetworkHandler.java           # 服务端数据包注册
│       │   ├── handler/
│       │   │   ├── RemoteExchangeHandler.java    # 双向交换处理器
│       │   │   └── ScanContainerHandler.java     # 容器扫描处理器
│       │   └── payload/
│       │       ├── RemoteExchangePayload.java
│       │       ├── RemoteExchangeResultPayload.java
│       │       ├── ScanContainerPayload.java
│       │       └── ScanContainerResultPayload.java
│       └── util/Translations.java           # 自定义 i18n 系统
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/remote-inventory-next/lang/   # 5 种语言
├── fabricWrapper/                           # 聚合 JAR（版本包）
├── versions/                                # 17 个 MC 版本子项目
├── build.gradle.kts                         # 预处理器链配置
├── common.gradle                            # 共享构建逻辑
├── settings.gradle.kts / settings.json
└── README.md
```

### 预处理器指令

使用 [Fallen-Breath 预处理器](https://github.com/Fallen-Breath/preprocessor) 实现单源码多版本支持：

```java
//#if MC >= 12005
// 基于 CustomPacketPayload 的网络（Fabric API）
//#else
//$$ // 基于 ResourceLocation + FriendlyByteBuf 的传统网络
//#endif
```

## 与旧版 remote-inventory-server 对比

| 特性 | remote-inventory-server（旧） | remote-inventory-next |
|------|------|------|
| 运行端 | 仅服务端 | **客户端 + 服务端** |
| 网络协议 | Exchange + Scan | Exchange + Scan（增强） |
| 客户端缓存 | ❌ | **✅ 内置**（30 秒 TTL，磁盘持久化） |
| 物品归还追踪 | ❌ | **✅ FIFO 队列** |
| 缓存持久化 | ❌ | **✅ 调色板压缩 JSON** |
| 版本子项目 | 14 | 17 |
| MC 26.1.2 支持 | ❌ | **✅** |
| MC 26.2 支持 | ❌ | **✅** |

## 许可证

AGPL-3.0
