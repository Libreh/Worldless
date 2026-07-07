package me.libreh.worldreset.mixin.world;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Invoker("runDistanceManagerUpdates")
    boolean worldreset$invokeRunDistanceManagerUpdates();
}
