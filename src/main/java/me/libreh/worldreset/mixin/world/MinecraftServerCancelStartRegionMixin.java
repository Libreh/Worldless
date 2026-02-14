package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.WorldReset;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public class MinecraftServerCancelStartRegionMixin {
    @Inject(at = @At(value = "HEAD"), method = "prepareLevels", cancellable = true)
    private void prepareStartRegion(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        if (server.isDedicatedServer() || WorldReset.worlds() != null && WorldReset.worlds().isCancelSaving()) {
            ci.cancel();
        }
    }
}
