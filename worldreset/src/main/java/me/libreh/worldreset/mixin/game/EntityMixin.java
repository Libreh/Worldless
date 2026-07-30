package me.libreh.worldreset.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.WorldReset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// handlePortal is the one place where the portal block and the actual teleport meet, so portal
// triggers are noted here atomically. The credits path never reaches this call (EndPortalBlock
// shows the credits instead of teleporting); ServerPlayerEndCreditsMixin covers it.
@Mixin(Entity.class)
public class EntityMixin {
    @WrapOperation(
        method = "handlePortal",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/world/entity/Entity;")
    )
    private Entity worldreset$notePortalUse(Entity instance, TeleportTransition transition, Operation<Entity> original) {
        ServerLevel origin = instance.level() instanceof ServerLevel level ? level : null;
        Portal portal = instance.portalProcess != null
            ? ((PortalProcessorAccessor) instance.portalProcess).worldreset$getPortal()
            : null;
        Entity result = original.call(instance, transition);
        if (result instanceof ServerPlayer player && origin != null && portal instanceof Block block) {
            var worlds = WorldReset.worlds(origin.getServer());
            if (worlds != null) {
                worlds.onPortalUsed(player, BuiltInRegistries.BLOCK.getKey(block), origin);
            }
        }
        return result;
    }
}
