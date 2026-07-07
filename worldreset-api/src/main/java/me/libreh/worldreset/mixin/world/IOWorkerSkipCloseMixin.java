package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IOWorker.class)
public class IOWorkerSkipCloseMixin {
    @Inject(
        method = "close",
        at = @At("HEAD"),
        cancellable = true
    )
    private void worldreset$skipCloseDuringReset(CallbackInfo ci) {
        if (ResetFlags.skipCloseSave.get()) {
            ci.cancel();
        }
    }
}
