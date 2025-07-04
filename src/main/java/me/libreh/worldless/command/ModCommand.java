package me.libreh.worldless.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

/**
 * Interface for mod commands, extending Brigadier Command.
 */
public interface ModCommand extends Command<ServerCommandSource> {
    /**
     * Creates a literal argument builder for the given command name.
     * @param name the command name
     * @return the argument builder
     */
    static LiteralArgumentBuilder<ServerCommandSource> literal(String name) {
        return CommandManager.literal(name);
    }
}