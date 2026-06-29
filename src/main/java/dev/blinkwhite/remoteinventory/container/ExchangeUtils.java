package dev.blinkwhite.remoteinventory.container;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared exchange business logic used by both modern (>=1.20.5) and legacy handlers.
 * Registration and serialization are handler-specific; the actual exchange logic lives here.
 */
public class ExchangeUtils {

    public record ExchangeData(BlockPos takePos, String takeItemId, int takeSlot,
                               BlockPos returnPos, String returnItemId, int returnCount) {}

    public static byte[] snapshotInventory(ServerPlayer player) {
        Inventory inv = player.getInventory();
        byte[] snap = new byte[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            snap[i] = (byte) Math.min(s.getCount(), 127);
        }
        return snap;
    }

    public static List<RemoteExchangeResultPayload.SlotSnapshot> computeDelta(ServerPlayer player,
                                                                               byte[] before, byte[] after) {
        List<RemoteExchangeResultPayload.SlotSnapshot> delta = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (before[i] != after[i]) {
                ItemStack s = inv.getItem(i);
                String itemId = s.isEmpty() ? "" : getItemId(s.getItem());
                delta.add(new RemoteExchangeResultPayload.SlotSnapshot(i, itemId, s.getCount()));
            }
        }
        return delta;
    }

    public static int returnItems(ServerPlayer player, BlockPos pos, String itemId, int count) {
        if (count <= 0 || itemId.isEmpty()) return 0;
        Level level = getPlayerLevel(player);
        if (!level.isLoaded(pos)) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return 0;
        Item item = resolveItem(itemId);
        if (item == null) return 0;
        Inventory inv = player.getInventory();
        int maxInsertable = simulateInsert(container, inv, item);
        int toMove = Math.min(count, maxInsertable);
        if (toMove <= 0) return 0;
        int moved = 0;
        for (int i = 0; i < 36 && moved < toMove; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = Math.min(toMove - moved, stack.getCount());
            ItemStack taken = stack.split(take);
            int before = taken.getCount();
            int inserted = insertIntoContainer(container, taken);
            moved += inserted;
            if (inserted < before) {
                inv.add(taken);
                if (!taken.isEmpty()) player.drop(taken, false);
                break;
            }
        }
        container.setChanged();
        return moved;
    }

    public static ContainerItemResolver.ResolveResult takeItems(ServerPlayer player,
                                                                  BlockPos pos, String itemId, int slot) {
        if (itemId.isEmpty() || slot < 0) return null;
        return ContainerItemResolver.resolveItem(player, pos, itemId, slot);
    }

    public static Item resolveItem(String itemId) {
        //#if MC >= 11903
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
        //#else
        //$$ return net.minecraft.core.Registry.ITEM.getOptional(
        //#endif
            //#if MC >= 12105
            net.minecraft.resources.Identifier.parse(itemId)
            //#elseif MC >= 12101
            //$$ net.minecraft.resources.ResourceLocation.parse(itemId)
            //#else
            //$$ new net.minecraft.resources.ResourceLocation(itemId)
            //#endif
        ).orElse(null);
    }

    private static String getItemId(Item item) {
        //#if MC >= 11903
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        //#else
        //$$ return net.minecraft.core.Registry.ITEM.getKey(item).toString();
        //#endif
    }

    private static Level getPlayerLevel(ServerPlayer player) {
        //#if MC >= 12000
        return player.level();
        //#else
        //$$ return player.getLevel();
        //#endif
    }

    private static int simulateInsert(Container container, Inventory inv, Item item) {
        int maxStack = item.getDefaultInstance().getMaxStackSize();
        int available = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(item)) continue;
            available += s.getCount();
        }
        int canFit = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) {
                canFit += maxStack;
            } else if (s.is(item) && s.getCount() < maxStack) {
                canFit += maxStack - s.getCount();
            }
        }
        return Math.min(available, canFit);
    }

    private static int insertIntoContainer(Container container, ItemStack stack) {
        int maxStack = stack.getMaxStackSize();
        int remaining = stack.getCount();
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) {
                int put = Math.min(remaining, maxStack);
                ItemStack copy = stack.copy();
                copy.setCount(put);
                container.setItem(i, copy);
                remaining -= put;
            }
            //#if MC >= 12005
            else if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < maxStack) {
            //#else
            //$$ else if (ItemStack.isSameItem(slot, stack) && slot.getCount() < maxStack) {
            //#endif
                int put = Math.min(remaining, maxStack - slot.getCount());
                slot.grow(put);
                remaining -= put;
            }
        }
        return stack.getCount() - remaining;
    }
}
