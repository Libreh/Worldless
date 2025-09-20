package me.libreh.worldless.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.libreh.worldless.WorldlessMod;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class BaseCommand implements ModCommand {
    private final String name;

    protected BaseCommand(String name) {
        this.name = name;
    }

    public LiteralArgumentBuilder<ServerCommandSource> register() {
        return ModCommand.literal(name)
            .requires(src -> hasPermission(src.getPlayer(), WorldlessMod.MOD_ID + name))
            .executes(this);
    }

    private boolean hasPermission(ServerPlayerEntity player, String permission) {
        return Permissions.check(player, permission, 3);
    }
}