package dev.blinkwhite.remoteinventory.network.payload;

import dev.blinkwhite.remoteinventory.enums.ResultType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RemoteExchangeResultPayload(BlockPos pos, ResultType takeResult, int takenCount, int returnedCount,
                                          List<SlotSnapshot> inventoryDelta) {
    public record SlotSnapshot(int slotIndex, String itemId, int count) {
    }

    public RemoteExchangeResultPayload(BlockPos pos, ResultType takeResult,
                                       int takenCount, int returnedCount,
                                       List<SlotSnapshot> inventoryDelta) {
        this.pos = pos;
        this.takeResult = takeResult;
        this.takenCount = takenCount;
        this.returnedCount = returnedCount;
        this.inventoryDelta = inventoryDelta != null ? inventoryDelta : Collections.emptyList();
    }

    public static RemoteExchangeResultPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        ResultType takeResult = ResultType.values()[buf.readVarInt()];
        int takenCount = buf.readVarInt();
        int returnedCount = buf.readVarInt();
        int deltaSize = buf.readVarInt();
        List<SlotSnapshot> delta = new ArrayList<>(deltaSize);
        for (int i = 0; i < deltaSize; i++) {
            int slotIndex = buf.readVarInt();
            String itemId = buf.readUtf();
            int count = buf.readVarInt();
            delta.add(new SlotSnapshot(slotIndex, itemId, count));
        }
        return new RemoteExchangeResultPayload(pos, takeResult, takenCount, returnedCount, delta);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(takeResult.ordinal());
        buf.writeVarInt(takenCount);
        buf.writeVarInt(returnedCount);
        buf.writeVarInt(inventoryDelta.size());
        for (SlotSnapshot s : inventoryDelta) {
            buf.writeVarInt(s.slotIndex());
            buf.writeUtf(s.itemId());
            buf.writeVarInt(s.count());
        }
    }
}
