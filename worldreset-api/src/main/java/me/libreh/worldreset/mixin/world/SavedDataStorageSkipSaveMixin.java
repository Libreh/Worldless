package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.ResetFlags;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SavedDataStorage.class)
public class SavedDataStorageSkipSaveMixin {
    @Redirect(
        method = "close",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/SavedDataStorage;saveAndJoin()V")
    )
    private void worldreset$skipSaveAndJoinDuringReset(SavedDataStorage instance) {
        if (!ResetFlags.skipCloseSave.get()) {
            instance.saveAndJoin();
        }
    }
}
