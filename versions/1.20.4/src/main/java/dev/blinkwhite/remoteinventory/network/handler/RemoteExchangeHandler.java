package dev.blinkwhite.remoteinventory.network.handler;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.container.ExchangeUtils;
import dev.blinkwhite.remoteinventory.enums.ResultType;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class RemoteExchangeHandler {
    private static final ResourceLocation CHANNEL_ID = new ResourceLocation(Reference.MOD_ID, "exchange");
    private static final ResourceLocation RESULT_ID = new ResourceLocation(Reference.MOD_ID, "exchange_result");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL_ID,
            (server, player, handler, buf, responseSender) -> {
                BlockPos takePos = buf.readBlockPos();
                String takeItemId = buf.readUtf(32767);
                int takeSlot = buf.readVarInt();
                BlockPos returnPos = buf.readBlockPos();
                String returnItemId = buf.readUtf(32767);
                int returnCount = buf.readVarInt();
                server.execute(() -> {
                    try {
                        byte[] before = ExchangeUtils.snapshotInventory(player);
                        int returned = ExchangeUtils.returnItems(player, returnPos, returnItemId, returnCount);
                        var taken = ExchangeUtils.takeItems(player, takePos, takeItemId, takeSlot);
                        byte[] after = ExchangeUtils.snapshotInventory(player);
                        List<RemoteExchangeResultPayload.SlotSnapshot> delta = ExchangeUtils.computeDelta(player, before, after);

                        FriendlyByteBuf resultBuf = PacketByteBufs.create();
                        resultBuf.writeBlockPos(takePos);
                        resultBuf.writeVarInt(taken != null ? taken.type().ordinal() : ResultType.SUCCESS.ordinal());
                        resultBuf.writeVarInt(taken != null ? taken.extractedCount() : 0);
                        resultBuf.writeVarInt(returned);
                        resultBuf.writeVarInt(delta.size());
                        for (RemoteExchangeResultPayload.SlotSnapshot s : delta) {
                            resultBuf.writeVarInt(s.slotIndex());
                            resultBuf.writeUtf(s.itemId());
                            resultBuf.writeVarInt(s.count());
                        }
                        responseSender.sendPacket(ServerPlayNetworking.createS2CPacket(RESULT_ID, resultBuf));
                    } catch (Exception e) {
                        Reference.LOGGER.error("Exchange error for {}: {}", player.getName().getString(), e.getMessage(), e);
                        FriendlyByteBuf errorBuf = PacketByteBufs.create();
                        errorBuf.writeBlockPos(takePos);
                        errorBuf.writeVarInt(ResultType.INTERNAL_ERROR.ordinal());
                        errorBuf.writeVarInt(0); errorBuf.writeVarInt(0); errorBuf.writeVarInt(0);
                        responseSender.sendPacket(ServerPlayNetworking.createS2CPacket(RESULT_ID, errorBuf));
                    }
                });
            }
        );
    }
}