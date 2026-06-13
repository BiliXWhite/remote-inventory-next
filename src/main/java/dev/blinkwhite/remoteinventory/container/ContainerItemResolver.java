package dev.blinkwhite.remoteinventory.container;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.config.RemoteInvConfig;
import dev.blinkwhite.remoteinventory.enums.ResultType;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

public class ContainerItemResolver {

    public record ResolveResult(ResultType type, int extractedCount) {}

    public static ResolveResult resolveItem(ServerPlayer player, BlockPos pos,
                                          String itemIdStr, int slot) {
        Level level = getPlayerLevel(player);

        if (RemoteInvConfig.isDistanceLimitEnabled()) {
            double maxDist = RemoteInvConfig.getMaxInteractionDistance();
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance > maxDist * maxDist) {
                return new ResolveResult(ResultType.PLAYER_TOO_FAR, 0);
            }
        }

        if (!level.isLoaded(pos)) {
            return new ResolveResult(ResultType.CONTAINER_NOT_LOADED, 0);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return new ResolveResult(ResultType.CONTAINER_NOT_FOUND, 0);
        }

        if (!(blockEntity instanceof Container container)) {
            return new ResolveResult(ResultType.NOT_A_CONTAINER, 0);
        }

        if (!isBlockAllowed(level, pos)) {
            return new ResolveResult(ResultType.NOT_A_CONTAINER, 0);
        }

        if (slot < 0 || slot >= container.getContainerSize()) {
            return new ResolveResult(ResultType.SLOT_EMPTY, 0);
        }

        ItemStack stackInSlot = container.getItem(slot);
        if (stackInSlot.isEmpty()) {
            return new ResolveResult(ResultType.SLOT_EMPTY, 0);
        }

        Item requestedItem = resolveItemFromId(itemIdStr);
        if (requestedItem == null) {
            return new ResolveResult(ResultType.ITEM_NOT_MATCH, 0);
        }

        if (!stackInSlot.is(requestedItem)) {
            return new ResolveResult(ResultType.ITEM_NOT_MATCH, 0);
        }

        return giveToPlayer(player, container, slot, stackInSlot);
    }

    private static Item resolveItemFromId(String itemIdStr) {
        //#if MC >= 12105
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(itemIdStr);
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        //#elseif MC >= 12101
        //$$ net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.parse(itemIdStr);
        //$$ return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        //#else
        //$$ net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation(itemIdStr);
        //$$ return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        //#endif
    }

    private static boolean isBlockAllowed(Level level, BlockPos pos) {
        return RemoteInvConfig.isBlockAllowed(
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    private static Level getPlayerLevel(ServerPlayer player) {
        //#if MC >= 12000
        return player.level();
        //#else
        //$$ return player.level;
        //#endif
    }

    private static ResolveResult giveToPlayer(ServerPlayer player, Container container,
                                                int slot, ItemStack stack) {
        try {
            int maxAddable = computeMaxAddable(player.getInventory(), stack);
            if (maxAddable <= 0)
                return new ResolveResult(ResultType.INVENTORY_FULL, 0);

            int toExtract = Math.min(stack.getCount(), maxAddable);
            ItemStack extracted = container.removeItem(slot, toExtract);
            int initialCount = extracted.getCount();
            if (initialCount <= 0)
                return new ResolveResult(ResultType.INTERNAL_ERROR, 0);

            player.getInventory().add(extracted);
            int actuallyAdded = initialCount - extracted.getCount();
            if (!extracted.isEmpty())
                player.drop(extracted, false);

            container.setChanged();
            ResultType type = toExtract < stack.getCount() ? ResultType.PARTIAL : ResultType.SUCCESS;
            return new ResolveResult(type, actuallyAdded);
        } catch (Exception e) {
            Reference.LOGGER.error("Error giving item to player: {}", e.getMessage(), e);
            return new ResolveResult(ResultType.INTERNAL_ERROR, 0);
        }
    }

    private static int computeMaxAddable(net.minecraft.world.entity.player.Inventory inventory, ItemStack template) {
        int maxStack = template.getMaxStackSize();
        int canAdd = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack existing = inventory.getItem(i);
            if (existing.isEmpty()) {
                canAdd += maxStack;
            } else {
                //#if MC >= 12005
                if (ItemStack.isSameItemSameComponents(existing, template))
                    canAdd += maxStack - existing.getCount();
                //#else
                //$$ if (ItemStack.isSameItem(existing, template))
                //$$     canAdd += maxStack - existing.getCount();
                //#endif
            }
        }
        return canAdd;
    }

    public static java.util.List<ScanContainerResultPayload.SlotEntry> scanContainer(
            ServerPlayer player, BlockPos pos) {
        Level level = getPlayerLevel(player);

        if (RemoteInvConfig.isDistanceLimitEnabled()) {
            double maxDist = RemoteInvConfig.getMaxInteractionDistance();
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance > maxDist * maxDist) {
                return java.util.List.of();
            }
        }
        if (!level.isLoaded(pos)) {
            return java.util.List.of();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            return java.util.List.of();
        }

        if (!isBlockAllowed(level, pos)) {
            return java.util.List.of();
        }

        java.util.List<ScanContainerResultPayload.SlotEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                entries.add(new ScanContainerResultPayload.SlotEntry(i, itemId, stack.getCount()));
            }
        }
        return entries;
    }
}