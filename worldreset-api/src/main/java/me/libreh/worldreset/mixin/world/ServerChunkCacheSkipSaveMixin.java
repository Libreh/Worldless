package me.libreh.worldreset.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheSkipSaveMixin {
    @WrapOperation(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;save(Z)V")
    )
    private void worldreset$skipSaveDuringReset(ServerChunkCache instance, boolean flushStorage, Operation<Void> original) {
        if (!ResetFlags.skipCloseSave.get()) {
            original.call(instance, flushStorage);
        }
    }
}
