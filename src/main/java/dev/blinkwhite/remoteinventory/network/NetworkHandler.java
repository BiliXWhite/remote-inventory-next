package dev.blinkwhite.remoteinventory.network;

import dev.blinkwhite.remoteinventory.Reference;
//#if MC >= 12005
import dev.blinkwhite.remoteinventory.network.handler.RemoteExchangeHandler;
//#endif
import dev.blinkwhite.remoteinventory.network.handler.ScanContainerHandler;

//#if MC >= 12005
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangePayload;
import dev.blinkwhite.remoteinventory.network.payload.RemoteExchangeResultPayload;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerPayload;
import dev.blinkwhite.remoteinventory.network.payload.ScanContainerResultPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
//#endif

public class NetworkHandler {
    public static void registerReceivers() {
        Reference.LOGGER.info("Registering remote inventory packet receivers...");

        //#if MC >= 12005
        PayloadTypeRegistry.playC2S().register(RemoteExchangePayload.TYPE,
                StreamCodec.ofMember(RemoteExchangePayload::write, RemoteExchangePayload::decode));
        PayloadTypeRegistry.playS2C().register(RemoteExchangeResultPayload.TYPE,
                StreamCodec.ofMember(RemoteExchangeResultPayload::write, RemoteExchangeResultPayload::decode));
        PayloadTypeRegistry.playC2S().register(ScanContainerPayload.TYPE,
                StreamCodec.ofMember(ScanContainerPayload::write, ScanContainerPayload::decode));
        PayloadTypeRegistry.playS2C().register(ScanContainerResultPayload.TYPE,
                StreamCodec.ofMember(ScanContainerResultPayload::write, ScanContainerResultPayload::decode));
        //#endif

        //#if MC >= 12005
        RemoteExchangeHandler.register();
        //#endif
        ScanContainerHandler.register();
    }
}
