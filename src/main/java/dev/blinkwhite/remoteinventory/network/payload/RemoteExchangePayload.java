package dev.blinkwhite.remoteinventory.network.payload;

import dev.blinkwhite.remoteinventory.Reference;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import lombok.NonNull;

@Getter
public class RemoteExchangePayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoteExchangePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    //#if MC >= 12101
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(Reference.MOD_ID, "exchange")
                    //#else
                    //$$ new net.minecraft.resources.ResourceLocation(Reference.MOD_ID, "exchange")
                    //#endif
            );

    public static final StreamCodec<ByteBuf, RemoteExchangePayload> CODEC =
            StreamCodec.ofMember(RemoteExchangePayload::write, RemoteExchangePayload::decode);

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

    public static RemoteExchangePayload decode(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        return new RemoteExchangePayload(
                wrapped.readBlockPos(), wrapped.readUtf(), wrapped.readVarInt(),
                wrapped.readBlockPos(), wrapped.readUtf(), wrapped.readVarInt());
    }

    public void write(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        wrapped.writeBlockPos(takePos);
        wrapped.writeUtf(takeItemId);
        wrapped.writeVarInt(takeSlot);
        wrapped.writeBlockPos(returnPos);
        wrapped.writeUtf(returnItemId);
        wrapped.writeVarInt(returnCount);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
