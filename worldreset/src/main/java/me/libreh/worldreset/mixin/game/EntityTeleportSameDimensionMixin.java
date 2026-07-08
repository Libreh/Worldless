package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityTeleportSameDimensionMixin {
    @Inject(method = "teleportSameDimension", at = @At("TAIL"))
    private void worldreset$onSameDimensionTeleport(ServerLevel level, TeleportTransition transition, CallbackInfoReturnable<Entity> cir) {
        var worlds = WorldReset.worlds();
        if (worlds == null) return;
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof ServerPlayer player)) return;
        if (worlds.triggers.confirmPortalTeleport(player)) {
            worlds.evaluateTriggers();
        }
    }
}
