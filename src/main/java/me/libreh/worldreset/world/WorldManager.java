package me.libreh.worldreset.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorldManager {
    private boolean shouldCancelSaving = true;
    public final Set<UUID> fountainPlayers = new HashSet<>();
    private final CountdownManager countdownManager;
    private final PlayerManager playerManager;
    private final ResetManager resetManager;

    public WorldManager(MinecraftServer server, LobbyWorld lobbyWorld) {
        ServerTaskExecutor taskExecutor = new ServerTaskExecutor(server);
        this.playerManager = new PlayerManager(server, taskExecutor);
        this.countdownManager = new CountdownManager(server);
        this.resetManager = new ResetManager(
            server, playerManager, lobbyWorld, fountainPlayers
        );
        lobbyWorld.prepareLobbyFiles(server);
    }

    public void onServerTick() {
        if (!countdownManager.isCountdownActive()) return;

        countdownManager.tick();
        if (!countdownManager.isCountdownActive()) {
            countdownManager.broadcastRestart();
            resetWorlds("");
            countdownManager.continueCountdown();
        }
    }

    public void resetWorlds(String seed) {
        resetManager.resetWorlds(seed);
    }

    public boolean shouldStop(ServerPlayer player) {
        return playerManager.shouldStop(player);
    }

    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }

    public boolean isCancelSaving() {
        return shouldCancelSaving;
    }

    public void setCancelSaving(boolean shouldCancelSaving) {
        this.shouldCancelSaving = shouldCancelSaving;
    }
}