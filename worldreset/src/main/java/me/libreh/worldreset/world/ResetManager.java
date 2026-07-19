package me.libreh.worldreset.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.*;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.mixin.world.MinecraftServerPollTaskAccessor;
import me.libreh.worldreset.mixin.world.RaidsAccessor;
import me.libreh.worldreset.mixin.world.ServerChunkCacheAccessor;
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
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ResetManager {
    private final MinecraftServer server;
    private final WorldManager worldManager;
    private final PlayerManager playerManager;
    private final PlayerResetState playerResetState;
    private final TriggerTracker triggers;

    public ResetManager(MinecraftServer server, WorldManager worldManager, PlayerManager playerManager, PlayerResetState playerResetState, TriggerTracker triggers) {
        this.server = server;
        this.worldManager = worldManager;
        this.playerManager = playerManager;
        this.playerResetState = playerResetState;
        this.triggers = triggers;
    }

    public void resetWorlds(Config cfg, String seed) {
        String seedString = seed.isEmpty() ? cfg.seed : seed;
        long seedLong = SeedUtil.parseSeed(seedString);
        boolean explicitOverride = !seed.isEmpty() && !seed.equals(cfg.seed);

        WorldPool pool = worldManager.getWorldPool();
        if (explicitOverride && pool != null) {
            pool.discardAndKickOff(seedLong);
        }

        PooledWorlds pooled = (pool != null) ? pool.claim() : null;
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

    private void resetWithPoolDirect(Config cfg, WorldPool pool, PooledWorlds pooled) {
        tickKeepAlive();
        stopAllRaids();
        saveWorldData();

        CustomLevel liveOverworld = worldManager.getGameOverworld();
        CustomLevel liveNether = worldManager.getGameNether();
        CustomLevel liveEnd = worldManager.getGameEnd();
        BossEvents.clearForLevels(liveOverworld, liveNether, liveEnd);
        ScoreboardClear.clearAll(server);

        pool.registerPooledWorlds(pooled);
        worldManager.setGameWorlds(pooled.overworld(), pooled.nether(), pooled.end());
        WorldReset.LOGGER.info("Adopted pooled worlds (seed={})", pooled.seed());

        ServerLevel gameOverworld = worldManager.getGameOverworld();
        BlockPos spawn = pooled.spawn() != null ? pooled.spawn() : SpawnFinder.findSpawn(gameOverworld);

        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!player.isAlive()) {
                playerManager.respawnInto(player, gameOverworld, spawn);
            }
        }

        postReset(cfg, pooled.spawn());

        tickKeepAlive();
        ResetFlags.skipCloseSave.set(true);
        try {
            worldManager.getWorldPreloader().reset();
            for (CustomLevel level : new CustomLevel[]{liveOverworld, liveNether, liveEnd}) {
                if (level != null) WorldDeletion.deleteDimensionAsync(server, level);
            }
            while (((MinecraftServerPollTaskAccessor) server).worldreset$invokePollTask()) {}
        } finally {
            ResetFlags.skipCloseSave.set(false);
        }
    }

    private void resetWithPoolViaLobby(Config cfg, WorldPool pool, PooledWorlds pooled) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            playerManager.preparePlayerForReset(player);
        }

        tickKeepAlive();
        stopAllRaids();
        saveWorldData();

        CustomLevel liveOverworld = worldManager.getGameOverworld();
        CustomLevel liveNether = worldManager.getGameNether();
        CustomLevel liveEnd = worldManager.getGameEnd();
        BossEvents.clearForLevels(liveOverworld, liveNether, liveEnd);
        ScoreboardClear.clearAll(server);

        ResetFlags.skipCloseSave.set(true);
        try {
            worldManager.getWorldPreloader().reset();
            for (CustomLevel level : new CustomLevel[]{liveOverworld, liveNether, liveEnd}) {
                if (level != null) WorldDeletion.deleteDimensionAsync(server, level);
            }
            while (((MinecraftServerPollTaskAccessor) server).worldreset$invokePollTask()) {}
            pool.registerPooledWorlds(pooled);
            worldManager.setGameWorlds(pooled.overworld(), pooled.nether(), pooled.end());
            WorldReset.LOGGER.info("Adopted pooled worlds (seed={})", pooled.seed());
        } finally {
            ResetFlags.skipCloseSave.set(false);
        }

        postReset(cfg, pooled.spawn());
    }

    private void resetInPlace(Config cfg, long seed) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            playerManager.preparePlayerForReset(player);
        }

        tickKeepAlive();
        stopAllRaids();
        saveWorldData();

        CustomLevel liveOverworld = worldManager.getGameOverworld();
        CustomLevel liveNether = worldManager.getGameNether();
        CustomLevel liveEnd = worldManager.getGameEnd();
        BossEvents.clearForLevels(liveOverworld, liveNether, liveEnd);
        ScoreboardClear.clearAll(server);

        WorldDeletion.resetWorldChunks(server, liveOverworld, liveNether, liveEnd);
        tickKeepAlive();
        createGameWorlds(seed);
        BlockPos customSpawn = SpawnSearch.findSpawn(worldManager.getGameOverworld(), cfg.spawnNear, server);

        postReset(cfg, customSpawn);
    }

    public void createGameWorlds(long seed) {
        String modId = WorldReset.MOD_ID;
        VanillaLikeLevels levels = GameWorlds.create(
            server,
            DimensionKeys.generate(modId, "overworld"),
            DimensionKeys.generate(modId, "nether"),
            DimensionKeys.generate(modId, "the_end"),
            seed,
            false
        );
        worldManager.setGameWorlds(
            levels.getOrThrow(VanillaDimension.Overworld),
            levels.getOrThrow(VanillaDimension.Nether),
            levels.getOrThrow(VanillaDimension.End)
        );
    }

    private void stopAllRaids() {
        for (ServerLevel world : List.of(worldManager.getGameOverworld(), worldManager.getGameNether(), worldManager.getGameEnd())) {
            if (world == null) continue;
            RaidsAccessor raidManagerAccessor = (RaidsAccessor) world.getRaids();
            Int2ObjectMap<Raid> raids = raidManagerAccessor.getRaidMap();
            for (Raid raid : List.copyOf(raids.values())) {
                raid.stop();
            }
        }
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
        BlockPos customSpawn = SpawnSearch.findSpawn(worldManager.getGameOverworld(), cfg.spawnNear, server);
        setWorldSpawn(customSpawn);
    }

    private BlockPos setWorldSpawn(@Nullable BlockPos customSpawn) {
        var gameOverworld = worldManager.getGameOverworld();
        BlockPos respawnPos = customSpawn != null ? customSpawn : SpawnFinder.findSpawn(gameOverworld);
        server.setRespawnData(LevelData.RespawnData.of(gameOverworld.dimension(), respawnPos, 0.0F, 0.0F));

        ChunkPos spawnChunk = ChunkPos.containing(respawnPos);
        ServerChunkCache chunkSource = gameOverworld.getChunkSource();
        chunkSource.addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, spawnChunk, 2);
        ((ServerChunkCacheAccessor) chunkSource).worldreset$invokeRunDistanceManagerUpdates();
        return respawnPos;
    }

    private void postReset(Config cfg, @Nullable BlockPos customSpawn) {
        triggers.reset();
        setTimeOfDay(cfg);
        clearWeather(cfg);

        var gameOverworld = worldManager.getGameOverworld();
        BlockPos respawnPos = setWorldSpawn(customSpawn);

        Set<UUID> processedPlayers = new HashSet<>();
        boolean spawnNearNone = cfg.spawnNear.type.equals("none");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (customSpawn != null && !spawnNearNone) {
                player.teleportTo(gameOverworld,
                    customSpawn.getX() + 0.5, customSpawn.getY(), customSpawn.getZ() + 0.5,
                    Set.of(), 0.0F, 0.0F, true);
            } else {
                playerManager.teleportToOverworldSpawn(player);
            }
            PlayerReset.applyConfiguredResets(cfg.resetOnLoad, player);
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
