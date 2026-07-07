package me.libreh.worldreset.mixin.world;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftServer.class)
public interface MinecraftServerPollTaskAccessor {
    @Invoker("pollTask")
    boolean worldreset$invokePollTask();
}
