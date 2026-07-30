package me.libreh.worldreset.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerResetEvents {
    public static final Event<PlayerResetCallback> BEFORE_PLAYER_RESET = EventFactory.createArrayBacked(
        PlayerResetCallback.class,
        listeners -> player -> {
            for (PlayerResetCallback listener : listeners) {
                listener.onPlayerReset(player);
            }
        }
    );

    public static final Event<PlayerResetCallback> AFTER_PLAYER_RESET = EventFactory.createArrayBacked(
        PlayerResetCallback.class,
        listeners -> player -> {
            for (PlayerResetCallback listener : listeners) {
                listener.onPlayerReset(player);
            }
        }
    );

    private PlayerResetEvents() {}

    @FunctionalInterface
    public interface PlayerResetCallback {
        void onPlayerReset(ServerPlayer player);
    }
}
