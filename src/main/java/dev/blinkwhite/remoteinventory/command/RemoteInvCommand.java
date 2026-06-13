package dev.blinkwhite.remoteinventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.blinkwhite.remoteinventory.Reference;
import dev.blinkwhite.remoteinventory.config.RemoteInvConfig;
import dev.blinkwhite.remoteinventory.util.Translations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public class RemoteInvCommand {

    private static final String PREFIX = "remote-inventory-server.command.";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                        .then(Commands.argument("block", StringArgumentType.word())
                            .executes(RemoteInvCommand::whitelistAdd)
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.word())
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
                        .then(Commands.argument("block", StringArgumentType.word())
                            .executes(RemoteInvCommand::blacklistAdd)
                        )
                    )
                    .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.word())
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
        send(ctx, PREFIX + "distance.set", value);
        return 1;
    }

    private static int getDistance(CommandContext<CommandSourceStack> ctx) {
        send(ctx, PREFIX + "distance.get", RemoteInvConfig.getMaxInteractionDistance());
        return 1;
    }

    // ──────── whitelist ────────

    private static int whitelistAdd(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "block");
        RemoteInvConfig.addToWhitelist(id);
        send(ctx, PREFIX + "whitelist.add", id);
        return 1;
    }

    private static int whitelistRemove(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "block");
        RemoteInvConfig.removeFromWhitelist(id);
        send(ctx, PREFIX + "whitelist.remove", id);
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
        String id = StringArgumentType.getString(ctx, "block");
        RemoteInvConfig.addToBlacklist(id);
        send(ctx, PREFIX + "blacklist.add", id);
        return 1;
    }

    private static int blacklistRemove(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "block");
        RemoteInvConfig.removeFromBlacklist(id);
        send(ctx, PREFIX + "blacklist.remove", id);
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
        send(ctx, PREFIX + "config.distance", RemoteInvConfig.getMaxInteractionDistance());
        send(ctx, PREFIX + "config.distance_enabled", RemoteInvConfig.isDistanceLimitEnabled());
        send(ctx, PREFIX + "config.whitelist_enabled", RemoteInvConfig.isWhitelistEnabled());
        send(ctx, PREFIX + "config.whitelist", RemoteInvConfig.getWhitelist().toString());
        send(ctx, PREFIX + "config.blacklist", RemoteInvConfig.getBlacklist().toString());
        return 1;
    }

    // ──────── helpers ────────

    private static void send(CommandContext<CommandSourceStack> ctx, String key, Object... args) {
        String language = "en_us";
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            language = Translations.getPlayerLanguage(player);
        } catch (Exception ignored) {}
        String text = Translations.translate(language, key, args);
        //#if MC >= 11900
        Component msg = Component.literal(text);
        //#else
        //$$ Component msg = new net.minecraft.network.chat.TextComponent(text);
        //#endif
        //#if MC >= 12000
        ctx.getSource().sendSuccess(() -> msg, false);
        //#else
        //$$ ctx.getSource().sendSuccess(msg, false);
        //#endif
    }
}