//#if MC >= 12005
package dev.blinkwhite.remoteinventory.network.payload;

import dev.blinkwhite.remoteinventory.enums.ResultType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class RemoteExchangeResultPayload implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemoteExchangeResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    //#if MC >= 12101
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("remote-inventory-server", "exchange_result")
                    //#else
                    //$$ new net.minecraft.resources.ResourceLocation("remote-inventory-server", "exchange_result")
                    //#endif
            );

    public static final StreamCodec<ByteBuf, RemoteExchangeResultPayload> CODEC =
            StreamCodec.ofMember(RemoteExchangeResultPayload::write, RemoteExchangeResultPayload::decode);

    private final BlockPos pos;
    private final ResultType takeResult;
    private final int takenCount;
    private final int returnedCount;

    public RemoteExchangeResultPayload(BlockPos pos, ResultType takeResult, int takenCount, int returnedCount) {
        this.pos = pos; this.takeResult = takeResult; this.takenCount = takenCount; this.returnedCount = returnedCount;
    }

    public BlockPos getPos() { return pos; }
    public ResultType getTakeResult() { return takeResult; }
    public int getTakenCount() { return takenCount; }
    public int getReturnedCount() { return returnedCount; }

    public static RemoteExchangeResultPayload decode(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        return new RemoteExchangeResultPayload(
                wrapped.readBlockPos(), wrapped.readEnum(ResultType.class), wrapped.readVarInt(), wrapped.readVarInt());
    }

    public void write(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        wrapped.writeBlockPos(pos); wrapped.writeEnum(takeResult); wrapped.writeVarInt(takenCount); wrapped.writeVarInt(returnedCount);
    }

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
//#else
//$$ package dev.blinkwhite.remoteinventory.network.payload;
//$$ public class RemoteExchangeResultPayload {}
//#endif
