package me.libreh.worldless.command;

import com.mojang.brigadier.CommandDispatcher;
import me.libreh.worldless.Worldless;
import me.libreh.worldless.command.worldless.ReloadCommand;
import me.libreh.worldless.command.worldless.StopCommand;
import me.libreh.worldless.command.worldless.TimerCommand;
import net.minecraft.server.command.ServerCommandSource;

public final class Commands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                ModCommand.literal(Worldless.MOD_ID)
                        .then(new ReloadCommand().register())
                        .then(new TimerCommand().register())
                        .then(new StopCommand().register())
        );
    }
}