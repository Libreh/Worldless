package me.libreh.worldreset.mixin.world;

import me.libreh.worldreset.WorldReset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void onPlaceNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (WorldReset.worlds() == null) return;
        if (player.level().dimension().identifier().getNamespace().equals(WorldReset.MOD_ID)) {
            return;
        }
        WorldReset.worlds().playerManager.teleportToOverworldSpawn(player);
    }

    @Inject(method = "loadPlayerData", at = @At(value = "RETURN"), cancellable = true)
    private void loadDefaultPlayerData(NameAndId nameAndId, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        if (WorldReset.worlds() == null) return;
        if (cir.getReturnValue().isEmpty()) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("Dimension", WorldReset.LOBBY_WORLD.identifier().toString());

            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(1));
            position.add(DoubleTag.valueOf(65));
            position.add(DoubleTag.valueOf(1));
            nbt.put("Pos", position);

            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(0.0F));
            rotation.add(FloatTag.valueOf(0.0F));
            nbt.put("Rotation", rotation);

            cir.setReturnValue(Optional.of(nbt));
        }
    }
}
