package dev.blinkwhite.remoteinventory.client;

import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class ContainerItemCache {
    public static final ContainerItemCache INSTANCE = new ContainerItemCache();
    private static final long CACHE_TTL_MS = 30_000;

    private final Map<String, DimensionCache> dimensionCaches = new ConcurrentHashMap<>();

    public record SlotRef(String dimension, BlockPos pos, int slot) {}
    public record ContainerSnapshot(List<ScanContainerResultPayload.SlotEntry> entries, long timestamp) {}
    public record DimensionKey(String dimension, BlockPos pos) {}

    private static class DimensionCache {
        final Map<String, List<SlotRef>> itemIndex = new ConcurrentHashMap<>();
        final Map<BlockPos, ContainerSnapshot> containerIndex = new ConcurrentHashMap<>();
    }

    private ContainerItemCache() {}

    private static long now() { return System.currentTimeMillis(); }

    private DimensionCache getOrCreateCache(String dimension) {
        return dimensionCaches.computeIfAbsent(dimension, k -> new DimensionCache());
    }

    public SlotRef findItem(String itemId) {
        for (var dimEntry : dimensionCaches.entrySet()) {
            String dimension = dimEntry.getKey();
            DimensionCache cache = dimEntry.getValue();
            List<SlotRef> refs = cache.itemIndex.get(itemId);
            if (refs == null || refs.isEmpty()) continue;
            synchronized (refs) {
                if (refs.isEmpty()) continue;
                return refs.remove(0);
            }
        }
        return null;
    }

    public SlotRef findItem(String itemId, String dimension) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) return null;
        List<SlotRef> refs = cache.itemIndex.get(itemId);
        if (refs == null || refs.isEmpty()) return null;
        synchronized (refs) {
            if (refs.isEmpty()) return null;
            return refs.remove(0);
        }
    }

    public void updateContainer(String dimension, BlockPos pos, List<ScanContainerResultPayload.SlotEntry> entries) {
        DimensionCache cache = getOrCreateCache(dimension);
        ContainerSnapshot old = cache.containerIndex.remove(pos);
        if (old != null) {
            for (ScanContainerResultPayload.SlotEntry e : old.entries())
                removeSlotRef(cache, e.itemId(), dimension, pos, e.slot());
        }
        if (entries.isEmpty()) {
            cache.containerIndex.put(pos, new ContainerSnapshot(List.of(), now()));
            return;
        }
        cache.containerIndex.put(pos, new ContainerSnapshot(List.copyOf(entries), now()));
        for (ScanContainerResultPayload.SlotEntry e : entries)
            cache.itemIndex.computeIfAbsent(e.itemId(),
                    k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new SlotRef(dimension, pos, e.slot()));
    }

    public void invalidate(String dimension, BlockPos pos) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) return;
        ContainerSnapshot old = cache.containerIndex.remove(pos);
        if (old != null)
            for (ScanContainerResultPayload.SlotEntry e : old.entries())
                removeSlotRef(cache, e.itemId(), dimension, pos, e.slot());
    }

    public void invalidateDimension(String dimension) {
        dimensionCaches.remove(dimension);
    }

    public boolean isCached(String dimension, BlockPos pos) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) return false;
        ContainerSnapshot snap = cache.containerIndex.get(pos);
        return snap != null && (now() - snap.timestamp()) < CACHE_TTL_MS;
    }

    public void invalidateOldest(String dimension) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) return;
        BlockPos oldest = null;
        long oldestTime = Long.MAX_VALUE;
        for (var e : cache.containerIndex.entrySet()) {
            if (e.getValue().timestamp() < oldestTime) {
                oldestTime = e.getValue().timestamp();
                oldest = e.getKey();
            }
        }
        if (oldest != null) invalidate(dimension, oldest);
    }

    public int getContainerCount(String dimension) {
        DimensionCache cache = dimensionCaches.get(dimension);
        return cache == null ? 0 : cache.containerIndex.size();
    }

    public int getTotalContainerCount() {
        return dimensionCaches.values().stream()
                .mapToInt(c -> c.containerIndex.size())
                .sum();
    }

    public void clear() {
        dimensionCaches.clear();
    }

    public void clearDimension(String dimension) {
        dimensionCaches.remove(dimension);
    }

    public void recordTake(String dimension, BlockPos pos, int slot, int takenCount) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) return;
        ContainerSnapshot snapshot = cache.containerIndex.get(pos);
        if (snapshot == null) return;

        List<ScanContainerResultPayload.SlotEntry> updated = new ArrayList<>();
        String itemId = null;
        for (ScanContainerResultPayload.SlotEntry e : snapshot.entries()) {
            if (e.slot() == slot) {
                itemId = e.itemId();
                int remaining = e.count() - takenCount;
                if (remaining > 0)
                    updated.add(new ScanContainerResultPayload.SlotEntry(slot, e.itemId(), remaining));
            } else {
                updated.add(e);
            }
        }
        cache.containerIndex.put(pos, new ContainerSnapshot(updated, now()));
        if (itemId != null) {
            removeSlotRef(cache, itemId, dimension, pos, slot);
            if (takenCount < getOriginalCount(snapshot, slot))
                cache.itemIndex.computeIfAbsent(itemId,
                        k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(new SlotRef(dimension, pos, slot));
        }
    }

    public void recordReturn(String dimension, BlockPos pos, String itemId, int returnedCount) {
        DimensionCache cache = dimensionCaches.get(dimension);
        if (cache == null) { invalidate(dimension, pos); return; }
        ContainerSnapshot snapshot = cache.containerIndex.get(pos);
        if (snapshot == null) { invalidate(dimension, pos); return; }

        List<ScanContainerResultPayload.SlotEntry> updated = new ArrayList<>(snapshot.entries());
        boolean merged = false;
        for (int i = 0; i < updated.size(); i++) {
            ScanContainerResultPayload.SlotEntry e = updated.get(i);
            if (e.itemId().equals(itemId)) {
                updated.set(i, new ScanContainerResultPayload.SlotEntry(e.slot(), itemId, e.count() + returnedCount));
                merged = true;
                break;
            }
        }
        if (!merged) { invalidate(dimension, pos); return; }

        cache.containerIndex.put(pos, new ContainerSnapshot(updated, now()));
        cache.itemIndex.computeIfAbsent(itemId,
                k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new SlotRef(dimension, pos, findSlot(updated, itemId)));
    }

    private static int getOriginalCount(ContainerSnapshot snap, int slot) {
        for (ScanContainerResultPayload.SlotEntry e : snap.entries())
            if (e.slot() == slot) return e.count();
        return 0;
    }

    private static int findSlot(List<ScanContainerResultPayload.SlotEntry> entries, String itemId) {
        for (ScanContainerResultPayload.SlotEntry e : entries)
            if (e.itemId().equals(itemId)) return e.slot();
        return -1;
    }

    private void removeSlotRef(DimensionCache cache, String itemId, String dimension, BlockPos pos, int slot) {
        List<SlotRef> refs = cache.itemIndex.get(itemId);
        if (refs == null) return;
        synchronized (refs) {
            refs.removeIf(r -> r.dimension().equals(dimension) && r.pos().equals(pos) && r.slot() == slot);
            if (refs.isEmpty()) cache.itemIndex.remove(itemId);
        }
    }

    public void importContainer(String dimension, BlockPos pos, List<ScanContainerResultPayload.SlotEntry> entries) {
        DimensionCache cache = getOrCreateCache(dimension);
        ContainerSnapshot old = cache.containerIndex.remove(pos);
        if (old != null)
            for (ScanContainerResultPayload.SlotEntry e : old.entries())
                removeSlotRef(cache, e.itemId(), dimension, pos, e.slot());
        if (entries.isEmpty()) {
            cache.containerIndex.put(pos, new ContainerSnapshot(List.of(), now()));
            return;
        }
        cache.containerIndex.put(pos, new ContainerSnapshot(List.copyOf(entries), now()));
        for (ScanContainerResultPayload.SlotEntry e : entries)
            cache.itemIndex.computeIfAbsent(e.itemId(),
                    k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new SlotRef(dimension, pos, e.slot()));
    }

    public Map<DimensionKey, List<ScanContainerResultPayload.SlotEntry>> exportContainerData() {
        Map<DimensionKey, List<ScanContainerResultPayload.SlotEntry>> data = new HashMap<>();
        for (var dimEntry : dimensionCaches.entrySet()) {
            String dimension = dimEntry.getKey();
            DimensionCache cache = dimEntry.getValue();
            for (Map.Entry<BlockPos, ContainerSnapshot> e : cache.containerIndex.entrySet())
                data.put(new DimensionKey(dimension, e.getKey()), new ArrayList<>(e.getValue().entries()));
        }
        return data;
    }
}