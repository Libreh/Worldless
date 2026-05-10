package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerEndCreditsMixin {
    private static final Identifier END_PORTAL = Identifier.withDefaultNamespace("end_portal");

    @Inject(method = "showEndCredits", at = @At("HEAD"))
    private void worldreset$onShowEndCredits(CallbackInfo ci) {
        var worlds = WorldReset.worlds();
        if (worlds == null) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        worlds.stopConditions.notePortalTouch(self, END_PORTAL);
        if (worlds.stopConditions.confirmPortalTeleport(self)) {
            worlds.evaluateAndMaybeStop();
        }
    }
}
