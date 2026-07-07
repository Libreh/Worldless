package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.libreh.worldreset.WorldReset;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ResetCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reset")
                .requires(src -> WorldReset.hasPermission(src, "reset"))
                .executes(context -> resetWorlds(context.getSource(), ""))
                .then(Commands.argument("seed", StringArgumentType.word())
                        .suggests((context, builder) -> builder.suggest("random").buildFuture())
                        .executes(context -> {
                            String seed = StringArgumentType.getString(context, "seed");
                            return resetWorlds(context.getSource(), seed);
                        }))
        );
    }

    private static int resetWorlds(CommandSourceStack source, String seed) {
        if (!WorldReset.worlds().resetWorlds(seed)) {
            source.sendSuccess(() -> Component.literal("Next world isn't ready yet, reset queued")
                    .append(Component.literal("Consider increasing pool_size in the config"))
                .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }
}
