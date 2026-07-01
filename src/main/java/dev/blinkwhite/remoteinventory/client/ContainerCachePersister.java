package dev.blinkwhite.remoteinventory.client;

import com.google.gson.Gson;
import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Environment(EnvType.CLIENT)
public class ContainerCachePersister {
    private static final Gson GSON = new Gson();

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> save(client));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> load(client));
    }

    static void save(Minecraft client) {
        Map<ContainerItemCache.DimensionKey, List<ScanContainerResultPayload.SlotEntry>> data =
                ContainerItemCache.INSTANCE.exportContainerData();
        if (data.isEmpty()) return;

        List<String> palette = new ArrayList<>();
        Map<String, Integer> paletteIndex = new HashMap<>();
        for (var slots : data.values()) {
            for (var se : slots) {
                String id = se.itemId();
                if (!paletteIndex.containsKey(id)) {
                    paletteIndex.put(id, palette.size());
                    palette.add(id);
                }
            }
        }

        List<CacheEntry> entries = new ArrayList<>();
        for (var e : data.entrySet()) {
            ContainerItemCache.DimensionKey key = e.getKey();
            List<SlotData> slots = new ArrayList<>();
            for (var se : e.getValue())
                slots.add(new SlotData(se.slot(), paletteIndex.get(se.itemId()), se.count()));
            entries.add(new CacheEntry(key.dimension(), key.pos().getX(), key.pos().getY(), key.pos().getZ(), slots));
        }

        try {
            Path path = cachePath(client);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(new CacheFile(palette, entries)));
            Reference.LOGGER.info("[Cache] saved {} containers, {} items to {}",
                    entries.size(), palette.size(), path);
        } catch (IOException e) {
            Reference.LOGGER.warn("[Cache] save failed: {}", e.getMessage());
        }
    }

    static void load(Minecraft client) {
        Path path = cachePath(client);
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            CacheFile file = GSON.fromJson(json, CacheFile.class);
            if (file == null || file.p == null || file.c == null) return;

            List<String> palette = file.p;
            for (CacheEntry e : file.c) {
                List<ScanContainerResultPayload.SlotEntry> slots = new ArrayList<>();
                for (SlotData s : e.s) {
                    String itemId = s.i >= 0 && s.i < palette.size() ? palette.get(s.i) : "minecraft:air";
                    slots.add(new ScanContainerResultPayload.SlotEntry(s.s, itemId, s.c));
                }
                String dimension = e.d != null ? e.d : "minecraft:overworld";
                ContainerItemCache.INSTANCE.importContainer(dimension, new BlockPos(e.x, e.y, e.z), slots);
            }
            Reference.LOGGER.info("[Cache] loaded {} containers, {} items from {}",
                    file.c.size(), palette.size(), path);
        } catch (IOException e) {
            Reference.LOGGER.warn("[Cache] load failed: {}", e.getMessage());
        }
    }

    private static String getCacheKey(Minecraft client) {
        if (client.getCurrentServer() != null) {
            return sanitize(client.getCurrentServer().ip);
        }
        if (client.getSingleplayerServer() != null) {
            return "local_" + sanitize(client.getSingleplayerServer().getWorldData().getLevelName());
        }
        return "default";
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static Path cachePath(Minecraft client) {
        return FabricLoader.getInstance().getGameDir()
                .resolve("remoteinventory").resolve("remote_inventory_cache_" + getCacheKey(client) + ".json");
    }

    @SuppressWarnings("unused")
    private static class CacheFile { List<String> p; List<CacheEntry> c;
        CacheFile(List<String> p, List<CacheEntry> c) { this.p = p; this.c = c; } }
    @SuppressWarnings("unused")
    private static class CacheEntry { String d; int x, y, z; List<SlotData> s;
        CacheEntry(String d, int x, int y, int z, List<SlotData> s) { this.d = d; this.x = x; this.y = y; this.z = z; this.s = s; } }
    @SuppressWarnings("unused")
    private static class SlotData { int s; int i; int c;
        SlotData(int s, int i, int c) { this.s = s; this.i = i; this.c = c; } }
}