package me.libreh.worldless.command.worldless;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.libreh.worldless.WorldlessMod;
import me.libreh.worldless.command.BaseCommand;
import me.libreh.worldless.util.TimeUtils;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.argument;


public class TimerCommand extends BaseCommand {
    public TimerCommand() {
        super("timer");
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) {
        String durationInput = StringArgumentType.getString(context, "duration");
        long seconds = TimeUtils.parseDuration(durationInput);
        if (seconds == 0) {
            WorldlessMod.getWorldManager().stopCountdown();
        } else {
            WorldlessMod.getWorldManager().setCountdownTimer(seconds);
        }
        return 1;
    }

    public LiteralArgumentBuilder<ServerCommandSource> register() {
        var builder = super.register();
        builder.then(argument("duration", StringArgumentType.string())
                .executes(this));
        return builder;
    }
}
