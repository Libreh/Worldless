package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.libreh.worldreset.WorldReset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ResetCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reset")
                .requires(src -> WorldReset.hasPermission(src, "reset"))
                .executes(context -> resetWorlds(""))
                .then(Commands.argument("seed", StringArgumentType.word())
                        .suggests((context, builder) -> builder.suggest("random").buildFuture())
                        .executes(context -> {
                            String seed = StringArgumentType.getString(context, "seed");
                            resetWorlds(seed);
                            return 1;
                        }))
        );
    }

    private static int resetWorlds(String seed) {
        WorldReset.worlds().resetWorlds(seed);
        return 1;
    }
}
