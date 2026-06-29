package dev.blinkwhite.remoteinventory.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record ScanContainerPayload(BlockPos pos) {

    public static ScanContainerPayload decode(FriendlyByteBuf buf) {
        return new ScanContainerPayload(buf.readBlockPos());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }
}
