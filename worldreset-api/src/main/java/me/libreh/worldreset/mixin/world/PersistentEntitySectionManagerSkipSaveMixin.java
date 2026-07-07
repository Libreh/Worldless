package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerSkipSaveMixin {
    @Redirect(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;saveAll()V")
    )
    private void worldreset$skipEntitySaveDuringReset(PersistentEntitySectionManager<?> instance) {
        if (!ResetFlags.skipCloseSave.get()) {
            instance.saveAll();
        }
    }
}
