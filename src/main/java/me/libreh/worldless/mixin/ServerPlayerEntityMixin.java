package me.libreh.worldless.mixin;

import me.libreh.worldless.Worldless;
import me.libreh.worldless.config.ConfigManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
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
        Worldless.getWorldManager().fountainPlayers.add(player.getUuid());
        if (shouldStop()) {
            Worldless.getWorldManager().stopCountdown();
        }
    }

    @Unique
    private boolean shouldStop() {
        if (!ConfigManager.getInstance().getConfig().timerStop.endFountainEnter) return false;
        int fountainPlayersCount = Worldless.getWorldManager().fountainPlayers.size();
        int playerCount = player.getServer().getPlayerManager().getPlayerList().size();
        return fountainPlayersCount == playerCount;
    }
}
