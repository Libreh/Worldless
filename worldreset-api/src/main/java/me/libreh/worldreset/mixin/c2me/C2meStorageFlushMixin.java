package me.libreh.worldreset.mixin.c2me;

import me.libreh.worldreset.api.ResetFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// C2ME replaces the vanilla IOWorker with its own per-dimension storage thread whose close() does a
// blocking flush + fsync (flush0 -> RegionBasedStorage.sync). WorldReset's IOWorkerSkipCloseMixin
// can't reach it because C2ME overrides close() without calling super, so every reset pays that
// stall three times on the server thread. The region files are about to be renamed and deleted
// anyway, so skip the flush during a reset; the storage thread still closes its region files right
// after, so handles are released either way.
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageThread", remap = false)
public class C2meStorageFlushMixin {
    @Inject(method = "flush0(Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void worldreset$skipFlushDuringReset(boolean sync, CallbackInfo ci) {
        if (sync && ResetFlags.skipChunkIoFlush) {
            ci.cancel();
        }
    }
}
