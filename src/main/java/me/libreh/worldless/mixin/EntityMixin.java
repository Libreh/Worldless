package me.libreh.worldless.mixin;

import me.libreh.worldless.WorldlessMod;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.Portal;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "tryUsePortal", at = @At("TAIL"))
    private void worldless$tryUsePortal(Portal portal, BlockPos pos, CallbackInfo ci) {
        if (portal instanceof EndPortalBlock && ((Entity) (Object) this) instanceof ServerPlayerEntity serverPlayer) {
            var worldManager = WorldlessMod.getWorldManager();
            boolean shouldStop = worldManager.shouldStop(serverPlayer);

            worldManager.fountainPlayers.add(serverPlayer.getUuid());

            if (shouldStop) {
                WorldlessMod.getWorldManager().stopCountdown();
            }
        }
    }
}
