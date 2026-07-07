package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheSkipSaveMixin {
    @Redirect(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;save(Z)V")
    )
    private void worldreset$skipSaveDuringReset(ServerChunkCache instance, boolean flushStorage) {
        if (!ResetFlags.skipCloseSave.get()) {
            instance.save(flushStorage);
        }
    }
}
