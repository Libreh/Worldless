package me.libreh.worldless.command.worldless;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.libreh.worldless.Worldless;
import me.libreh.worldless.command.BaseCommand;
import me.libreh.worldless.util.TimeUtils;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.server.command.CommandManager.argument;


public class TimerCommand extends BaseCommand {
    public TimerCommand() {
        super("timer", "worldless.main");
    }

    @Override
    protected int execute(CommandContext<ServerCommandSource> ctx) {
        String durationInput = StringArgumentType.getString(ctx, "duration");
        long seconds = TimeUtils.parseDuration(durationInput);
        if (seconds == 0) {
            Worldless.getWorldManager().stopCountdown();
        } else {
            Worldless.getWorldManager().setCountdownTimer(seconds);
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
