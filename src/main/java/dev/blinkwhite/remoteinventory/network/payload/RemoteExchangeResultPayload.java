package dev.blinkwhite.remoteinventory.network.payload;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.enums.ResultType;
import io.netty.buffer.ByteBuf;
import lombok.NonNull;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RemoteExchangeResultPayload(BlockPos pos, ResultType takeResult, int takenCount, int returnedCount,
                                          List<SlotSnapshot> inventoryDelta) implements CustomPacketPayload {
    public static final Type<RemoteExchangeResultPayload> TYPE =
            new Type<>(
                    //#if MC >= 12101
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(Reference.MOD_ID, "exchange_result")
                    //#else
                    //$$ new net.minecraft.resources.ResourceLocation(Reference.MOD_ID, "exchange_result")
                    //#endif
            );

    public static final StreamCodec<ByteBuf, RemoteExchangeResultPayload> CODEC =
            StreamCodec.ofMember(RemoteExchangeResultPayload::write, RemoteExchangeResultPayload::decode);

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

    public static RemoteExchangeResultPayload decode(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        BlockPos pos = wrapped.readBlockPos();
        ResultType takeResult = wrapped.readEnum(ResultType.class);
        int takenCount = wrapped.readVarInt();
        int returnedCount = wrapped.readVarInt();
        int deltaSize = wrapped.readVarInt();
        List<SlotSnapshot> delta = new ArrayList<>(deltaSize);
        for (int i = 0; i < deltaSize; i++) {
            int slotIndex = wrapped.readVarInt();
            String itemId = wrapped.readUtf();
            int count = wrapped.readVarInt();
            delta.add(new SlotSnapshot(slotIndex, itemId, count));
        }
        return new RemoteExchangeResultPayload(pos, takeResult, takenCount, returnedCount, delta);
    }

    public void write(ByteBuf buf) {
        FriendlyByteBuf wrapped = (FriendlyByteBuf) buf;
        wrapped.writeBlockPos(pos);
        wrapped.writeEnum(takeResult);
        wrapped.writeVarInt(takenCount);
        wrapped.writeVarInt(returnedCount);
        wrapped.writeVarInt(inventoryDelta.size());
        for (SlotSnapshot s : inventoryDelta) {
            wrapped.writeVarInt(s.slotIndex());
            wrapped.writeUtf(s.itemId());
            wrapped.writeVarInt(s.count());
        }
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
