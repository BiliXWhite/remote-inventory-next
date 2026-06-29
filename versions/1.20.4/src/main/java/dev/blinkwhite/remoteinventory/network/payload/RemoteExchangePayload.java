package dev.blinkwhite.remoteinventory.network.payload;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

@Getter
public class RemoteExchangePayload {
    private final BlockPos takePos;
    private final String takeItemId;
    private final int takeSlot;
    private final BlockPos returnPos;
    private final String returnItemId;
    private final int returnCount;

    public RemoteExchangePayload(BlockPos takePos, String takeItemId, int takeSlot,
                                  BlockPos returnPos, String returnItemId, int returnCount) {
        this.takePos = takePos;
        this.takeItemId = takeItemId != null ? takeItemId : "";
        this.takeSlot = takeSlot;
        this.returnPos = returnPos != null ? returnPos : takePos;
        this.returnItemId = returnItemId != null ? returnItemId : "";
        this.returnCount = returnCount;
    }

    public static RemoteExchangePayload decode(FriendlyByteBuf buf) {
        return new RemoteExchangePayload(
                buf.readBlockPos(), buf.readUtf(), buf.readVarInt(),
                buf.readBlockPos(), buf.readUtf(), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(takePos);
        buf.writeUtf(takeItemId);
        buf.writeVarInt(takeSlot);
        buf.writeBlockPos(returnPos);
        buf.writeUtf(returnItemId);
        buf.writeVarInt(returnCount);
    }
}
