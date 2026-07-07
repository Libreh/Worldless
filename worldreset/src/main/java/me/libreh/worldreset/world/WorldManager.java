package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.LobbyWorld;
import me.libreh.worldreset.api.WorldPool;
import me.libreh.worldreset.api.WorldPoolHost;
import me.libreh.worldreset.api.WorldPreloader;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.utils.level.LevelUtilsKt;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class WorldManager implements WorldPoolHost {
    private final MinecraftServer server;
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

    private final WorldPreloader worldPreloader;
    private @Nullable WorldPool worldPool;

    private boolean resetQueued;
    private String queuedResetSeed = "";
    private boolean queuedFromCountdown;

    public WorldManager(MinecraftServer server, LobbyWorld lobbyWorld) {
        this.server = server;
        this.taskExecutor = new me.libreh.worldreset.api.ServerTaskExecutor(server);
        this.stopConditions = new StopConditionTracker(server);
        this.playerManager = new PlayerManager(server, this.taskExecutor);
        this.playerResetState = PlayerResetState.load(server);
        this.countdownManager = new CountdownManager(server, this.stopConditions);
        this.worldPreloader = new WorldPreloader(server);
        this.resetManager = new ResetManager(
            server, this, playerManager, playerResetState, stopConditions
        );
        lobbyWorld.prepareLobbyFiles(server);
        initGameWorlds(server);
        cleanupOrphanedPoolWorlds(server);

        if (ConfigManager.config().poolSize > 0) {
            this.worldPool = new WorldPool(server, this, taskExecutor, worldPreloader, WorldReset.MOD_ID);
            WorldReset.LOGGER.info("World pool enabled (pool_size={})", ConfigManager.config().poolSize);
        }
    }

    private void initGameWorlds(MinecraftServer server) {
        ActiveWorldsState.ActiveKeys saved = ActiveWorldsState.load(server);
        if (saved != null) {
            CustomLevel overworld = ArcadeDimensions.load(server, saved.overworld());
            CustomLevel nether = ArcadeDimensions.load(server, saved.nether());
            CustomLevel end = ArcadeDimensions.load(server, saved.end());
            if (overworld != null && nether != null && end != null) {
                setGameWorlds(overworld, nether, end);
                WorldReset.LOGGER.info("Restored active game worlds from saved state");
                return;
            }
            WorldReset.LOGGER.warn("Saved active worlds state missing or invalid, creating fresh game worlds");
            ActiveWorldsState.clear(server);
            if (overworld != null) ArcadeDimensions.delete(server, overworld);
            if (nether != null) ArcadeDimensions.delete(server, nether);
            if (end != null) ArcadeDimensions.delete(server, end);
        }

        resetManager.createGameWorlds(SeedUtil.parseSeed(ConfigManager.config().seed));
    }

    private void cleanupOrphanedPoolWorlds(MinecraftServer server) {
        Set<ResourceKey<Level>> keep = new HashSet<>();
        keep.add(WorldReset.LOBBY_WORLD);
        if (gameOverworld != null) {
            keep.add(gameOverworld.dimension());
            keep.add(gameNether.dimension());
            keep.add(gameEnd.dimension());
        }

        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (ServerLevel level : levels) {
            if (!(level instanceof CustomLevel custom)) continue;
            ResourceKey<Level> key = level.dimension();
            if (!key.identifier().getNamespace().equals(WorldReset.MOD_ID)) continue;
            if (keep.contains(key)) continue;
            WorldReset.LOGGER.info("Cleaning up orphaned pool world: {}", key.identifier());
            ArcadeDimensions.delete(server, custom);
        }
    }

    public void onServerTick() {
        if (worldPool != null) {
            worldPool.tick();
        }
        drainQueuedReset();
        if (!countdownManager.isCountdownActive()) return;

        countdownManager.tick();
        if (!countdownManager.isCountdownActive()) {
            boolean poolBusy = worldPool != null && !worldPool.isReady();
            if (poolBusy) {
                if (!resetQueued) {
                    resetQueued = true;
                    queuedResetSeed = "";
                    queuedFromCountdown = true;
                    broadcastQueuedReset();
                }
            } else {
                countdownManager.broadcastRestart();
                resetWorlds("");
                countdownManager.continueCountdown();
            }
        }
    }

    private void drainQueuedReset() {
        if (!resetQueued) return;
        if (worldPool == null || !worldPool.isReady()) return;
        String seed = queuedResetSeed;
        boolean fromCountdown = queuedFromCountdown;
        resetQueued = false;
        queuedResetSeed = "";
        queuedFromCountdown = false;
        resetWorlds(seed);
        if (fromCountdown) countdownManager.continueCountdown();
    }

    private void broadcastQueuedReset() {
        Component msg = Component.literal("Next world isn't ready yet - reset queued. Consider increasing pool_size in the config.")
            .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    public boolean resetWorlds(String seed) {
        if (worldPool != null && !worldPool.isReady()) {
            resetQueued = true;
            queuedResetSeed = seed;
            WorldReset.LOGGER.info("Reset queued: pool not ready (state={}, ready={})",
                worldPool.getState(), worldPool.getReadyCount());
            return false;
        }
        resetQueued = false;
        queuedResetSeed = "";
        state = WorldState.RESETTING;
        try {
            resetManager.resetWorlds(seed);
        } catch (Throwable e) {
            WorldReset.LOGGER.error("Error during world reset", e);
        } finally {
            state = WorldState.LOADED;
        }
        return true;
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

    public WorldPreloader getWorldPreloader() {
        return worldPreloader;
    }

    public @Nullable WorldPool getWorldPool() {
        return worldPool;
    }

    public void setGameWorlds(CustomLevel overworld, CustomLevel nether, CustomLevel end) {
        this.gameOverworld = overworld;
        this.gameNether = nether;
        this.gameEnd = end;
        LevelUtilsKt.setSpoofedDimension(overworld, Level.OVERWORLD);
        LevelUtilsKt.setSpoofedDimension(nether, Level.NETHER);
        LevelUtilsKt.setSpoofedDimension(end, Level.END);
        ActiveWorldsState.save(server, overworld.dimension(), nether.dimension(), end.dimension());
    }

    public void clearGameWorlds() {
        this.gameOverworld = null;
        this.gameNether = null;
        this.gameEnd = null;
        ActiveWorldsState.clear(server);
    }

    public void onConfigReload() {
        int poolSize = ConfigManager.config().poolSize;
        if (poolSize > 0 && worldPool == null) {
            worldPool = new WorldPool(server, this, taskExecutor, worldPreloader, WorldReset.MOD_ID);
            WorldReset.LOGGER.info("World pool enabled (pool_size={})", poolSize);
        } else if (poolSize <= 0 && worldPool != null) {
            worldPool.cleanup();
            worldPool = null;
            WorldReset.LOGGER.info("World pool disabled");
        }
    }

    @Override
    public int maxPoolSize() {
        return ConfigManager.config().poolSize;
    }

    @Override
    public long nextSeed() {
        return SeedUtil.parseSeed(ConfigManager.config().seed);
    }

    @Override
    public float resolvePreloadDistance(MinecraftServer server) {
        return ConfigManager.config().resolvePreloadDistance(server);
    }

    @Override
    public CompletableFuture<@Nullable BlockPos> findSpawn(ServerLevel overworld) {
        var config = ConfigManager.config();
        return CompletableFuture.supplyAsync(
            () -> SpawnSearch.findSpawn(overworld, config, server),
            taskExecutor
        );
    }
}
