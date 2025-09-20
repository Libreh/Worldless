package me.libreh.worldless.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.RandomSeed;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorldManager {
    public final boolean cancelSaving = true;
    public final Set<UUID> fountainPlayers = new HashSet<>();
    private final CountdownManager countdownManager;
    private final PlayerManager playerManager;
    private final WorldResetService worldResetService;

    public WorldManager(MinecraftServer server) {
        ServerTaskExecutor taskExecutor = new ServerTaskExecutor(server);
        this.playerManager = new PlayerManager(server, taskExecutor);
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
            countdownManager.broadcastRestart();
            worldResetService.resetWorlds();
            countdownManager.continueCountdown();
        }
    }

    public boolean shouldStop(ServerPlayerEntity player) {
        return playerManager.shouldStop(player);
    }

    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }
}