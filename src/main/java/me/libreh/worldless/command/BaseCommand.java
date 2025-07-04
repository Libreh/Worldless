package me.libreh.worldless.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Base class for mod commands, handling name, permission, and registration.
 */
public abstract class BaseCommand implements ModCommand {
    private final String name;
    private final String permission;

    /**
     * Constructs a new BaseCommand.
     * @param name the command name
     * @param permission the required permission
     */
    protected BaseCommand(String name, String permission) {
        this.name = name;
        this.permission = permission;
    }

    /**
     * Executes the command logic. Must be implemented by subclasses.
     * @param ctx the command context
     * @return the result code
     * @throws CommandSyntaxException if command fails
     */
    protected abstract int execute(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException;

    /**
     * Runs the command (Brigadier interface).
     */
    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return execute(context);
    }

    /**
     * Registers the command with permission check and execution logic.
     * @return the argument builder
     */
    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return ModCommand.literal(name)
            .requires(src -> hasPermission(src.getPlayer(), permission))
            .executes(this);
    }

    private boolean hasPermission(ServerPlayerEntity player, String permission) {
        return Permissions.check(player, permission, 3);
    }
}