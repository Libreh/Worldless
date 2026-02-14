package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "setAsInsidePortal", at = @At("TAIL"))
    private void setAsInsidePortal(Portal portal, BlockPos blockPos, CallbackInfo ci) {
        if (portal instanceof EndPortalBlock && ((Entity) (Object) this) instanceof ServerPlayer serverPlayer) {
            var worldManager = WorldReset.worlds();
            boolean shouldStop = worldManager.shouldStop(serverPlayer);

            worldManager.fountainPlayers.add(serverPlayer.getUUID());

            if (shouldStop) {
                WorldReset.worlds().stopCountdown();
            }
        }
    }
}
