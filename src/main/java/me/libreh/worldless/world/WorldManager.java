package me.libreh.worldless.world;

import me.libreh.worldless.config.ConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.random.RandomSeed;

import java.util.*;

/**
 * Manages world state, resets, countdowns, and player management for Worldless.
 * <p>
 * Delegates to CountdownManager, WorldResetService, PlayerManager, and LobbyWorldService.
 */
public class WorldManager {
    public final boolean cancelSaving = true;
    public final Set<UUID> fountainPlayers = new HashSet<>();
    private final ServerTaskExecutor taskExecutor;
    private final CountdownManager countdownManager;
    private final WorldResetService worldResetService;
    private final PlayerManager playerManager;
    private final LobbyWorldService lobbyWorldService;

    public WorldManager(MinecraftServer server) {
        this.taskExecutor = new ServerTaskExecutor(server);
        this.playerManager = new PlayerManager(server, taskExecutor);
        this.lobbyWorldService = new LobbyWorldService();
        this.countdownManager = new CountdownManager(server);
        this.worldResetService = new WorldResetService(
            server, playerManager, lobbyWorldService, fountainPlayers
        );
        lobbyWorldService.unzipLobbyWorld();
    }

    /**
     * Called every server tick to update countdown and handle world reset.
     */
    public void onServerTick() {
        if (!countdownManager.isCountdownActive()) return;

        countdownManager.tick();
        // If countdown ends, trigger world reset
        if (!countdownManager.isCountdownActive()) {
            worldResetService.resetWorlds(RandomSeed.getSeed());
            countdownManager.continueCountdown();
        }
    }

    /**
     * Sets the countdown timer and starts the countdown.
     * The countdown is continued until stopped.
     * @param seconds the countdown duration in seconds
     */
    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }
}