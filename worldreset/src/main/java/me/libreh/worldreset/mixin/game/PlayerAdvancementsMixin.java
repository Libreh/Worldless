package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    @Inject(method = "award", at = @At("TAIL"))
    private void worldreset$award(AdvancementHolder advancementHolder, String string, CallbackInfoReturnable<Boolean> cir) {
        if (player == null) return;
        var worlds = WorldReset.worlds(player.level().getServer());
        if (worlds == null) return;
        if (!((PlayerAdvancements) (Object) this).getOrStartProgress(advancementHolder).isDone()) return;
        if (worlds.triggers.noteAdvancement(player, advancementHolder.id())) {
            worlds.evaluateTriggers();
        }
    }
}
