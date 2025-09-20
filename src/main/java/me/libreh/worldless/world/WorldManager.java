package me.libreh.worldless.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.random.RandomSeed;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorldManager {
    public final boolean cancelSaving = true;
    public final Set<UUID> fountainPlayers = new HashSet<>();
    private final CountdownManager countdownManager;
    private final WorldResetService worldResetService;

    public WorldManager(MinecraftServer server) {
        ServerTaskExecutor taskExecutor = new ServerTaskExecutor(server);
        PlayerManager playerManager = new PlayerManager(server, taskExecutor);
        LobbyWorldService lobbyWorldService = new LobbyWorldService();
        this.countdownManager = new CountdownManager(server);
        this.worldResetService = new WorldResetService(
            server, playerManager, lobbyWorldService, fountainPlayers
        );
        lobbyWorldService.unzipLobbyWorld();
    }

    public void onServerTick() {
        if (!countdownManager.isCountdownActive()) return;

        countdownManager.tick();
        if (!countdownManager.isCountdownActive()) {
            worldResetService.resetWorlds();
            countdownManager.continueCountdown();
        }
    }

    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }
}