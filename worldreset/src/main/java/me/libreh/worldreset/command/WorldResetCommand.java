package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import eu.pb4.predicate.api.MinecraftPredicate;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.predicate.AdvancementPredicate;
import me.libreh.worldreset.predicate.EntityDeathPredicate;
import me.libreh.worldreset.predicate.PortalEnterPredicate;
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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Optional;

public final class WorldResetCommand {
    private static final DynamicCommandExceptionType INVALID_STRUCTURE = new DynamicCommandExceptionType(
            o -> Component.literal("Invalid structure: " + o));
    private static final SimpleCommandExceptionType INDEX_OUT_OF_RANGE = new SimpleCommandExceptionType(
            Component.literal("Trigger index out of range"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(build("worldreset", buildContext));
        dispatcher.register(build("wr", buildContext));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name, CommandBuildContext buildContext) {
        return Commands.literal(name)
                .then(Commands.literal("reset")
                        .requires(src -> WorldReset.hasPermission(src, "reset"))
                        .executes(ctx -> resetWorlds(ctx.getSource(), ""))
                        .then(Commands.argument("seed", StringArgumentType.word())
                                .suggests((ctx, builder) -> builder.suggest("random").buildFuture())
                                .executes(ctx -> resetWorlds(ctx.getSource(), StringArgumentType.getString(ctx, "seed")))))
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
                                .executes(ctx -> listTriggers(ctx, "both"))
                                .then(Commands.literal("stop").executes(ctx -> listTriggers(ctx, "stop")))
                                .then(Commands.literal("reset").executes(ctx -> listTriggers(ctx, "reset"))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearTriggers(ctx, "both"))
                                .then(Commands.literal("stop").executes(ctx -> clearTriggers(ctx, "stop")))
                                .then(Commands.literal("reset").executes(ctx -> clearTriggers(ctx, "reset"))))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .suggests((c, b) -> suggestIndices(c, b, "stop"))
                                                .executes(ctx -> removeTrigger(ctx, "stop"))))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .suggests((c, b) -> suggestIndices(c, b, "reset"))
                                                .executes(ctx -> removeTrigger(ctx, "reset")))))
                        .then(Commands.literal("add")
                                .then(buildAddBranch("stop", buildContext))
                                .then(buildAddBranch("reset", buildContext))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddBranch(String listType, CommandBuildContext buildContext) {
        return Commands.literal(listType)
                .then(Commands.literal("portal")
                        .then(Commands.argument("block", ResourceArgument.resource(buildContext, Registries.BLOCK))
                                .executes(ctx -> addPortalTrigger(ctx, listType, Optional.empty(), false))
                                .then(Commands.argument("require_all_players", BoolArgumentType.bool())
                                        .executes(ctx -> addPortalTrigger(ctx, listType, Optional.empty(),
                                                BoolArgumentType.getBool(ctx, "require_all_players"))))
                                .then(Commands.argument("origin_dimension", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                ctx.getSource().getServer().levelKeys().stream().map(ResourceKey::identifier), builder))
                                        .executes(ctx -> addPortalTrigger(ctx, listType,
                                                Optional.of(IdentifierArgument.getId(ctx, "origin_dimension")), false))
                                        .then(Commands.argument("require_all_players", BoolArgumentType.bool())
                                                .executes(ctx -> addPortalTrigger(ctx, listType,
                                                        Optional.of(IdentifierArgument.getId(ctx, "origin_dimension")),
                                                        BoolArgumentType.getBool(ctx, "require_all_players")))))))
                .then(Commands.literal("death")
                        .then(Commands.argument("entity", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                                .executes(ctx -> addDeathTrigger(ctx, listType))))
                .then(Commands.literal("advancement")
                        .then(Commands.argument("advancement", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ctx.getSource().getServer().getAdvancements().getAllAdvancements()
                                                .stream().map(AdvancementHolder::id), builder))
                                .executes(ctx -> addAdvancementTrigger(ctx, listType, false))
                                .then(Commands.argument("require_all_players", BoolArgumentType.bool())
                                        .executes(ctx -> addAdvancementTrigger(ctx, listType,
                                                BoolArgumentType.getBool(ctx, "require_all_players"))))));
    }

    private static int resetWorlds(CommandSourceStack source, String seed) {
        if (!WorldReset.worlds().resetWorlds(seed)) {
            source.sendSuccess(() -> Component.literal("Next world isn't ready yet, reset queued")
                    .append(CommonComponents.NEW_LINE)
                    .append(Component.literal("Consider increasing pool_size in the config"))
                .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        if (ConfigManager.load()) {
            WorldReset.worlds().onConfigReload();
            source.sendSuccess(() -> withPendingNote(Component.literal("Reloaded config!")), false);
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
        ctx.getSource().sendSuccess(() -> withPendingNote(Component.literal("Seed set to " + seed + ".")), false);
        return 1;
    }

    private static int setSpawnType(CommandSourceStack source, String type, String target) {
        ConfigManager.config().spawnNear.type = type;
        ConfigManager.config().spawnNear.target = target;
        ConfigManager.save();
        MutableComponent base;
        if (type.equals("none")) {
            base = Component.literal("Spawn near disabled");
        } else {
            base = Component.literal("Spawn near set to " + type + ": " + target);
        }
        source.sendSuccess(() -> withPendingNote(base), false);
        return 1;
    }

    private static int setSpawnOffset(CommandContext<CommandSourceStack> ctx) {
        int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
        ConfigManager.config().spawnNear.offset = blocks;
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> withPendingNote(Component.literal("Spawn offset set to " + blocks + " blocks")), false);
        return 1;
    }

    private static int setRequireSurface(CommandContext<CommandSourceStack> ctx) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        ConfigManager.config().spawnNear.requireSurface = value;
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> withPendingNote(Component.literal("Require surface set to " + value)), false);
        return 1;
    }

    private static Component withPendingNote(MutableComponent base) {
        if (WorldReset.worlds().hasPendingChanges()) {
            return base.append(CommonComponents.NEW_LINE)
                .append(Component.literal("Applies on next reset").withStyle(ChatFormatting.YELLOW));
        }
        return base;
    }

    private static List<MinecraftPredicate> getTriggers(String listType) {
        return listType.equals("stop") ? ConfigManager.config().stopTriggers : ConfigManager.config().resetTriggers;
    }

    private static int listTriggers(CommandContext<CommandSourceStack> ctx, String listType) {
        if (listType.equals("both")) {
            int count = listTriggers(ctx, "stop");
            count += listTriggers(ctx, "reset");
            return count;
        }
        var triggers = getTriggers(listType);
        if (triggers.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No " + listType + " triggers configured."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(listType + " triggers:"), false);
        for (int i = 0; i < triggers.size(); i++) {
            final int index = i;
            ctx.getSource().sendSuccess(() -> Component.literal("  [" + index + "] " + describe(triggers.get(index))), false);
        }
        return triggers.size();
    }

    private static int clearTriggers(CommandContext<CommandSourceStack> ctx, String listType) {
        if (listType.equals("both")) {
            clearTriggers(ctx, "stop");
            clearTriggers(ctx, "reset");
            ctx.getSource().sendSuccess(() -> Component.literal("Cleared all triggers"), false);
            return 1;
        }
        getTriggers(listType).clear();
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared all " + listType + " triggers."), false);
        return 1;
    }

    private static int removeTrigger(CommandContext<CommandSourceStack> ctx, String listType) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        var triggers = getTriggers(listType);
        if (index >= triggers.size()) throw INDEX_OUT_OF_RANGE.create();
        MinecraftPredicate removed = triggers.remove(index);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + listType + " trigger: " + describe(removed)), false);
        return 1;
    }

    private static int addPortalTrigger(CommandContext<CommandSourceStack> ctx, String listType, Optional<Identifier> originDimension, boolean requireAll) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var holder = ResourceArgument.getResource(ctx, "block", Registries.BLOCK);
        Identifier id = holder.key().identifier();
        PortalEnterPredicate entry = new PortalEnterPredicate(id, requireAll, originDimension, Optional.empty());
        getTriggers(listType).add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + listType + " trigger: " + describe(entry)), false);
        return 1;
    }

    private static int addDeathTrigger(CommandContext<CommandSourceStack> ctx, String listType) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var holder = ResourceArgument.getResource(ctx, "entity", Registries.ENTITY_TYPE);
        Identifier id = holder.key().identifier();
        EntityDeathPredicate entry = new EntityDeathPredicate(id, Optional.empty());
        getTriggers(listType).add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + listType + " trigger: " + describe(entry)), false);
        return 1;
    }

    private static int addAdvancementTrigger(CommandContext<CommandSourceStack> ctx, String listType, boolean requireAll) {
        Identifier id = IdentifierArgument.getId(ctx, "advancement");
        AdvancementPredicate entry = new AdvancementPredicate(id, requireAll, Optional.empty());
        getTriggers(listType).add(entry);
        ConfigManager.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + listType + " trigger: " + describe(entry)), false);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestIndices(CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String listType) {
        var triggers = getTriggers(listType);
        for (int i = 0; i < triggers.size(); i++) {
            builder.suggest(i, Component.literal(describe(triggers.get(i))));
        }
        return builder.buildFuture();
    }

    private static String describe(MinecraftPredicate c) {
        if (c instanceof PortalEnterPredicate pe) {
            String desc = "portal " + pe.block();
            if (pe.originDimension().isPresent()) desc += " from " + pe.originDimension().get();
            desc += pe.requireAllPlayers() ? " (all players)" : " (any player)";
            return desc;
        } else if (c instanceof EntityDeathPredicate ed) {
            return "death " + ed.entity();
        } else if (c instanceof AdvancementPredicate adv) {
            return "advancement " + adv.advancement() + (adv.requireAllPlayers() ? " (all players)" : " (any player)");
        }
        return c.identifier().toString();
    }
}
