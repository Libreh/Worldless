package me.libreh.worldreset.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SavedDataStorage.class)
public class SavedDataStorageSkipSaveMixin {
    @WrapOperation(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/SavedDataStorage;saveAndJoin()V")
    )
    private void worldreset$skipSaveAndJoinDuringReset(SavedDataStorage instance, Operation<Void> original) {
        if (!ResetFlags.skipCloseSave.get()) {
            original.call(instance);
        }
    }
}
