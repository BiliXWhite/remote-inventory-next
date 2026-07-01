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