package dev.blinkwhite.remoteinventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.config.RemoteInvConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class RemoteInvCommand {

    private static final String PREFIX = Reference.MOD_ID + ".command.";

    //#if MC >= 11900
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext registryAccess) {
    //#else
    //$$ public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    //#endif
        //#if MC >= 11900
        var blockArg = net.minecraft.commands.arguments.blocks.BlockStateArgument.block(registryAccess);
        //#else
        //$$ var blockArg = net.minecraft.commands.arguments.blocks.BlockStateArgument.block();
        //#endif

        dispatcher.register(
            Commands.literal("remoteinv")
                .then(Commands.literal("distance")
                    .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 256.0))
                        .executes(RemoteInvCommand::setDistance)
                    )
                    .then(Commands.literal("enable")
                        .executes(ctx -> { RemoteInvConfig.setDistanceLimitEnabled(true);
                            send(ctx, PREFIX + "distance.enable"); return 1; })
                    )
                    .then(Commands.literal("disable")
                        .executes(ctx -> { RemoteInvConfig.setDistanceLimitEnabled(false);
                            send(ctx, PREFIX + "distance.disable"); return 1; })
                    )
                    .executes(RemoteInvCommand::getDistance)
                )
                .then(Commands.literal("whitelist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("block", blockArg)
                            .executes(RemoteInvCommand::whitelistAdd)
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("block", blockArg)
                            .executes(RemoteInvCommand::whitelistRemove)
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(RemoteInvCommand::whitelistList)
                    )
                    .then(Commands.literal("clear")
                        .executes(RemoteInvCommand::whitelistClear)
                    )
                    .then(Commands.literal("enable")
                        .executes(ctx -> { RemoteInvConfig.toggleWhitelist(true);
                            send(ctx, PREFIX + "whitelist.enable"); return 1; })
                    )
                    .then(Commands.literal("disable")
                        .executes(ctx -> { RemoteInvConfig.toggleWhitelist(false);
                            send(ctx, PREFIX + "whitelist.disable"); return 1; })
                    )
                )
                .then(Commands.literal("blacklist")
                    .then(Commands.literal("add")
                        .then(Commands.argument("block", blockArg)
                            .executes(RemoteInvCommand::blacklistAdd)
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("block", blockArg)
                            .executes(RemoteInvCommand::blacklistRemove)
                        )
                    )
                    .then(Commands.literal("list")
                        .executes(RemoteInvCommand::blacklistList)
                    )
                    .then(Commands.literal("clear")
                        .executes(RemoteInvCommand::blacklistClear)
                    )
                )
                .then(Commands.literal("config")
                    .executes(RemoteInvCommand::showConfig)
                )
        );
    }

    // ──────── distance ────────

    private static int setDistance(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "value");
        RemoteInvConfig.setMaxInteractionDistance(value);
        send(ctx, PREFIX + "distance.set", String.format("%.1f", value));
        return 1;
    }

    private static int getDistance(CommandContext<CommandSourceStack> ctx) {
        send(ctx, PREFIX + "distance.get", String.format("%.1f", RemoteInvConfig.getMaxInteractionDistance()));
        return 1;
    }

    // ──────── whitelist ────────

    private static int whitelistAdd(CommandContext<CommandSourceStack> ctx) {
        String blockId = getBlockId(ctx);
        RemoteInvConfig.addToWhitelist(blockId);
        send(ctx, PREFIX + "whitelist.add", blockId);
        return 1;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> ctx) {
        String blockId = getBlockId(ctx);
        RemoteInvConfig.removeFromWhitelist(blockId);
        send(ctx, PREFIX + "whitelist.remove", blockId);
        return 1;
    }

    private static int whitelistList(CommandContext<CommandSourceStack> ctx) {
        Set<String> wl = RemoteInvConfig.getWhitelist();
        if (wl.isEmpty()) {
            send(ctx, PREFIX + "whitelist.empty");
        } else {
            send(ctx, PREFIX + "whitelist.list", wl.size(), wl.toString());
        }
        return 1;
    }

    private static int whitelistClear(CommandContext<CommandSourceStack> ctx) {
        RemoteInvConfig.clearWhitelist();
        send(ctx, PREFIX + "whitelist.clear");
        return 1;
    }

    // ──────── blacklist ────────

    private static int blacklistAdd(CommandContext<CommandSourceStack> ctx) {
        String blockId = getBlockId(ctx);
        RemoteInvConfig.addToBlacklist(blockId);
        send(ctx, PREFIX + "blacklist.add", blockId);
        return 1;
    }

    private static int blacklistRemove(CommandContext<CommandSourceStack> ctx) {
        String blockId = getBlockId(ctx);
        RemoteInvConfig.removeFromBlacklist(blockId);
        send(ctx, PREFIX + "blacklist.remove", blockId);
        return 1;
    }

    private static int blacklistList(CommandContext<CommandSourceStack> ctx) {
        Set<String> bl = RemoteInvConfig.getBlacklist();
        if (bl.isEmpty()) {
            send(ctx, PREFIX + "blacklist.empty");
        } else {
            send(ctx, PREFIX + "blacklist.list", bl.size(), bl.toString());
        }
        return 1;
    }

    private static int blacklistClear(CommandContext<CommandSourceStack> ctx) {
        RemoteInvConfig.clearBlacklist();
        send(ctx, PREFIX + "blacklist.clear");
        return 1;
    }

    // ──────── config ────────

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        send(ctx, PREFIX + "config.distance", String.format("%.1f", RemoteInvConfig.getMaxInteractionDistance()));
        send(ctx, PREFIX + "config.distance_enabled", RemoteInvConfig.isDistanceLimitEnabled());
        send(ctx, PREFIX + "config.whitelist_enabled", RemoteInvConfig.isWhitelistEnabled());
        send(ctx, PREFIX + "config.whitelist", RemoteInvConfig.getWhitelist().toString());
        send(ctx, PREFIX + "config.blacklist", RemoteInvConfig.getBlacklist().toString());
        return 1;
    }

    // ──────── helpers ────────

    private static String getBlockId(CommandContext<CommandSourceStack> ctx) {
        var input = net.minecraft.commands.arguments.blocks.BlockStateArgument.getBlock(ctx, "block");
        var block = input.getState().getBlock();
        //#if MC >= 11900
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
        //#else
        //$$ return net.minecraft.core.Registry.BLOCK.getKey(block).toString();
        //#endif
    }

    private static void send(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        //#if MC >= 11900
        Component msg = Component.translatable(key, args);
        //#else
        //$$ Component msg = new net.minecraft.network.chat.TranslatableComponent(key, args);
        //#endif
        //#if MC >= 12000
        ctx.getSource().sendSuccess(() -> msg, false);
        //#else
        //$$ ctx.getSource().sendSuccess(msg, false);
        //#endif
    }
}
