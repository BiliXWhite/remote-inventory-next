package dev.blinkwhite.remoteinventory.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientRemoteInventoryMod implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        RemoteInventoryClient.register();
        ContainerCachePersister.register();
    }
}
