package dev.blinkwhite.remoteinventory.network.handler;

import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.container.ContainerItemResolver;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerPayload;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ScanContainerHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
            ScanContainerPayload.TYPE,
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
                                ScanContainerPayload payload) {
        server.execute(() -> {
            try {
                List<ScanContainerResultPayload.SlotEntry> entries =
                        ContainerItemResolver.scanContainer(player, payload.pos());
                String dimension = getDimensionId(player);
                ServerPlayNetworking.send(player,
                        new ScanContainerResultPayload(dimension, payload.pos(), entries));
            } catch (Exception e) {
                Reference.LOGGER.error(
                    "Error scanning container at {} for {}: {}",
                    payload.pos(), player.getName().getString(), e.getMessage(), e
                );
                String dimension = getDimensionId(player);
                ServerPlayNetworking.send(player,
                        new ScanContainerResultPayload(dimension, payload.pos(), List.of()));
            }
        });
    }

    private static String getDimensionId(ServerPlayer player) {
        return player.level().dimension().toString();
    }
}