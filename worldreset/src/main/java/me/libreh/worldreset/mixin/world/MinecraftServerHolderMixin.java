package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.world.WorldManager;
import me.libreh.worldreset.world.WorldResetHolder;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecraftServer.class)
public class MinecraftServerHolderMixin implements WorldResetHolder {
    @Unique
    @Nullable
    private WorldManager worldreset$worldManager;

    @Override
    public @Nullable WorldManager worldreset$worldManager() {
        return worldreset$worldManager;
    }

    @Override
    public void worldreset$setWorldManager(@Nullable WorldManager manager) {
        this.worldreset$worldManager = manager;
    }
}
