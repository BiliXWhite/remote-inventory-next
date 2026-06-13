package dev.blinkwhite.remoteinventory;

import dev.blinkwhite.remoteinventory.command.RemoteInvCommand;
import dev.blinkwhite.remoteinventory.config.RemoteInvConfig;
import dev.blinkwhite.remoteinventory.network.NetworkHandler;
import dev.blinkwhite.remoteinventory.util.Translations;
import net.fabricmc.api.ModInitializer;
//#if MC >= 11900
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//#else
//$$ import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#endif

public class RemoteInventoryMod implements ModInitializer {
    @Override
    public void onInitialize() {
        Reference.LOGGER.info("Initializing Remote Inventory Server...");
        RemoteInvConfig.load();
        Translations.init();
        NetworkHandler.registerReceivers();
        //#if MC >= 11900
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> RemoteInvCommand.register(dispatcher)
        );
        //#else
        //$$ CommandRegistrationCallback.EVENT.register(
        //$$     (dispatcher, dedicated) -> RemoteInvCommand.register(dispatcher)
        //$$ );
        //#endif
        Reference.LOGGER.info("Remote Inventory Server initialized.");
    }
}