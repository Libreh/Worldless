package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerDieMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void worldreset$playerDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.level().isClientSide()) return;
        var worlds = WorldReset.worlds(self.level().getServer());
        if (worlds == null) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        worlds.onEntityDeath(id, self);
    }
}
