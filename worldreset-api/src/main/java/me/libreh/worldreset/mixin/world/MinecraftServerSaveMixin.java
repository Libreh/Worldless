package me.libreh.worldreset.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.libreh.worldreset.api.VanillaDims;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaveMixin {
    @WrapOperation(
        method = "saveAllChunks",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;save(Lnet/minecraft/util/ProgressListener;ZZ)V")
    )
    private void worldreset$skipVanillaEmptySave(ServerLevel level, @Nullable ProgressListener listener, boolean flush, boolean noSave, Operation<Void> original) {
        if (VanillaDims.is(level) && level.dimension() != Level.OVERWORLD && level.players().isEmpty()) {
            return;
        }
        original.call(level, listener, flush, noSave);
    }
}
