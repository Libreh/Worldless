package me.libreh.worldless.mixin;

import me.libreh.worldless.Worldless;
import me.libreh.worldless.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Unique
    private final ServerPlayerEntity player = ((ServerPlayerEntity) (Object) this);

    @Inject(at = @At(value = "TAIL"), method = "detachForDimensionChange")
    private void detach(CallbackInfo ci) {
        Worldless.fountainPlayers.add(player.getUuid());
        if (ConfigManager.getConfig().endTimerOn.equals("end_fountain") && Worldless.fountainPlayers.size() == player.getServer().getPlayerManager().getPlayerList().size()) {
            Worldless.isCountdownRunning = false;
        }
    }
}
