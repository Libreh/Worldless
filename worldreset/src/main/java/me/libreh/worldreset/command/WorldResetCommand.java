package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.config.StopCondition;
import me.libreh.worldreset.util.TimeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class WorldResetCommand {
    private static final DynamicCommandExceptionType INVALID_STRUCTURE = new DynamicCommandExceptionType(
            o -> Component.literal("Invalid structure: " + o));
    private static final SimpleCommandExceptionType STOP_INDEX_OUT_OF_RANGE = new SimpleCommandExceptionType(
            Component.literal("Stop condition index out of range"));

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
                                        .executes(WorldResetCommand::setRequireSurface))))
                .then(Commands.literal("triggers")
                        .requires(src -> WorldReset.hasPermission(src, "triggers"))
                        .then(Commands.literal("list")
                                .executes(WorldResetCommand::listTriggers))
                        .then(Commands.literal("clear")
                                .executes(WorldResetCommand::clearTriggers))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .suggests((c, b) -> {
                                            var conds = ConfigManager.config().stopConditions;
                                            for (int i = 0; i < conds.size(); i++) {
                                                b.suggest(i, Component.literal(describe(conds.get(i))));
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(WorldResetCommand::removeTrigger)))
                        .then(Commands.literal("add")
                                .then(Commands.literal("portal")
                                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                                .executes(ctx -> addPortalTrigger(ctx, true))
                                                .then(Commands.argument("require_all_players", BoolArgumentType.bool())
                                                        .executes(ctx -> addPortalTrigger(ctx,
                                                                BoolArgumentType.getBool(ctx, "require_all_players"))))))
                                .then(Commands.literal("death")
                                        .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                                .executes(WorldResetCommand::addDeathTrigger)))
                                .then(Commands.literal("advancement")
                                        .then(Commands.argument("advancement", IdentifierArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        ctx.getSource().getServer().getAdvancements().getAllAdvancements()
                                                                .stream().map(AdvancementHolder::id), builder))
                                                .executes(ctx -> addAdvancementTrigger(ctx, false))
                                                .then(Commands.argument("require_all_players", BoolArgumentType.bool())
                                                        .executes(ctx -> addAdvancementTrigger(ctx,
                                                                BoolArgumentType.getBool(ctx, "require_all_players"))))))));
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

    private static int listTriggers(CommandContext<CommandSourceStack> ctx) {
        var conditions = ConfigManager.config().stopConditions;
        if (conditions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No reset triggers configured."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Reset triggers:"), false);
        for (int i = 0; i < conditions.size(); i++) {
            final int index = i;
            ctx.getSource().sendSuccess(() -> Component.literal("  [" + index + "] " + describe(conditions.get(index))), false);
        }
        return conditions.size();
    }

    private static int clearTriggers(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.config().stopConditions.clear();
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared all reset triggers."), false);
        return 1;
    }

    private static int removeTrigger(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        var conditions = ConfigManager.config().stopConditions;
        if (index >= conditions.size()) throw STOP_INDEX_OUT_OF_RANGE.create();
        StopCondition removed = conditions.remove(index);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Removed trigger: " + describe(removed)), false);
        return 1;
    }

    private static int addPortalTrigger(CommandContext<CommandSourceStack> ctx, boolean requireAll) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var holder = ResourceArgument.getResource(ctx, "block", Registries.BLOCK);
        Identifier id = holder.key().identifier();
        StopCondition.PortalEnter entry = new StopCondition.PortalEnter();
        entry.block = id.toString();
        entry.requireAllPlayers = requireAll;
        ConfigManager.config().stopConditions.add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added trigger: " + describe(entry)), false);
        return 1;
    }

    private static int addDeathTrigger(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var holder = ResourceArgument.getResource(ctx, "entity", Registries.ENTITY_TYPE);
        Identifier id = holder.key().identifier();
        StopCondition.EntityDeath entry = new StopCondition.EntityDeath();
        entry.entity = id.toString();
        ConfigManager.config().stopConditions.add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added trigger: " + describe(entry)), false);
        return 1;
    }

    private static int addAdvancementTrigger(CommandContext<CommandSourceStack> ctx, boolean requireAll) {
        Identifier id = IdentifierArgument.getId(ctx, "advancement");
        StopCondition.Advancement entry = new StopCondition.Advancement();
        entry.advancement = id.toString();
        entry.requireAllPlayers = requireAll;
        ConfigManager.config().stopConditions.add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added trigger: " + describe(entry)), false);
        return 1;
    }

    private static String describe(StopCondition c) {
        if (c instanceof StopCondition.PortalEnter pe) {
            return "portal " + pe.block + (pe.requireAllPlayers ? " (all players)" : " (any player)");
        } else if (c instanceof StopCondition.EntityDeath ed) {
            return "death " + ed.entity;
        } else if (c instanceof StopCondition.Advancement adv) {
            return "advancement " + adv.advancement + (adv.requireAllPlayers ? " (all players)" : " (any player)");
        }
        return c.type;
    }
}