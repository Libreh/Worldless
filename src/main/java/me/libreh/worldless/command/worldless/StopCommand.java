package me.libreh.worldless.command.worldless;

import com.mojang.brigadier.context.CommandContext;
import me.libreh.worldless.Worldless;
import me.libreh.worldless.command.BaseCommand;
import net.minecraft.server.command.ServerCommandSource;

public class StopCommand extends BaseCommand {
    public StopCommand() {
        super("stop");
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) {
        Worldless.getWorldManager().stopCountdown();
        return 1;
    }
}
