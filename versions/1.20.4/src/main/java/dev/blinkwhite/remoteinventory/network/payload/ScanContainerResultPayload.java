package dev.blinkwhite.remoteinventory.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ScanContainerResultPayload(BlockPos pos, List<SlotEntry> entries) {
    public record SlotEntry(int slot, String itemId, int count) {
    }

    public ScanContainerResultPayload(BlockPos pos, List<SlotEntry> entries) {
        this.pos = pos;
        this.entries = entries != null ? entries : List.of();
    }

    public static ScanContainerResultPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<SlotEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int slot = buf.readVarInt();
            String itemId = buf.readResourceLocation().toString();
            int count = buf.readVarInt();
            entries.add(new SlotEntry(slot, itemId, count));
        }
        return new ScanContainerResultPayload(pos, entries);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(entries.size());
        for (SlotEntry e : entries) {
            buf.writeVarInt(e.slot());
            buf.writeResourceLocation(new ResourceLocation(e.itemId()));
            buf.writeVarInt(e.count());
        }
    }
}
