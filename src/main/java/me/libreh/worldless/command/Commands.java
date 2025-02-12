package me.libreh.worldless.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import me.libreh.worldless.config.Config;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static me.libreh.worldless.Worldless.*;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Commands {
    public static void worldlessCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("worldless")
                .then(literal("reload")
                        .requires(source -> (source.isExecutedByPlayer() &&
                                hasPermission(source.getPlayer(), "worldless.reload")) || (!source.isExecutedByPlayer())
                        )
                        .executes(context -> {
                            Config.saveConfig();

                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(argument("timer", IntegerArgumentType.integer())
                        .requires(source -> (source.isExecutedByPlayer() &&
                                hasPermission(source.getPlayer(), "worldless.main")) || (!source.isExecutedByPlayer())
                        )
                        .executes(context -> {
                            var timer = IntegerArgumentType.getInteger(context, "timer");
                            if (timer == 0) {
                                stopTimer();
                            } else {
                                resetTimer = timer * 20 + 40 - 1;
                                worldTimer = resetTimer;
                                startTimer = true;
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(literal("stop")
                        .executes(context -> {
                            stopTimer();

                            return Command.SINGLE_SUCCESS;
                        })
                )
        );
    }

    private static boolean hasPermission(ServerPlayerEntity player, String key) {
        return Permissions.check(player, key) || player.hasPermissionLevel(1) || player.hasPermissionLevel(2) ||
                player.hasPermissionLevel(3) || player.hasPermissionLevel(4);
    }
}
