package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.world.WorldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void hurtServer(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        var worlds = WorldReset.worlds();
        if (worlds != null && worlds.state() == WorldState.RESETTING) {
            cir.setReturnValue(false);
        }
    }
}