package me.libreh.worldreset.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class PlayerResetEvents {
    private static final List<Consumer<ServerPlayer>> BEFORE_PLAYER_RESET = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ServerPlayer>> AFTER_PLAYER_RESET = new CopyOnWriteArrayList<>();

    private PlayerResetEvents() {}

    public static void onBeforePlayerReset(Consumer<ServerPlayer> listener) {
        BEFORE_PLAYER_RESET.add(listener);
    }

    public static void onAfterPlayerReset(Consumer<ServerPlayer> listener) {
        AFTER_PLAYER_RESET.add(listener);
    }

    public static void fireBeforePlayerReset(ServerPlayer player) {
        for (Consumer<ServerPlayer> l : BEFORE_PLAYER_RESET) l.accept(player);
    }

    public static void fireAfterPlayerReset(ServerPlayer player) {
        for (Consumer<ServerPlayer> l : AFTER_PLAYER_RESET) l.accept(player);
    }
}
