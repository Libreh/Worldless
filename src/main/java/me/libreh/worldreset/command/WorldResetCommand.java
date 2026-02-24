package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

public final class WorldResetCommand {
    private static final DynamicCommandExceptionType INVALID_STRUCTURE = new DynamicCommandExceptionType(
            o -> Component.literal("Invalid structure: " + o));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(build("worldreset", buildContext));
        dispatcher.register(build("wr", buildContext));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name, CommandBuildContext buildContext) {
        return Commands.literal(name)
                .then(Commands.literal("reset")
                        .requires(src -> WorldReset.hasPermission(src, "reset"))
                        .executes(ctx -> resetWorlds(""))
                        .then(Commands.argument("seed", StringArgumentType.word())
                                .suggests((ctx, builder) -> builder.suggest("random").buildFuture())
                                .executes(ctx -> resetWorlds(StringArgumentType.getString(ctx, "seed")))))
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
                        .then(Commands.argument("seed", StringArgumentType.word())
                                .suggests((ctx, builder) -> builder.suggest("random").buildFuture())
                                .executes(WorldResetCommand::setSeed)))
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

    private static int resetWorlds(String seed) {
        WorldReset.worlds().resetWorlds(seed);
        return 1;
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
        String seed = StringArgumentType.getString(ctx, "seed");
        ConfigManager.config().seed = seed;
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