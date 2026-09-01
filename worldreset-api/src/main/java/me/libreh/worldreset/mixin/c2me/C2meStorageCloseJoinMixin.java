package me.libreh.worldreset.mixin.c2me;

import me.libreh.worldreset.api.ResetFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

// C2MEStorageVanillaInterface.close() overrides the vanilla IOWorker.close() without calling super,
// so WorldReset's IOWorkerSkipCloseMixin can't reach it, and every reset blocks the server thread on
// backend.close().join() once per storage worker (chunks, POI, entities x3 dimensions = 9 joins).
// During a reset the region dirs are renamed and deleted right after, so don't wait: let
// backend.close() fire (it still stops the thread and releases file handles) and skip the join.
@Mixin(targets = "com.ishland.c2me.rewrites.chunkio.common.C2MEStorageVanillaInterface", remap = false)
public class C2meStorageCloseJoinMixin {
    @Redirect(
        method = "close",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
        require = 0
    )
    private Object worldreset$skipJoinDuringReset(CompletableFuture<?> future) {
        if (ResetFlags.skipChunkIoFlush) {
            return null;
        }
        return future.join();
    }
}
