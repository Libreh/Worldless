package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.VanillaDims;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerPrepareLevelsMixin {
    @Redirect(
        method = "prepareLevels",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkLoadCounter;track(Lnet/minecraft/server/level/ServerLevel;Ljava/lang/Runnable;)V")
    )
    private void worldreset$skipVanillaDimensionPrep(ChunkLoadCounter counter, ServerLevel level, Runnable scheduler) {
        if (VanillaDims.is(level)) {
            return;
        }
        counter.track(level, scheduler);
    }
}
