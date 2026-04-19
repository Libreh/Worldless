package me.libreh.worldreset.mixin.game;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public class EnderDragonEntityMixin {
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;isDeadOrDying()Z"))
    private boolean aiStep(EnderDragon instance, Operation<Boolean> original) {
        if (instance.isDeadOrDying()) {
            stopCountdownIfDead();
        }
        return original.call(instance);
    }

    @Inject(method = "kill", at = @At("TAIL"))
    private void kill(ServerLevel world, CallbackInfo ci) {
        stopCountdownIfDead();
    }

    @Unique
    private void stopCountdownIfDead() {
        if (WorldReset.worlds() == null) return;
        if (ConfigManager.config().stopTimerOn.dragonDeath) {
            WorldReset.worlds().stopCountdown();
        }
    }
}
