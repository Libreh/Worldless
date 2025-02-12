package me.libreh.worldless.mixin;

import me.libreh.worldless.config.Config;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.libreh.worldless.Worldless.stopTimer;

@Mixin(PhaseManager.class)
public class PhaseManagerMixin {
    @Inject(method = "setPhase", at = @At("TAIL"))
    private void runnersWon(PhaseType<?> type, CallbackInfo ci) {
        if (type == PhaseType.DYING && Config.getConfig().endTimerOn.equals("DRAGON_DEATH")) {
            stopTimer();
        }
    }
}
