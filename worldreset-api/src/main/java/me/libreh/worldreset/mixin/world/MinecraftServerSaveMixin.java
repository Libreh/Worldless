package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.api.VanillaDims;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaveMixin {
    @Redirect(
        method = "saveAllChunks",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;save(Lnet/minecraft/util/ProgressListener;ZZ)V")
    )
    private void worldreset$skipVanillaEmptySave(ServerLevel level, @Nullable ProgressListener listener, boolean flush, boolean noSave) {
        if (VanillaDims.is(level) && level.dimension() != Level.OVERWORLD && level.players().isEmpty()) {
            return;
        }
        level.save(listener, flush, noSave);
    }
}
