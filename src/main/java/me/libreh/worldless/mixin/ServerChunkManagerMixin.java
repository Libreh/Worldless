package me.libreh.worldless.mixin;

import me.libreh.worldless.WorldlessMod;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkManager.class)
public class ServerChunkManagerMixin {
    @Inject(at = @At(value = "HEAD"), method = "save(Z)V", cancellable = true)
    public void worldless$save(boolean flush, CallbackInfo ci) {
        if (WorldlessMod.getWorldManager().cancelSaving) {
            ci.cancel();
        }
    }
}
