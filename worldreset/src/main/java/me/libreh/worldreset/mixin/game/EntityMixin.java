package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Portal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "setAsInsidePortal", at = @At("TAIL"))
    private void setAsInsidePortal(Portal portal, BlockPos blockPos, CallbackInfo ci) {
        if (!(((Entity) (Object) this) instanceof ServerPlayer serverPlayer)) return;
        var worlds = WorldReset.worlds(serverPlayer.level().getServer());
        if (worlds == null) return;
        if (!(portal instanceof EndPortalBlock || portal instanceof NetherPortalBlock || portal instanceof EndGatewayBlock)) {
            return;
        }
        Identifier blockId = BuiltInRegistries.BLOCK.getKey((net.minecraft.world.level.block.Block) portal);
        worlds.triggers.notePortalTouch(serverPlayer, blockId);
    }
}
