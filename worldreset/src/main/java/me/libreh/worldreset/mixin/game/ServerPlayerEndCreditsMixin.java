package me.libreh.worldreset.mixin.game;

import me.libreh.worldreset.WorldReset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerEndCreditsMixin {
    private static final Identifier END_PORTAL = Identifier.withDefaultNamespace("end_portal");

    // Entering the end fountain without having seen the credits shows the credits screen instead
    // of teleporting, so the handlePortal path in EntityMixin never fires; note the portal use here.
    @Inject(method = "showEndCredits", at = @At("HEAD"))
    private void worldreset$onShowEndCredits(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        var worlds = WorldReset.worlds(self.level().getServer());
        if (worlds == null) return;
        if (self.level() instanceof ServerLevel level) {
            worlds.onPortalUsed(self, END_PORTAL, level);
        }
    }
}
