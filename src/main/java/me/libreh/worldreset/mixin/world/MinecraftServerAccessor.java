package me.libreh.worldreset.mixin.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Mutable
    @Accessor("isSaving")
    void setIsSaving(boolean saving);

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();

    @Invoker("loadLevel")
    void invokeLoadLevel();
}