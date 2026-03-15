package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorldManager {
    private final ServerTaskExecutor taskExecutor;
    public final Set<UUID> fountainPlayers = new HashSet<>();
    private final CountdownManager countdownManager;
    public final PlayerManager playerManager;
    private final ResetManager resetManager;
    private WorldState state = WorldState.LOADED;

    private CustomLevel gameOverworld;
    private CustomLevel gameNether;
    private CustomLevel gameEnd;

    public WorldManager(MinecraftServer server, LobbyWorld lobbyWorld) {
        this.taskExecutor = new ServerTaskExecutor(server);
        this.playerManager = new PlayerManager(server, this.taskExecutor);
        this.countdownManager = new CountdownManager(server);
        this.resetManager = new ResetManager(
            server, this, playerManager, lobbyWorld, fountainPlayers
        );
        lobbyWorld.prepareLobbyFiles(server);
        initGameWorlds(server);
    }

    private void initGameWorlds(MinecraftServer server) {
        CustomLevel overworld = ArcadeDimensions.load(server, WorldReset.GAME_OVERWORLD);
        CustomLevel nether = ArcadeDimensions.load(server, WorldReset.GAME_NETHER);
        CustomLevel end = ArcadeDimensions.load(server, WorldReset.GAME_END);

        if (overworld != null && nether != null && end != null) {
            this.gameOverworld = overworld;
            this.gameNether = nether;
            this.gameEnd = end;
            return;
        }

        if (overworld != null) ArcadeDimensions.delete(server, overworld);
        if (nether != null) ArcadeDimensions.delete(server, nether);
        if (end != null) ArcadeDimensions.delete(server, end);

        resetManager.createGameWorlds(SeedUtil.parseSeed(ConfigManager.config().seed));
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
        state = WorldState.RESETTING;
        resetManager.resetWorlds(seed);
        state = WorldState.LOADED;
    }

    public boolean shouldStop(ServerPlayer player) {
        return playerManager.shouldStopCountdown(player);
    }

    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }

    public WorldState state() {
        return state;
    }

    public CustomLevel getGameOverworld() {
        return gameOverworld;
    }

    public CustomLevel getGameNether() {
        return gameNether;
    }

    public CustomLevel getGameEnd() {
        return gameEnd;
    }

    public void setGameWorlds(CustomLevel overworld, CustomLevel nether, CustomLevel end) {
        this.gameOverworld = overworld;
        this.gameNether = nether;
        this.gameEnd = end;
    }
}
