package me.libreh.worldless.mixin.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Mutable
    @Accessor("saving")
    void setIsSaving(boolean saving);

    @Accessor("session")
    LevelStorage.Session getStorageSource();

    @Invoker("loadWorld")
    void invokeLoadLevel();
}
