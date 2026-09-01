package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.*;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.SpawnType;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class ResetManager {
    private final MinecraftServer server;
    private final ActiveWorlds activeWorlds;
    private final Supplier<WorldPool<Void>> worldPoolSupplier;
    private final PlayerManager playerManager;
    private final PlayerResetState playerResetState;
    private final TriggerTracker triggers;

    public ResetManager(
        MinecraftServer server, ActiveWorlds activeWorlds, Supplier<WorldPool<Void>> worldPoolSupplier,
        PlayerManager playerManager, PlayerResetState playerResetState, TriggerTracker triggers
    ) {
        this.server = server;
        this.activeWorlds = activeWorlds;
        this.worldPoolSupplier = worldPoolSupplier;
        this.playerManager = playerManager;
        this.playerResetState = playerResetState;
        this.triggers = triggers;
    }

    public void resetWorlds(Config cfg, String seed) {
        String seedString = seed.isEmpty() ? cfg.seed : seed;
        long seedLong = SeedUtil.parseSeed(seedString);
        boolean explicitOverride = !seed.isEmpty() && !seed.equals(cfg.seed);

        WorldPool<Void> pool = worldPoolSupplier.get();
        if (explicitOverride && pool != null) {
            pool.discardAndKickOff(seedLong);
        }

        PooledWorlds<Void> pooled = (pool != null) ? pool.claim() : null;
        if (pooled != null) {
            try {
                if (cfg.spoofDimension) {
                    resetWithPoolViaLobby(cfg, pool, pooled);
                } else {
                    resetWithPoolDirect(cfg, pool, pooled);
                }
                return;
            } catch (Throwable e) {
                WorldReset.LOGGER.error("Pool adoption failed, falling back to synchronous create", e);
            }
        }
        resetInPlace(cfg, seedLong);
    }

    private void resetWithPoolDirect(Config cfg, WorldPool<Void> pool, PooledWorlds<Void> pooled) {
        LiveWorlds live = beginReset();

        adoptPooledWorlds(pool, pooled);

        ServerLevel gameOverworld = activeWorlds.overworld();
        BlockPos spawn = pooled.spawn() != null ? pooled.spawn() : SpawnFinder.findSpawn(gameOverworld);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.isAlive()) {
                playerManager.respawnInto(player, gameOverworld, spawn);
            }
        }

        postReset(cfg, pooled.spawn());

        tickKeepAlive();
        withSkipCloseSave(() -> deleteOldWorlds(live));
    }

    private void resetWithPoolViaLobby(Config cfg, WorldPool<Void> pool, PooledWorlds<Void> pooled) {
        movePlayersToLobby();
        LiveWorlds live = beginReset();

        withSkipCloseSave(() -> {
            deleteOldWorlds(live);
            adoptPooledWorlds(pool, pooled);
        });

        postReset(cfg, pooled.spawn());
    }

    private void resetInPlace(Config cfg, long seed) {
        movePlayersToLobby();
        LiveWorlds live = beginReset();

        WorldDeletion.resetWorldChunks(server, live.overworld(), live.nether(), live.end());
        tickKeepAlive();
        createGameWorlds(seed);
        BlockPos customSpawn = SpawnSearch.findSpawn(activeWorlds.overworld(), cfg.spawnNear, server);

        postReset(cfg, customSpawn);
    }

    private LiveWorlds beginReset() {
        tickKeepAlive();
        saveWorldData();
        LiveWorlds live = new LiveWorlds(activeWorlds.overworld(), activeWorlds.nether(), activeWorlds.end());
        ResetCleanup.clear(server, live.overworld(), live.nether(), live.end());
        return live;
    }

    private void movePlayersToLobby() {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            playerManager.preparePlayerForReset(player);
        }
    }

    private void adoptPooledWorlds(WorldPool<Void> pool, PooledWorlds<Void> pooled) {
        pool.registerPooledWorlds(pooled);
        activeWorlds.set(pooled.overworld(), pooled.nether(), pooled.end());
        WorldReset.LOGGER.info("Adopted pooled worlds (seed={})", pooled.seed());
    }

    private void deleteOldWorlds(LiveWorlds live) {
        for (CustomLevel level : live.all()) {
            WorldDeletion.deleteDimensionAsync(server, level);
        }
        ServerTasks.drainQueue(server);
    }

    private void withSkipCloseSave(Runnable action) {
        ResetFlags.setSkipCloseSave(true);
        try {
            action.run();
        } finally {
            ResetFlags.setSkipCloseSave(false);
        }
    }

    private record LiveWorlds(CustomLevel overworld, CustomLevel nether, CustomLevel end) {
        List<CustomLevel> all() {
            return List.of(overworld, nether, end);
        }
    }

    public void createGameWorlds(long seed) {
        String modId = WorldReset.MOD_ID;
        VanillaLikeLevels levels = GameWorlds.create(
            server,
            DimensionKeys.generate(modId, "overworld"),
            DimensionKeys.generate(modId, "nether"),
            DimensionKeys.generate(modId, "the_end"),
            seed
        );
        activeWorlds.set(
            levels.getOrThrow(VanillaDimension.Overworld),
            levels.getOrThrow(VanillaDimension.Nether),
            levels.getOrThrow(VanillaDimension.End)
        );
    }

    private void saveWorldData() {
        WorldReset.LOGGER.debug("Saving player data...");
        server.getPlayerList().saveAll();
        tickKeepAlive();
    }

    private void tickKeepAlive() {
        server.tickConnection();
    }

    public void initializeWorldSpawn(Config cfg) {
        BlockPos customSpawn = SpawnSearch.findSpawn(activeWorlds.overworld(), cfg.spawnNear, server);
        setWorldSpawn(customSpawn);
    }

    private void setWorldSpawn(@Nullable BlockPos customSpawn) {
        var gameOverworld = activeWorlds.overworld();
        BlockPos respawnPos = customSpawn != null ? customSpawn : SpawnFinder.findSpawn(gameOverworld);
        server.setRespawnData(LevelData.RespawnData.of(gameOverworld.dimension(), respawnPos, 0.0F, 0.0F));

        ChunkPos spawnChunk = ChunkPos.containing(respawnPos);
        ServerChunkCache chunkSource = gameOverworld.getChunkSource();
        chunkSource.addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, spawnChunk, 2);
        ChunkLoading.runDistanceManagerUpdates(chunkSource);
    }

    private void postReset(Config cfg, @Nullable BlockPos customSpawn) {
        triggers.reset();
        setTimeOfDay(cfg);
        clearWeather(cfg);

        var gameOverworld = activeWorlds.overworld();
        setWorldSpawn(customSpawn);

        Set<UUID> processedPlayers = new HashSet<>();
        boolean spawnNearNone = cfg.spawnNear.type == SpawnType.NONE;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (customSpawn != null && !spawnNearNone) {
                player.teleportTo(gameOverworld,
                    customSpawn.getX() + 0.5, customSpawn.getY(), customSpawn.getZ() + 0.5,
                    Set.of(), 0.0F, 0.0F, true);
            } else {
                playerManager.teleportToOverworldSpawn(player, gameOverworld);
            }
            cfg.resetOnLoad.applyTo(player);
            processedPlayers.add(player.getUUID());
        }
        playerResetState.resetCycle(processedPlayers);
    }

    private void setTimeOfDay(Config cfg) {
        int timeOfDay = cfg.resetOnLoad.timeOfDay;
        if (timeOfDay >= 0) {
            var overworldClock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            server.clockManager().setTotalTicks(overworldClock, timeOfDay);
            WorldReset.LOGGER.debug("Set time of day to {}", timeOfDay);
        }
    }

    private void clearWeather(Config cfg) {
        if (cfg.resetOnLoad.clearWeather) {
            server.setWeatherParameters(0, 0, false, false);
            WorldReset.LOGGER.debug("Cleared weather");
        }
    }
}
