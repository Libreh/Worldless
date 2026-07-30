package me.libreh.worldreset.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerSkipSaveMixin {
    @WrapOperation(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;saveAll()V")
    )
    private void worldreset$skipEntitySaveDuringReset(PersistentEntitySectionManager<?> instance, Operation<Void> original) {
        if (!ResetFlags.skipCloseSave.get()) {
            original.call(instance);
        }
    }
}
