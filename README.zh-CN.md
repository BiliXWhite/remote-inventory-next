# Remote Inventory Server

[English](README.md) | **中文**

服务端 Fabric Mod，远程解析容器物品。作为 **Litematica Printer** 等客户端的后端使用。

客户端发送物品请求 → 服务端验证距离、容器状态、物品匹配 → 将物品给予玩家或返回详细错误码。同时支持容器库存扫描，实现高效批量取物。

## 功能

- **物品获取** (`get_item_from_inventory`) — 请求容器中特定槽位的特定物品
- **容器扫描** (`scan_container`) — 一次请求扫描整个容器的所有非空槽位
- **可配置距离** — `/remoteinv distance <1-256>` 设置最大交互距离
- **白名单 / 黑名单** — `/remoteinv whitelist|blacklist add|remove|list|clear <方块>`
- **缓存支持** — 扫描结果为客户端缓存提供数据，实现高效重复访问

## 支持的版本

| Minecraft        | Java    | Loom 插件             |
|------------------|---------|---------------------|
| 1.18.2, 1.19.4   | Java 17 | `fabric-loom-remap` |
| 1.20.1 – 1.20.6  | Java 21 | `fabric-loom-remap` |
| 1.21.1 – 1.21.11 | Java 21 | `fabric-loom-remap` |
| 26.1             | Java 25 | `fabric-loom`（无混淆）  |

> 单一代码库，14 个版本子项目，[ReplayMod preprocessor](https://github.com/ReplayMod/preprocessor) 处理所有版本差异。
> 构建结构遵循 [fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template)。

## 命令

```
/remoteinv distance <1-256>    设置或查看最大交互距离
/remoteinv whitelist add <id>  添加方块到白名单
/remoteinv whitelist remove <id>
/remoteinv whitelist enable     启用仅白名单模式
/remoteinv whitelist disable    禁用白名单（回到黑名单模式）
/remoteinv whitelist list       显示当前白名单
/remoteinv whitelist clear      清空白名单
/remoteinv blacklist add <id>  添加方块到黑名单
/remoteinv blacklist remove|list|clear
/remoteinv config               显示当前所有设置
```

> 白名单模式：仅列表中的方块允许远程交互。
> 黑名单模式：列表中的方块被排除在外。
> 空黑名单（默认）允许所有容器交互。

## API

### C2S — `GetItemFromInventoryPayload`

| 字段       | 类型         | 说明                          |
|----------|------------|-----------------------------|
| `itemId` | `string`   | 物品标识（如 `minecraft:diamond`） |
| `pos`    | `BlockPos` | 容器坐标                        |
| `slot`   | `int`      | 槽位索引                        |

### C2S — `ScanContainerPayload`

| 字段    | 类型         | 说明       |
|-------|------------|----------|
| `pos` | `BlockPos` | 要扫描的容器坐标 |

### S2C — `GetItemResultPayload`

| 字段           | 类型           | 说明      |
|--------------|--------------|---------|
| `pos`        | `BlockPos`   | 回显的容器坐标 |
| `resultType` | `ResultType` | 结果枚举    |

### S2C — `ScanContainerResultPayload`

| 字段        | 类型                | 说明                             |
|-----------|-------------------|--------------------------------|
| `pos`     | `BlockPos`        | 回显的容器坐标                        |
| `entries` | `List<SlotEntry>` | 非空槽位列表：`(slot, itemId, count)` |

### 结果类型

| 返回值                    | 含义            |
|------------------------|---------------|
| `SUCCESS`              | 已从容器取出物品并给予玩家 |
| `PLAYER_TOO_FAR`       | 超出交互范围        |
| `CONTAINER_NOT_LOADED` | 目标区块未加载       |
| `CONTAINER_NOT_FOUND`  | 坐标处无方块实体      |
| `NOT_A_CONTAINER`      | 方块实体不是容器      |
| `SLOT_EMPTY`           | 槽位为空或超出范围     |
| `ITEM_NOT_MATCH`       | 槽位中的物品与请求不匹配  |
| `INTERNAL_ERROR`       | 服务端意外错误       |
| `UNKNOWN`              | 无法识别的结果       |

## 工作流程

```
客户端                             服务端
  │                                   │
  ├── get_item: 物品ID+坐标+槽位 ────►│
  │                                   ├── 距离检查
  │                                   ├── 区块是否已加载？
  │                                   ├── 方块实体是否存在？
  │                                   ├── 是否为容器？
  │                                   ├── 槽位有效且非空？
  │                                   ├── 物品 ID 是否匹配？
  │                                   └── 取出物品 → 给予玩家
  │◄──── 结果 + 坐标 ────────────────┤
  │                                   │
  ├── scan_container: 坐标 ─────────►│
  │                                   ├── 同上验证
  │                                   ├── 遍历所有槽位
  │                                   └── 返回非空 (槽位,物品ID,数量)
  │◄──── 坐标 + [槽位条目] ─────────┤
```

## 构建

```bash
# 构建所有版本
./gradlew build

# 构建单个版本
./gradlew :1.21.11:build

# 运行单个版本的服务端
./gradlew :1.21.11:runServer
```
构建产物：`versions/*/build/libs/`。
```
# 构建所有版本并将 JAR 收集到单个文件夹中:
./gradlew buildAndGather
```
所有构建产物：`build/libs/`（项目根目录）。

## 依赖

- **Java 21+**（26.1 快照需要 Java 25）
- **Fabric Loader** ≥0.18.4
- **Fabric API**（版本匹配你的 MC 版本）

## 项目结构

```
remote-inventory-server/
├── src/main/java/          # 共享源码（经过预处理）
│   ├── RemoteInventoryMod.java         # Mod 入口，命令注册
│   ├── Reference.java                  # 常量定义
│   ├── command/RemoteInvCommand.java   # /remoteinv 命令
│   ├── config/RemoteInvConfig.java     # 服务端配置（距离、列表）
│   ├── container/ContainerItemResolver.java  # 核心逻辑
│   ├── enums/ResultType.java           # 结果枚举
│   └── network/
│       ├── NetworkHandler.java         # 包类型 + 处理器注册
│       ├── handler/
│       │   ├── GetItemFromInventoryHandler.java
│       │   └── ScanContainerHandler.java
│       └── payload/
│           ├── GetItemFromInventoryPayload.java
│           ├── GetItemResultPayload.java
│           ├── ScanContainerPayload.java
│           └── ScanContainerResultPayload.java
├── src/main/resources/     # fabric.mod.json + 语言文件
├── buildSrc/               # 自定义 Gradle 插件
├── fabricWrapper/          # 聚合 JAR
├── versions/               # 13 个 MC 版本子项目
├── build.gradle.kts        # 预处理器链配置
├── build.fabric.gradle.kts         # MC ≥1.21.5
├── build.fabric.remap.gradle.kts   # MC <1.21.5
└── settings.gradle.kts     # 多版本子项目引入
```

### 预处理器指令

使用 [Fallen-Breath 预处理器](https://github.com/Fallen-Breath/preprocessor) 实现单源码多版本支持：

```java
//#if MC >= 12005
// New Networking API（CustomPacketPayload）
//#else
//$$ // Old Networking API（ResourceLocation + PacketByteBufs）
//#endif
```

## 许可证

[AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.txt)
