package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.world.PlayerReset;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlaceNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        var worlds = WorldReset.worlds();
        if (worlds == null) return;
        if (worlds.playerResetState.isProcessed(player.getUUID())) return;

        worlds.playerManager.teleportToOverworldSpawn(player);
        PlayerReset.applyConfiguredResets(player);
        worlds.playerResetState.markProcessed(player.getUUID());
    }
}
