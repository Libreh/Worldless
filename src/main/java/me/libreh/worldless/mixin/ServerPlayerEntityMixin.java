package me.libreh.worldless.mixin;

import me.libreh.worldless.config.Config;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.libreh.worldless.Worldless.FOUNTAIN_PLAYERS;
import static me.libreh.worldless.Worldless.stopTimer;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Unique
    private final ServerPlayerEntity player = ((ServerPlayerEntity) (Object) this);

    @Inject(at = @At(value = "TAIL"), method = "detachForDimensionChange")
    private void detach(CallbackInfo ci) {
        FOUNTAIN_PLAYERS.add(player.getUuid());
        if (Config.getConfig().endTimerOn.equals("END_FOUNTAIN") && FOUNTAIN_PLAYERS.size() == player.getServer().getPlayerManager().getPlayerList().size()) {
            stopTimer();
        }
    }
}
