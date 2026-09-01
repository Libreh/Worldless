package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.LobbyWorld;
import me.libreh.worldreset.api.ServerTaskExecutor;
import me.libreh.worldreset.api.WorldDeletion;
import me.libreh.worldreset.api.WorldPool;
import me.libreh.worldreset.api.WorldPoolHost;
import me.libreh.worldreset.api.WorldPreloader;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class WorldManager implements WorldPoolHost<Void> {
    private final MinecraftServer server;
    private final ServerTaskExecutor taskExecutor;
    private final TriggerTracker triggers;
    private final CountdownManager countdownManager;
    private final PlayerManager playerManager;
    private final PlayerResetState playerResetState;
    private final ActiveWorlds activeWorlds;
    private final ResetManager resetManager;
    private boolean resetting;

    private final WorldPreloader poolPreloader;
    private @Nullable WorldPool<Void> worldPool;

    private @Nullable PendingReset pendingReset;

    private record PendingReset(String seed, boolean fromCountdown) {}

    private String activeSeed;
    private Config.SpawnNear activeSpawnNear;

    public WorldManager(MinecraftServer server, LobbyWorld lobbyWorld) {
        this.server = server;
        this.taskExecutor = new ServerTaskExecutor(server);
        this.triggers = new TriggerTracker(server);
        this.playerManager = new PlayerManager(server, this.taskExecutor);
        this.playerResetState = PlayerResetState.load(server);
        this.countdownManager = new CountdownManager(server, this.triggers, () -> resetting);
        this.activeWorlds = new ActiveWorlds(server);
        this.poolPreloader = new WorldPreloader(server);
        this.resetManager = new ResetManager(
            server, activeWorlds, this::getWorldPool, playerManager, playerResetState, triggers
        );
        lobbyWorld.prepareLobbyFiles(server);
        initGameWorlds(server);
        cleanupOrphanedPoolWorlds(server);
        this.activeSeed = ConfigManager.config().seed;
        this.activeSpawnNear = ConfigManager.config().spawnNear.copy();

        if (ConfigManager.config().poolSize > 0) {
            this.worldPool = new WorldPool<>(server, this, taskExecutor, poolPreloader, WorldReset.MOD_ID);
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
                activeWorlds.set(overworld, nether, end);
                WorldReset.LOGGER.info("Restored active game worlds from saved state");
                return;
            }
            WorldReset.LOGGER.warn("Saved active worlds state missing or invalid, creating fresh game worlds");
            ActiveWorldsState.clear(server);
            if (overworld != null) WorldDeletion.deleteDimensionAsync(server, overworld);
            if (nether != null) WorldDeletion.deleteDimensionAsync(server, nether);
            if (end != null) WorldDeletion.deleteDimensionAsync(server, end);
        }

        Config cfg = ConfigManager.config();
        resetManager.createGameWorlds(SeedUtil.parseSeed(cfg.seed));
        resetManager.initializeWorldSpawn(cfg);
    }

    private void cleanupOrphanedPoolWorlds(MinecraftServer server) {
        Set<ResourceKey<Level>> keep = new HashSet<>();
        keep.add(WorldReset.LOBBY_WORLD);
        CustomLevel overworld = activeWorlds.overworld();
        if (overworld != null) {
            keep.add(overworld.dimension());
            keep.add(activeWorlds.nether().dimension());
            keep.add(activeWorlds.end().dimension());
        }

        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        for (ServerLevel level : levels) {
            if (!(level instanceof CustomLevel custom)) continue;
            ResourceKey<Level> key = level.dimension();
            if (!key.identifier().getNamespace().equals(WorldReset.MOD_ID)) continue;
            if (keep.contains(key)) continue;
            WorldReset.LOGGER.info("Cleaning up orphaned pool world: {}", key.identifier());
            WorldDeletion.deleteDimensionAsync(server, custom);
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
            boolean alreadyQueued = pendingReset != null;
            if (tryReset("", true)) {
                countdownManager.broadcastRestart();
                countdownManager.continueCountdown();
            } else if (!alreadyQueued) {
                broadcastQueuedReset();
            }
        }
    }

    public void onServerStopping() {
        poolPreloader.reset();
        if (worldPool != null) {
            worldPool.cleanup();
        }
    }

    private void drainQueuedReset() {
        if (worldPool == null || !worldPool.isReady()) return;
        PendingReset request = pendingReset;
        if (request == null) return;
        pendingReset = null;
        resetWorlds(request.seed());
        if (request.fromCountdown()) countdownManager.continueCountdown();
    }

    private void broadcastQueuedReset() {
        Component msg = queuedResetMessage();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    public static Component queuedResetMessage() {
        return Component.literal("Next world isn't ready yet, reset queued")
                .append(CommonComponents.NEW_LINE)
                .append(Component.literal("Consider increasing pool_size in the config"))
            .withStyle(ChatFormatting.YELLOW);
    }

    public boolean resetWorlds(String seed) {
        return tryReset(seed, false);
    }

    // Single decision point for both the explicit /reset command and the automatic countdown-expiry
    // reset: commits pending config changes, then either starts the reset now or queues it for when
    // the pool is ready. A queued request is always overwritten with the latest seed, but a request
    // already flagged fromCountdown stays flagged even if a later, non-countdown call queues again.
    private boolean tryReset(String seed, boolean fromCountdown) {
        boolean hadPendingChanges = hasPendingChanges();
        if (hadPendingChanges) {
            commitPendingChanges();
        }
        boolean poolBusy = !hadPendingChanges && worldPool != null && !worldPool.isReady();
        if (poolBusy) {
            boolean inheritedFromCountdown = fromCountdown || (pendingReset != null && pendingReset.fromCountdown());
            pendingReset = new PendingReset(seed, inheritedFromCountdown);
            WorldReset.LOGGER.info("Reset queued: pool not ready (state={}, ready={})",
                worldPool.getState(), worldPool.getReadyCount());
            return false;
        }
        pendingReset = null;
        resetting = true;
        try {
            resetManager.resetWorlds(ConfigManager.config(), seed);
        } catch (Throwable e) {
            WorldReset.LOGGER.error("Error during world reset", e);
        } finally {
            resetting = false;
        }
        return true;
    }

    public boolean hasPendingChanges() {
        Config cfg = ConfigManager.config();
        return !activeSeed.equals(cfg.seed) || !activeSpawnNear.equals(cfg.spawnNear);
    }

    private void commitPendingChanges() {
        Config cfg = ConfigManager.config();
        WorldReset.LOGGER.info("Committing pending config changes (seed: '{}' -> '{}')", activeSeed, cfg.seed);
        syncActiveConfig(cfg);
        if (worldPool != null) {
            worldPool.cleanup();
        }
    }

    private void syncActiveConfig(Config cfg) {
        activeSeed = cfg.seed;
        activeSpawnNear = cfg.spawnNear.copy();
    }

    public boolean shouldStop() {
        return triggers.shouldStop();
    }

    public void onPortalUsed(ServerPlayer player, Identifier blockId, ServerLevel origin) {
        if (triggers.notePortalUse(player, blockId, toVanillaDimension(origin))) {
            evaluateTriggers();
        }
    }

    public void onEntityDeath(Identifier entityId, Entity entity) {
        if (triggers.noteEntityDeath(entityId, entity)) {
            evaluateTriggers();
        }
    }

    public void onAdvancement(ServerPlayer player, Identifier advancementId) {
        if (triggers.noteAdvancement(player, advancementId)) {
            evaluateTriggers();
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (playerResetState.isProcessed(player.getUUID())) return;
        playerManager.teleportToOverworldSpawn(player, activeWorlds.overworld());
        ConfigManager.config().resetOnLoad.applyTo(player);
        playerResetState.markProcessed(player.getUUID());
    }

    public void evaluateTriggers() {
        // tickKeepAlive() during a reset pumps player ticks, which can re-fire portal/death/advancement
        // events (e.g. the triggering player still standing in the portal); ignore them until the reset completes.
        if (resetting) return;
        if (triggers.shouldReset()) {
            resetWorlds("");
        } else if (countdownManager.isCountdownActive() && triggers.shouldStop()) {
            stopCountdown();
        }
    }

    public void setCountdownTimer(long seconds) {
        countdownManager.startCountdown(seconds);
    }

    public void stopCountdown() {
        countdownManager.stopCountdown();
    }

    public boolean isResetting() {
        return resetting;
    }

    public ResourceKey<Level> toVanillaDimension(ServerLevel level) {
        if (level == activeWorlds.overworld()) return Level.OVERWORLD;
        if (level == activeWorlds.nether()) return Level.NETHER;
        if (level == activeWorlds.end()) return Level.END;
        return level.dimension();
    }

    public @Nullable WorldPool<Void> getWorldPool() {
        return worldPool;
    }

    public void onConfigReload() {
        // Reloaded config deserializes fresh trigger predicate instances, so any in-progress
        // trigger state keyed on the old instances is now orphaned; start the new config clean.
        triggers.reset();
        int poolSize = ConfigManager.config().poolSize;
        if (poolSize > 0 && worldPool == null) {
            syncActiveConfig(ConfigManager.config());
            worldPool = new WorldPool<>(server, this, taskExecutor, poolPreloader, WorldReset.MOD_ID);
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
        return SeedUtil.parseSeed(activeSeed);
    }

    @Override
    public float resolvePreloadDistance(MinecraftServer server) {
        return ConfigManager.config().resolvePreloadDistance(server);
    }

    @Override
    public CompletableFuture<SpawnResult<Void>> findSpawn(ServerLevel overworld) {
        Config.SpawnNear spawnNear = activeSpawnNear;
        return CompletableFuture.supplyAsync(
            () -> new SpawnResult<Void>(SpawnSearch.findSpawn(overworld, spawnNear, server), null),
            taskExecutor
        );
    }
}
