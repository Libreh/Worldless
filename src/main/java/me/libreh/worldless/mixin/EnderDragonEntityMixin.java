package me.libreh.worldless.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldless.WorldlessMod;
import me.libreh.worldless.config.ConfigManager;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonEntity.class)
public class EnderDragonEntityMixin {
    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;isDead()Z"))
    private boolean worldless$tickMovement(EnderDragonEntity instance, Operation<Boolean> original) {
        if (instance.isDead()) {
            stopCountdownIfDead();
        }
        return original.call(instance);
    }

    @Inject(method = "kill", at = @At("TAIL"))
    private void worldless$kill(ServerWorld world, CallbackInfo ci) {
        stopCountdownIfDead();
    }

    @Unique
    private void stopCountdownIfDead() {
        if (ConfigManager.getInstance().getConfig().stopTimerOn.dragonDeath) {
            WorldlessMod.getWorldManager().stopCountdown();
        }
    }
}
