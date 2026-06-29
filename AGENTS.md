# PROJECT KNOWLEDGE BASE

**Generated:** 2026-05-31
**Stack:** Minecraft Fabric mod, Java 8-25, Gradle 9.5, Groovy DSL (common.gradle)
**Template:** [Fallen-Breath/fabric-mod-template](https://github.com/Fallen-Breath/fabric-mod-template) (multi branch)

## OVERVIEW

Server-side Fabric mod that resolves container inventory items remotely. Client sends item ID + slot → server validates distance, container state, item match → gives item to player or returns error. Also supports container inventory scanning (`scan_container`) for efficient multi-item retrieval. Spans Minecraft 1.18.2 through 26.1 via preprocessor.

## STRUCTURE

```
remote-inventory-server/
├── src/main/java/         # Shared source (preprocessed per version)
├── src/main/resources/    # fabric.mod.json template + lang/
├── versions/              # 14 MC version subprojects
│   ├── 1.18.2/            # Each has gradle.properties with version-specific config
│   ├── ...
│   └── 26.1/
├── fabricWrapper/         # Aggregator JAR bundling all version submods
├── settings.json          # Centralized version list (template pattern)
├── settings.gradle.kts    # Reads settings.json, assigns common.gradle to subprojects
├── build.gradle.kts       # Root: plugin declarations + preprocessor version chain + buildAndGather
├── common.gradle          # Shared build script (Groovy DSL) for ALL version subprojects
└── gradle.properties      # Shared properties (mod_id, loader_version, lombok, etc.)
```

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Mod entry point | `RemoteInventoryMod.java` | Registers network + command |
| Core logic — resolve item | `container/ContainerItemResolver.java` | Distance check → container lookup → slot match → give item |
| Core logic — scan container | `container/ContainerItemResolver.java` | `scanContainer()` iterates all slots, returns non-empty entries |
| Server config | `config/RemoteInvConfig.java` | Distance, whitelist/blacklist (in-memory, String-based) |
| Command | `command/RemoteInvCommand.java` | `/remoteinv distance|whitelist|blacklist|config` |
| Network packets (C2S) | `network/payload/GetItemFromInventoryPayload.java` | itemId + BlockPos + slot |
| Network packets (C2S) | `network/payload/ScanContainerPayload.java` | BlockPos |
| Network packets (S2C) | `network/payload/GetItemResultPayload.java` | BlockPos + ResultType |
| Network packets (S2C) | `network/payload/ScanContainerResultPayload.java` | BlockPos + `List<SlotEntry>` (slot, itemId, count) |
| Packet handler — get item | `network/handler/GetItemFromInventoryHandler.java` | Server-thread dispatch for item retrieval |
| Packet handler — scan | `network/handler/ScanContainerHandler.java` | Server-thread dispatch for container scanning |
| Network registration | `network/NetworkHandler.java` | PayloadTypeRegistry + all handler registration |
| Result enums | `enums/ResultType.java` | SUCCESS, PLAYER_TOO_FAR, CONTAINER_NOT_FOUND, etc. |
| Constants | `Reference.java` | MOD_ID, MOD_NAME, LOGGER, MAX_CONTAINER_INTERACTION_DISTANCE |
| Language files | `src/main/resources/assets/remote-inventory-next/lang/` | en_us, zh_cn, zh_tw, lzh (yamlang-managed) |
| Build — shared script | `common.gradle` | Groovy DSL; handles Java version, Lombok, Loom, publishing, license, yamlang |
| Build — root config | `build.gradle.kts` | Preprocessor nodes & links, buildAndGather task |
| Build — version list | `settings.json` | JSON array of all MC version directories |

## CONVENTIONS

- **Preprocessor comments**: `//#if MC >= X` / `//#else` / `//#elseif MC >= X` / `//#endif` for version-specific code. `//$$` prefix for alternate branches. Mojang mappings only for MC < 26.0.
- **Command API**: `MC >= 11900` uses Fabric `command.v2` (`(dispatcher, registryAccess, environment)`), older uses `command.v1` (`(dispatcher, dedicated)`).
- **Text components**: `MC >= 11900` uses `Component.literal()`, older uses `new TextComponent()`.
- **sendSuccess**: `MC >= 12000` accepts `Supplier<Component>`, older accepts `Component` directly.
- **Version subprojects**: Each subproject in `versions/` has `gradle.properties` with `minecraft_version`, `fabric_version`, `mcVersion` (int), `parchment_version`, `minecraft_dependency`, `game_versions`. `mcVersion` drives Java version selection.
- **Build split**: MC ≥ 26.0 (unobfuscated) uses `fabric-loom` directly, no mappings needed. MC < 26.0 uses `fabric-loom-remap` with `loom.officialMojangMappings()`. Handled by `unobfuscated` flag in `common.gradle`.
- **Dependencies**: MC ≥ 26.0 uses `implementation` scope; older uses `modImplementation` (auto-switched by `common.gradle`).
- **Lombok**: Project uses Lombok throughout. `@Data`, `@Slf4j` etc. expected.
- **Java versions**: `mcVersionInt >= 260000 → Java 25`, `>= 12005 → Java 21`, `>= 11800 → Java 17`, `< 11800 → Java 8`. Toolchain auto-provisioned.
- **IDE runs**: Only server run config generated. VM args include Mixin debug.
- **Version packaging**: `fabricWrapper` collects all version JARs into a single "version pack" mod. `buildAndGather` task copies all JARs to root `build/libs/`.
- **Config storage**: `RemoteInvConfig` is in-memory only (no persistence). Whitelist/blacklist use `Set<String>` (block IDs as registry strings). Distance range: 1.0–256.0.
- **License**: AGPL-3.0. See root `LICENSE` file.
- **Yamlang**: Language files managed by `me.fallenbreath.yamlang` plugin. Input dir: `assets/remote-inventory-server/lang/`.

## NETWORK PROTOCOL

### C2S: `get_item_from_inventory`
```
Field order: Identifier/ResourceLocation(itemId) → BlockPos(pos) → VarInt(slot)
Response: S2C `get_item_result` → BlockPos(pos) → Enum(ResultType)
```

### C2S: `scan_container`
```
Field order: BlockPos(pos)
Response: S2C `scan_container_result` → BlockPos(pos) → VarInt(count)
  → for each: VarInt(slot) → Identifier/ResourceLocation(itemId) → VarInt(count)
```

## COMMAND REFERENCE

```
/remoteinv distance [1-256]     Set/view max interaction distance
/remoteinv whitelist add <id>   Add block to whitelist (e.g. minecraft:chest)
/remoteinv whitelist remove <id>
/remoteinv whitelist enable|disable  Toggle whitelist-only mode
/remoteinv whitelist list|clear
/remoteinv blacklist add|remove|list|clear
/remoteinv config               Show current distance + lists
```

- Whitelist mode: ONLY listed block IDs are allowed.
- Blacklist mode (default): listed block IDs are EXCLUDED.
- Empty blacklist allows all containers.
- Block IDs use registry format: `minecraft:chest`, `minecraft:barrel`, etc.
