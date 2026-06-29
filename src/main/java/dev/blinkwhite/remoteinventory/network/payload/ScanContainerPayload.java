package dev.blinkwhite.remoteinventory.network.payload;

import dev.blinkwhite.remoteinventory.Reference;
import io.netty.buffer.ByteBuf;
import lombok.NonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ScanContainerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ScanContainerPayload> TYPE = new Type<>(
            //#if MC >= 12105
            net.minecraft.resources.Identifier.fromNamespaceAndPath(Reference.MOD_ID, "scan_container")
            //#elseif MC >= 12101
            //$$ net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "scan_container")
            //#else
            //$$ new net.minecraft.resources.ResourceLocation(Reference.MOD_ID, "scan_container")
            //#endif
    );

    public static ScanContainerPayload decode(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        return new ScanContainerPayload(wrapped.readBlockPos());
    }

    public void write(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        wrapped.writeBlockPos(pos);
    }

    @Override
    public @NonNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
