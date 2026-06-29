# Remote Inventory Next

**English** | [中文](README.zh-CN.md)

Client-server Fabric mod that provides remote container inventory management. Designed as a backend for **Litematica Printer** and similar building assistance mods.

Clients send item exchange requests → server validates distance, container state, and item match → executes atomic take+return operations with inventory delta sync. Supports container scanning for efficient multi-item retrieval and client-side caching with disk persistence.

## Features

- **Item Exchange** (`exchange`) — Atomic bidirectional item operation: take from container + return items to container in a single packet, with inventory delta sync for race-free local inventory correction
- **Container Scanning** (`scan_container`) — Scan an entire container's non-empty slots in one request
- **Client-Side Caching** — Scan results cached locally with 30s TTL, persisted to disk on disconnect. Dual-indexed by item type and container position for O(1) lookups
- **FIFO Return Tracking** — Tracks taken items for efficient FIFO-based return when inventory is full
- **Configurable Distance** — `/remoteinv distance <1-256>` sets max interaction range
- **Whitelist / Blacklist** — `/remoteinv whitelist|blacklist add|remove|list|clear <block>`

## Supported Versions

| Minecraft | Java | Loom Plugin |
|-----------|------|-------------|
| 1.18.2, 1.19.4 | Java 17 | `fabric-loom-remap` |
| 1.20.1 – 1.20.6 | Java 21 | `fabric-loom-remap` |
| 1.21.1 – 1.21.11 | Java 21 | `fabric-loom-remap` |
| 26.1.2, 26.2 | Java 25 | `fabric-loom` (unobfuscated) |

> Single codebase, 17 version subprojects, [ReplayMod preprocessor](https://github.com/ReplayMod/preprocessor) handles the rest.
> Build structure follows [fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template).

## Installation

- **Server**: Install `remote-inventory-next` mod in your server's `mods/` folder. Required for remote container functionality.
- **Client**: The mod provides client-side container caching. If your building mod depends on it, the dependency is automatic.
- **Both sides**: `environment: "*"` — runs on both client and server.

## Commands (Server-side)

```
/remoteinv distance <1-256>        Set or view max interaction distance
/remoteinv distance enable|disable  Toggle distance limit

/remoteinv whitelist add <id>      Add block to whitelist
/remoteinv whitelist remove <id>
/remoteinv whitelist enable|disable Enable/disable whitelist-only mode
/remoteinv whitelist list|clear    Show/clear whitelist

/remoteinv blacklist add <id>      Add block to blacklist
/remoteinv blacklist remove|list|clear

/remoteinv config                  Show all current settings
```

> Whitelist mode: ONLY listed blocks can be remotely interacted with.
> Blacklist mode: listed blocks are EXCLUDED from remote interaction.
> An empty blacklist (default) allows all containers.

## Network Protocol

### C2S — `RemoteExchangePayload`

| Field | Type | Description |
|-------|------|-------------|
| `takePos` | `BlockPos` | Container position to take from |
| `takeItemId` | `string` | Item identifier (e.g. `minecraft:diamond`) |
| `takeSlot` | `int` | Slot index to take from |
| `returnPos` | `BlockPos` | Container position to return items to |
| `returnItemId` | `string` | Item identifier to return |
| `returnCount` | `int` | Number of items to return |

### S2C — `RemoteExchangeResultPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Echoed container position |
| `takeResult` | `ResultType` | Result of take operation |
| `takenCount` | `int` | Number of items taken |
| `returnedCount` | `int` | Number of items returned |
| `inventoryDelta` | `List<SlotSnapshot>` | Changed inventory slots for client-side sync |

### C2S — `ScanContainerPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Container position to scan |

### S2C — `ScanContainerResultPayload`

| Field | Type | Description |
|-------|------|-------------|
| `pos` | `BlockPos` | Echoed container position |
| `entries` | `List<SlotEntry>` | Non-empty slots: `(slot, itemId, count)` |

### Result Types

| Code | Meaning |
|------|---------|
| `SUCCESS` | Item removed from container and given to player |
| `PARTIAL` | Only part of the items could be taken (inventory space limit) |
| `INVENTORY_FULL` | Player inventory has no space for the items |
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
  ├── exchange: take+return ────────►│
  │                                   ├── Snapshot player inventory
  │                                   ├── Return items to container
  │                                   ├── Take items from container
  │                                   ├── Snapshot inventory again
  │                                   ├── Compute delta of changed slots
  │                                   └── Send result + inventory delta
  │◄── result + delta ──────────────┤
  │       Apply inventory delta       │
  │       Update cache (recordTake)   │
  │                                   │
  ├── scan_container: pos ──────────►│
  │                                   ├── Validate distance / chunk / container
  │                                   ├── Iterate all slots
  │                                   └── Return non-empty (slot,id,count)
  │◄── pos + [slot entries] ────────┤
  │       Update cache               │
  │       Look up items from cache   │
```

### Client-Side Caching

The mod provides a built-in client-side cache (`ContainerItemCache`) that:
- Dual-indexes containers by item type and position
- Records taken/returned counts optimistically (no re-scan needed)
- Persists to `remote_inventory_cache.json` on disconnect
- Loads from disk on join
- 30-second TTL per container entry
- Uses palette-compressed JSON format for efficient storage

## Client API (for dependent mods)

The client-side classes in `dev.blinkwhite.remoteinventory.client` provide a public API for mods that want to integrate remote container functionality:

- `RemoteInventoryClient` — Register callbacks and send C2S packets
- `ContainerItemCache` — Query and update cached container data
- `ContainerReturnTracker` — Track taken items for FIFO return
- `ContainerCachePersister` — Handle cache persistence lifecycle

### Example

```
// Register callbacks on client init
RemoteInventoryClient.setExchangeCallback((pos, result, taken, returned, delta) -> {
    // Handle exchange result
});
RemoteInventoryClient.setScanResultCallback(payload -> {
    ContainerItemCache.INSTANCE.updateContainer(payload.getPos(), payload.getEntries());
});

// Send requests
RemoteInventoryClient.sendExchange(takePos, itemId, slot, returnPos, returnItemId, count);
RemoteInventoryClient.sendScanContainerRequest(containerPos);
```

## Build

```bash
# Build all versions + aggregate version pack
./gradlew fabricWrapper:build

# Build a single version
./gradlew :1.21.11:buildAndCollect

# Publish to local Maven (for dependent mods)
./gradlew :1.21.11:publishToMavenLocal

# Run the server for one version
./gradlew :1.21.11:runServer
```

Output JARs go to `fabricWrapper/build/libs/` (version pack) and each `versions/*/build/libs/` (individual versions).

## Dependencies

- **Java 21+** (26.x requires Java 25)
- **Fabric Loader** ≥0.19.2
- **Fabric API** (version matching your MC version)

## Project Structure

```
remote-inventory-next/
├── src/main/java/
│   └── dev/blinkwhite/remoteinventory/
│       ├── RemoteInventoryMod.java           # Server entry point, command registration
│       ├── Reference.java                    # Constants (MOD_ID = "remote-inventory-next")
│       ├── client/
│       │   ├── ClientRemoteInventoryMod.java # Client entry point, networking setup
│       │   ├── ContainerItemCache.java       # Client-side container cache (30s TTL)
│       │   ├── ContainerCachePersister.java   # Cache persistence (JSON, palette-compressed)
│       │   ├── ContainerReturnTracker.java    # FIFO return tracking
│       │   └── RemoteInventoryClient.java    # Client networking, send/recv callbacks
│       ├── command/RemoteInvCommand.java     # /remoteinv command
│       ├── config/RemoteInvConfig.java       # Server config (distance, whitelist/blacklist)
│       ├── container/ContainerItemResolver.java  # Server-side item extraction logic
│       ├── enums/ResultType.java             # Result enum
│       ├── network/
│       │   ├── NetworkHandler.java           # Server-side packet registration
│       │   ├── handler/
│       │   │   ├── RemoteExchangeHandler.java    # Bidirectional exchange handler
│       │   │   └── ScanContainerHandler.java     # Container scan handler
│       │   └── payload/
│       │       ├── RemoteExchangePayload.java
│       │       ├── RemoteExchangeResultPayload.java
│       │       ├── ScanContainerPayload.java
│       │       └── ScanContainerResultPayload.java
│       └── util/Translations.java           # Custom i18n system
├── src/main/resources/
│   ├── fabric.mod.json
│   └── assets/remote-inventory-next/lang/   # 5 languages
├── fabricWrapper/                           # Aggregate JAR (version pack)
├── versions/                                # 17 MC version subprojects
├── build.gradle.kts                         # Preprocessor chain config
├── common.gradle                            # Shared build logic
├── settings.gradle.kts / settings.json
└── README.md
```

### Preprocessor Directives

Uses [Fallen-Breath preprocessor](https://github.com/Fallen-Breath/preprocessor) for single-source multi-version support:

```java
//#if MC >= 12005
// CustomPacketPayload-based networking (Fabric API)
//#else
//$$ // Legacy ResourceLocation + FriendlyByteBuf networking
//#endif
```

## Comparison with Original remote-inventory-server

| Feature | remote-inventory-server (old) | remote-inventory-next |
|---------|------|------|
| Side | Server-only | **Client + Server** |
| Network protocol | Exchange + Scan | Exchange + Scan (enhanced) |
| Client-side caching | ❌ | **✅ Built-in** (30s TTL, disk persistence) |
| Item return tracking | ❌ | **✅ FIFO queue** |
| Cache persistence | ❌ | **✅ Palette-compressed JSON** |
| Version subprojects | 14 | 17 |
| MC 26.1.2 support | ❌ | **✅** |
| MC 26.2 support | ❌ | **✅** |

## License

AGPL-3.0
