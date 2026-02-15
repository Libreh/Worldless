package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class WorldResetCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldreset")
                .then(Commands.literal("reload")
                        .requires(src -> WorldReset.hasPermission(src, "reload"))
                        .executes(ctx -> reloadConfig(ctx.getSource())))
                .then(Commands.literal("timer")
                        .requires(src -> WorldReset.hasPermission(src, "timer"))
                        .then(Commands.argument("duration", StringArgumentType.string())
                                .executes(WorldResetCommand::setTimer)))
                .then(Commands.literal("stop")
                        .requires(src -> WorldReset.hasPermission(src, "stop"))
                        .executes(ctx -> stopTimer()))
                .then(Commands.literal("seed")
                        .requires(src -> WorldReset.hasPermission(src, "seed"))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(WorldResetCommand::setSeed)))
        );
    }

    private static int reloadConfig(CommandSourceStack source) {
        if (ConfigManager.load()) {
            source.sendSuccess(() -> Component.literal("Reloaded config!"), false);
        } else {
            source.sendFailure(Component.literal("Error occurred while reloading config!").withStyle(ChatFormatting.RED));
        }
        return 1;
    }

    private static int setTimer(CommandContext<CommandSourceStack> ctx) {
        String durationInput = StringArgumentType.getString(ctx, "duration");
        long seconds = TimeUtil.parseDuration(durationInput);
        if (seconds == 0) {
            WorldReset.worlds().stopCountdown();
        } else {
            WorldReset.worlds().setCountdownTimer(seconds);
        }
        return 1;
    }

    private static int stopTimer() {
        WorldReset.worlds().stopCountdown();
        return 1;
    }

    private static int setSeed(CommandContext<CommandSourceStack> ctx) {
        long seed = LongArgumentType.getLong(ctx, "seed");
        ConfigManager.config().seed = String.valueOf(seed);
        ConfigManager.save();
        return 1;
    }
}