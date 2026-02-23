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
                .then(Commands.literal("spawn")
                        .requires(src -> WorldReset.hasPermission(src, "spawn"))
                        .then(Commands.literal("none")
                                .executes(ctx -> setSpawnType(ctx.getSource(), "none", "")))
                        .then(Commands.literal("structure")
                                .then(Commands.argument("id", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
                                        .executes(ctx -> setSpawnType(ctx.getSource(), "structure",
                                                ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, "id", Registries.STRUCTURE, INVALID_STRUCTURE).asPrintable()))))
                        .then(Commands.literal("biome")
                                .then(Commands.argument("id", ResourceOrTagArgument.resourceOrTag(buildContext, Registries.BIOME))
                                        .executes(ctx -> setSpawnType(ctx.getSource(), "biome",
                                                ResourceOrTagArgument.getResourceOrTag(ctx, "id", Registries.BIOME).asPrintable()))))
                        .then(Commands.literal("offset")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(0))
                                        .executes(WorldResetCommand::setSpawnOffset)))
                        .then(Commands.literal("require_surface")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(WorldResetCommand::setRequireSurface))));
    }

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

    private static int setSpawnType(CommandSourceStack source, String type, String target) {
        ConfigManager.config().spawnNear.type = type;
        ConfigManager.config().spawnNear.target = target;
        ConfigManager.save();
        if (type.equals("none")) {
            source.sendSuccess(() -> Component.literal("Spawn near disabled."), false);
        } else {
            source.sendSuccess(() -> Component.literal("Spawn near set to " + type + ": " + target), false);
        }
        return 1;
    }

    private static int setSpawnOffset(CommandContext<CommandSourceStack> ctx) {
        int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
        ConfigManager.config().spawnNear.offset = blocks;
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Spawn offset set to " + blocks + " blocks."), false);
        return 1;
    }

    private static int setRequireSurface(CommandContext<CommandSourceStack> ctx) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        ConfigManager.config().spawnNear.requireSurface = value;
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Require surface set to " + value + "."), false);
        return 1;
    }
}