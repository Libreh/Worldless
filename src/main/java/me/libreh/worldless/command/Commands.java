package me.libreh.worldless.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import me.libreh.worldless.config.ConfigManager;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static me.libreh.worldless.Worldless.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
    private static final String MAIN_PERMISSION = "worldless.main";
    private static final String RELOAD_PERMISSION = "worldless.reload";
    private static final Text RELOAD_SUCCESS = Text.literal("Reloaded config!");
    private static final Text RELOAD_ERROR = Text.literal("Error occurred while reloading config!").formatted(Formatting.RED);

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("worldless")
                .then(createReloadCommand())
                .then(createTimerCommand())
                .then(createStopCommand())
        );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createReloadCommand() {
        return literal("reload")
                .requires(Commands::hasReloadPermission)
                .executes(context -> {
                    boolean success = ConfigManager.loadConfig();
                    context.getSource().sendFeedback(
                            () -> success ? RELOAD_SUCCESS : RELOAD_ERROR,
                            false
                    );
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static RequiredArgumentBuilder<ServerCommandSource, Integer> createTimerCommand() {
        return argument("timer", IntegerArgumentType.integer())
                .requires(Commands::hasMainPermission)
                .executes(context -> {
                    int timer = IntegerArgumentType.getInteger(context, "timer");
                    if (timer == 0) {
                        isCountdownRunning = false;
                    } else {
                        resetTimer = timer * 20 + 39;
                        worldTimer = resetTimer;
                        isCountdownRunning = true;
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> createStopCommand() {
        return literal("stop")
                .executes(context -> {
                    isCountdownRunning = false;
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static boolean hasReloadPermission(ServerCommandSource source) {
        return !source.isExecutedByPlayer() || hasPermission(source.getPlayer(), RELOAD_PERMISSION);
    }

    private static boolean hasMainPermission(ServerCommandSource source) {
        return !source.isExecutedByPlayer() || hasPermission(source.getPlayer(), MAIN_PERMISSION);
    }

    private static boolean hasPermission(ServerPlayerEntity player, String permission) {
        return Permissions.check(player, permission) || player.hasPermissionLevel(4);
    }
}
