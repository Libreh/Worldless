package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityDieMixin {
    @Inject(method = "die", at = @At("TAIL"))
    private void worldreset$die(DamageSource damageSource, CallbackInfo ci) {
        var worlds = WorldReset.worlds();
        if (worlds == null) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (worlds.triggers.noteEntityDeath(id, self)) {
            worlds.evaluateTriggers();
        }
    }
}
