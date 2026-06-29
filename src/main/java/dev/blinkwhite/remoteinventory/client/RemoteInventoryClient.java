//#if MC >= 12005
package dev.blinkwhite.remoteinventory.client;

import lombok.Setter;
import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.enums.ResultType;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangePayload;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerPayload;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class RemoteInventoryClient {
    @Setter
    private static ExchangeResultCallback exchangeCallback;
    @Setter
    private static Consumer<ScanContainerResultPayload> scanResultCallback;

    @FunctionalInterface
    public interface ExchangeResultCallback {
        void accept(BlockPos pos, ResultType takeResult, int takenCount, int returnedCount,
                    List<RemoteExchangeResultPayload.SlotSnapshot> inventoryDelta);
    }

    public static void register() {
        try {
            PayloadTypeRegistry.playC2S().register(RemoteExchangePayload.TYPE, RemoteExchangePayload.CODEC);
            PayloadTypeRegistry.playS2C().register(RemoteExchangeResultPayload.TYPE, RemoteExchangeResultPayload.CODEC);
            PayloadTypeRegistry.playC2S().register(ScanContainerPayload.TYPE,
                    StreamCodec.ofMember(ScanContainerPayload::write, ScanContainerPayload::decode));
            PayloadTypeRegistry.playS2C().register(ScanContainerResultPayload.TYPE,
                    StreamCodec.ofMember(ScanContainerResultPayload::write, ScanContainerResultPayload::decode));
        } catch (IllegalArgumentException ignored) {
            Reference.LOGGER.warn("Failed to register remote inventory payloads, maybe already registered?");
        }

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            ClientPlayNetworking.registerReceiver(RemoteExchangeResultPayload.TYPE, (payload, context) -> {
                if (exchangeCallback != null) {
                    context.client().execute(() ->
                        exchangeCallback.accept(payload.pos(),
                            payload.takeResult(), payload.takenCount(),
                            payload.returnedCount(), payload.inventoryDelta()));
                }
            });
            ClientPlayNetworking.registerReceiver(ScanContainerResultPayload.TYPE, (payload, context) -> {
                if (scanResultCallback != null) {
                    context.client().execute(() -> scanResultCallback.accept(payload));
                }
            });
        });
    }

    public static void sendExchange(BlockPos takePos,
                                     String takeItemId, int takeSlot,
                                     BlockPos returnPos,
                                     String returnItemId, int returnCount) {
        ClientPlayNetworking.send(new RemoteExchangePayload(
                takePos, takeItemId, takeSlot,
                returnPos, returnItemId, returnCount));
    }

    public static void sendScanContainerRequest(BlockPos containerPos) {
        ClientPlayNetworking.send(new ScanContainerPayload(containerPos));
    }
}
//#else
//$$ package dev.blinkwhite.remoteinventory.client;
//$$
//$$ import dev.blinkwhite.remoteinventory.Reference;
//$$ import dev.blinkwhite.remoteinventory.enums.ResultType;
//$$ import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
//$$ import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
//$$ import io.netty.buffer.Unpooled;
//$$ import net.fabricmc.api.EnvType;
//$$ import net.fabricmc.api.Environment;
//$$ import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//$$ import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//$$ import net.minecraft.core.BlockPos;
//$$ import net.minecraft.network.FriendlyByteBuf;
//$$ import net.minecraft.resources.ResourceLocation;
//$$
//$$ import java.util.ArrayList;
//$$ import java.util.List;
//$$ import java.util.function.Consumer;
//$$
//$$ @Environment(EnvType.CLIENT)
//$$ public class RemoteInventoryClient {
//$$     private static ExchangeResultCallback exchangeCallback;
//$$     private static Consumer<ScanContainerResultPayload> scanResultCallback;
//$$
//$$     @FunctionalInterface
//$$     public interface ExchangeResultCallback {
//$$         void accept(BlockPos pos, ResultType takeResult, int takenCount, int returnedCount,
//$$                     List<RemoteExchangeResultPayload.SlotSnapshot> inventoryDelta);
//$$     }
//$$
//$$     public static void setExchangeCallback(ExchangeResultCallback callback) {
//$$         exchangeCallback = callback;
//$$     }
//$$
//$$     public static void setScanResultCallback(Consumer<ScanContainerResultPayload> callback) {
//$$         scanResultCallback = callback;
//$$     }
//$$
//$$     public static void register() {
//$$         ClientPlayConnectionEvents.INIT.register((handler, client) -> {
//$$             ClientPlayNetworking.registerReceiver(
//$$                 new ResourceLocation(Reference.MOD_ID, "exchange_result"),
//$$                 (client1, handler1, buf, responseSender) -> {
//$$                     BlockPos pos = buf.readBlockPos();
//$$                     ResultType takeResult = ResultType.values()[buf.readVarInt()];
//$$                     int takenCount = buf.readVarInt();
//$$                     int returnedCount = buf.readVarInt();
//$$                     int deltaSize = buf.readVarInt();
//$$                     List<RemoteExchangeResultPayload.SlotSnapshot> delta = new ArrayList<>(deltaSize);
//$$                     for (int i = 0; i < deltaSize; i++) {
//$$                         int slotIndex = buf.readVarInt();
//$$                         String itemId = buf.readUtf();
//$$                         int count = buf.readVarInt();
//$$                         delta.add(new RemoteExchangeResultPayload.SlotSnapshot(slotIndex, itemId, count));
//$$                     }
//$$                     if (exchangeCallback != null) {
//$$                         client1.execute(() -> exchangeCallback.accept(pos, takeResult, takenCount, returnedCount, delta));
//$$                     }
//$$                 }
//$$             );
//$$             ClientPlayNetworking.registerReceiver(
//$$                 new ResourceLocation(Reference.MOD_ID, "scan_container_result"),
//$$                 (client1, handler1, buf, responseSender) -> {
//$$                     BlockPos pos = buf.readBlockPos();
//$$                     int size = buf.readVarInt();
//$$                     List<ScanContainerResultPayload.SlotEntry> entries = new ArrayList<>(size);
//$$                     for (int i = 0; i < size; i++) {
//$$                         int slot = buf.readVarInt();
//$$                         String itemId = buf.readResourceLocation().toString();
//$$                         int count = buf.readVarInt();
//$$                         entries.add(new ScanContainerResultPayload.SlotEntry(slot, itemId, count));
//$$                     }
//$$                     ScanContainerResultPayload payload =
//$$                             new ScanContainerResultPayload(pos, entries);
//$$                     if (scanResultCallback != null) {
//$$                         client1.execute(() -> scanResultCallback.accept(payload));
//$$                     }
//$$                 }
//$$             );
//$$         });
//$$     }
//$$
//$$     public static void sendExchange(BlockPos takePos, String takeItemId, int takeSlot,
//$$                                      BlockPos returnPos, String returnItemId, int returnCount) {
//$$         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
//$$         buf.writeBlockPos(takePos);
//$$         buf.writeUtf(takeItemId);
//$$         buf.writeVarInt(takeSlot);
//$$         buf.writeBlockPos(returnPos);
//$$         buf.writeUtf(returnItemId);
//$$         buf.writeVarInt(returnCount);
//$$         ClientPlayNetworking.send(
//$$             new ResourceLocation(Reference.MOD_ID, "exchange"),
//$$             buf
//$$         );
//$$     }
//$$
//$$     public static void sendScanContainerRequest(BlockPos containerPos) {
//$$         FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
//$$         buf.writeBlockPos(containerPos);
//$$         ClientPlayNetworking.send(
//$$             new ResourceLocation(Reference.MOD_ID, "scan_container"),
//$$             buf
//$$         );
//$$     }
//$$ }
//#endif
