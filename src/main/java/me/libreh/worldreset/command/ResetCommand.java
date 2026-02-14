package me.libreh.worldreset.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import me.libreh.worldreset.WorldReset;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ResetCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reset")
                .requires(src -> WorldReset.hasPermission(src, "reset"))
                .executes(context -> resetWorlds(""))
                .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(context -> {
                            long seed = LongArgumentType.getLong(context, "seed");
                            resetWorlds(String.valueOf(seed));
                            return 1;
                        }))
        );
    }

    private static int resetWorlds(String seed) {
        WorldReset.worlds().resetWorlds(seed);
        return 1;
    }
}
