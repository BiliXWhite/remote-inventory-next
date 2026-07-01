package dev.blinkwhite.remoteinventory.network.handler;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.container.ExchangeUtils;
import dev.blinkwhite.remoteinventory.enums.ResultType;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangePayload;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class RemoteExchangeHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            RemoteExchangePayload.TYPE,
            (payload, context) -> {
                //#if MC >= 12100
                MinecraftServer server = context.server();
                //#else
                //$$ MinecraftServer server = context.player().getServer();
                //#endif
                handle(server, context.player(), payload);
            }
        );
    }

    private static void handle(MinecraftServer server, ServerPlayer player,
                                RemoteExchangePayload payload) {
        server.execute(() -> {
            try {
                byte[] before = ExchangeUtils.snapshotInventory(player);
                int returned = ExchangeUtils.returnItems(player, payload.getReturnPos(),
                        payload.getReturnItemId(), payload.getReturnCount());
                var taken = ExchangeUtils.takeItems(player, payload.getTakePos(),
                        payload.getTakeItemId(), payload.getTakeSlot());
                byte[] after = ExchangeUtils.snapshotInventory(player);
                var delta = ExchangeUtils.computeDelta(player, before, after);

                ServerPlayNetworking.send(player,
                    new RemoteExchangeResultPayload(payload.getTakePos(),
                        taken != null ? taken.type() : ResultType.SUCCESS,
                        taken != null ? taken.extractedCount() : 0,
                        returned, delta));
            } catch (Exception e) {
                Reference.LOGGER.error("Exchange error for {}: {}",
                        player.getName().getString(), e.getMessage(), e);
                ServerPlayNetworking.send(player,
                    new RemoteExchangeResultPayload(payload.getTakePos(),
                        ResultType.INTERNAL_ERROR, 0, 0, null));
            }
        });
    }
}