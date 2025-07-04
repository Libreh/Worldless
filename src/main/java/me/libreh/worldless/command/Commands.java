package me.libreh.worldless.command;

import com.mojang.brigadier.CommandDispatcher;
import me.libreh.worldless.command.worldless.ReloadCommand;
import me.libreh.worldless.command.worldless.StopCommand;
import me.libreh.worldless.command.worldless.TimerCommand;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Registers all mod commands under the main mod literal.
 */
public final class Commands {
    /**
     * Registers all commands to the dispatcher.
     * @param dispatcher the command dispatcher
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                ModCommand.literal("worldless")
                        .then(new ReloadCommand().register())
                        .then(new TimerCommand().register())
                        .then(new StopCommand().register())
        );
    }
}