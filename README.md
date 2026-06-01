# Remote Inventory Server

**English** | [中文](README.zh-CN.md)

Server-side Fabric mod that resolves container inventory items remotely. Designed as a backend for **Litematica Printer** and similar clients.

Clients send item requests → server validates distance, container state, and item match → gives the item to the player or returns a detailed error code. Also supports container inventory scanning for efficient multi-item retrieval.

## Features

- **Item Retrieval** (`get_item_from_inventory`) — Request a specific item from a specific slot
- **Container Scanning** (`scan_container`) — Scan an entire container's non-empty slots in one request
- **Configurable Distance** — `/remoteinv distance <1-256>` sets max interaction range
- **Whitelist / Blacklist** — `/remoteinv whitelist|blacklist add|remove|list|clear <block>`
- **Caching Support** — Scan results enable client-side caching for efficient repeat access

## Supported Versions

| Minecraft | Java | Loom Plugin |
|-----------|------|-------------|
| 1.18.2, 1.19.4 | Java 17 | `fabric-loom-remap` |
| 1.20.1 – 1.20.6 | Java 21 | `fabric-loom-remap` |
| 1.21.1 – 1.21.11 | Java 21 | `fabric-loom-remap` |
| 26.1 | Java 25 | `fabric-loom` (unobfuscated) |

> Single codebase, 14 version subprojects, [ReplayMod preprocessor](https://github.com/ReplayMod/preprocessor) handles the rest.
> Build structure follows [fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template).

## Commands

```
/remoteinv distance <1-256>    Set or view max interaction distance
/remoteinv whitelist add <id>  Add block to whitelist
/remoteinv whitelist remove <id>
/remoteinv whitelist enable     Enable whitelist-only mode
/remoteinv whitelist disable    Disable whitelist (back to blacklist mode)
/remoteinv whitelist list       Show current whitelist
/remoteinv whitelist clear      Clear whitelist
/remoteinv blacklist add <id>  Add block to blacklist
/remoteinv blacklist remove|list|clear
/remoteinv config               Show all current settings
```

> Whitelist mode: ONLY listed blocks can be remotely interacted with.
> Blacklist mode: listed blocks are EXCLUDED from remote interaction.
> An empty blacklist (default) allows all containers.

## API

### C2S — `GetItemFromInventoryPayload`

| Field | Type | Description |
|-------|------|-------------|
| `itemId` | `string` | Item identifier (e.g. `minecraft:diamond`) |
| `pos` | `BlockPos` | Container position |
| `slot` | `int` | Slot index |

### C2S — `ScanContainerPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Container position to scan |

### S2C — `GetItemResultPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Echoed container position |
| `resultType` | `ResultType` | Result enum |

### S2C — `ScanContainerResultPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Echoed container position |
| `entries` | `List<SlotEntry>` | Non-empty slots: `(slot, itemId, count)` |

### Result Types

| Code | Meaning |
|------|---------|
| `SUCCESS` | Item removed from container and given to player |
| `PLAYER_TOO_FAR` | Exceeded interaction range |
| `CONTAINER_NOT_LOADED` | Target chunk not loaded |
| `CONTAINER_NOT_FOUND` | No block entity at position |
| `NOT_A_CONTAINER` | Block entity is not a Container |
| `SLOT_EMPTY` | Slot is empty or out of bounds |
| `ITEM_NOT_MATCH` | Item in slot doesn't match requested item |
| `INTERNAL_ERROR` | Unexpected server-side failure |
| `UNKNOWN` | Unrecognized result |

## How It Works

```
Client                              Server
  │                                   │
  ├── get_item: itemId+pos+slot ────►│
  │                                   ├── Distance check
  │                                   ├── Chunk loaded?
  │                                   ├── BlockEntity exists?
  │                                   ├── Is a Container?
  │                                   ├── Slot valid & non-empty?
  │                                   ├── Item ID matches?
  │                                   └── Remove item → give to player
  │◄──── result + pos ──────────────┤
  │                                   │
  ├── scan_container: pos ──────────►│
  │                                   ├── Same validation as above
  │                                   ├── Iterate all slots
  │                                   └── Return non-empty (slot,id,count)
  │◄──── pos + [slot entries] ──────┤
```

## Build

```bash
# Build all versions
./gradlew build

# Build a single version
./gradlew :1.21.11:build

# Run the server for one version
./gradlew :1.21.11:runServer
```
Output JARs go to `versions/*/build/libs/`.
```
# If you want to build all versions and collect the JARs into a single folder:
./gradlew buildAndGather
```
Output JARs go to `build/libs/` (project root directory).

## Dependencies

- **Java 21+** (26.1 snapshot requires Java 25)
- **Fabric Loader** ≥0.18.4
- **Fabric API** (version matching your MC version)

## Project Structure

```
remote-inventory-server/
├── src/main/java/          # Shared source (preprocessed per version)
│   ├── RemoteInventoryMod.java         # Mod entry, command registration
│   ├── Reference.java                  # Constants
│   ├── command/RemoteInvCommand.java   # /remoteinv command
│   ├── config/RemoteInvConfig.java     # Server-side config (distance, lists)
│   ├── container/ContainerItemResolver.java  # Core logic
│   ├── enums/ResultType.java           # Result enum
│   └── network/
│       ├── NetworkHandler.java         # Packet type + handler registration
│       ├── handler/
│       │   ├── GetItemFromInventoryHandler.java
│       │   └── ScanContainerHandler.java
│       └── payload/
│           ├── GetItemFromInventoryPayload.java
│           ├── GetItemResultPayload.java
│           ├── ScanContainerPayload.java
│           └── ScanContainerResultPayload.java
├── src/main/resources/     # fabric.mod.json + lang files
├── buildSrc/               # Custom Gradle plugin
├── fabricWrapper/          # Aggregate JAR
├── versions/               # 13 MC version subprojects
├── build.gradle.kts        # Preprocessor chain config
├── build.fabric.gradle.kts         # MC ≥1.21.5
├── build.fabric.remap.gradle.kts   # MC <1.21.5
└── settings.gradle.kts     # Multi-version subproject includes
```

### Preprocessor Directives

Uses [Fallen-Breath preprocessor](https://github.com/Fallen-Breath/preprocessor) for single-source multi-version support:

```java
//#if MC >= 12005
// New networking API (CustomPacketPayload)
//#else
//$$ // Old networking API (ResourceLocation + PacketByteBufs)
//#endif
```

## License

[AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.txt)
