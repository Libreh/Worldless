package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.LobbyWorld;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.utils.level.LevelUtilsKt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public class WorldManager {
    private final me.libreh.worldreset.api.ServerTaskExecutor taskExecutor;
    public final StopConditionTracker stopConditions;
    private final CountdownManager countdownManager;
    public final PlayerManager playerManager;
    public final PlayerResetState playerResetState;
    private final ResetManager resetManager;
    private WorldState state = WorldState.LOADED;

    private CustomLevel gameOverworld;
    private CustomLevel gameNether;
    private CustomLevel gameEnd;

    public WorldManager(MinecraftServer server, LobbyWorld lobbyWorld) {
        this.taskExecutor = new me.libreh.worldreset.api.ServerTaskExecutor(server);
        this.stopConditions = new StopConditionTracker(server);
        this.playerManager = new PlayerManager(server, this.taskExecutor);
        this.playerResetState = PlayerResetState.load(server);
        this.countdownManager = new CountdownManager(server, this.stopConditions);
        this.resetManager = new ResetManager(
            server, this, playerManager, playerResetState, lobbyWorld, stopConditions
        );
        lobbyWorld.prepareLobbyFiles(server);
        initGameWorlds(server);
    }

    private void initGameWorlds(MinecraftServer server) {
        CustomLevel overworld = ArcadeDimensions.load(server, WorldReset.GAME_OVERWORLD);
        CustomLevel nether = ArcadeDimensions.load(server, WorldReset.GAME_NETHER);
        CustomLevel end = ArcadeDimensions.load(server, WorldReset.GAME_END);

        if (overworld != null && nether != null && end != null) {
            setGameWorlds(overworld, nether, end);
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
        try {
            resetManager.resetWorlds(seed);
        } catch (Throwable e) {
            WorldReset.LOGGER.error("Error during world reset", e);
        } finally {
            state = WorldState.LOADED;
        }
    }

    public boolean shouldStop() {
        return stopConditions.shouldStop();
    }

    public void evaluateAndMaybeStop() {
        if (countdownManager.isCountdownActive() && stopConditions.shouldStop()) {
            stopCountdown();
        }
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
        LevelUtilsKt.setSpoofedDimension(overworld, Level.OVERWORLD);
        LevelUtilsKt.setSpoofedDimension(nether, Level.NETHER);
        LevelUtilsKt.setSpoofedDimension(end, Level.END);
    }
}
