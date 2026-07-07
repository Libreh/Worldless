package me.libreh.worldreset.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.*;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.mixin.world.MinecraftServerPollTaskAccessor;
import me.libreh.worldreset.mixin.world.RaidsAccessor;
import me.libreh.worldreset.mixin.world.ServerChunkCacheAccessor;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
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
    private final StopConditionTracker stopConditions;

    public ResetManager(MinecraftServer server, WorldManager worldManager, PlayerManager playerManager, PlayerResetState playerResetState, StopConditionTracker stopConditions) {
        this.server = server;
        this.worldManager = worldManager;
        this.playerManager = playerManager;
        this.playerResetState = playerResetState;
        this.stopConditions = stopConditions;
    }

    public void resetWorlds(String seed) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            playerManager.preparePlayerForReset(player);
        }

        String seedString = seed.isEmpty() ? ConfigManager.config().seed : seed;
        long seedLong = SeedUtil.parseSeed(seedString);
        boolean explicitOverride = !seed.isEmpty() && !seed.equals(ConfigManager.config().seed);

        WorldPool pool = worldManager.getWorldPool();
        if (explicitOverride && pool != null) {
            pool.discardAndKickOff(seedLong);
        }

        tickKeepAlive();
        stopAllRaids();
        saveWorldData();
        BossEvents.clearForLevels(
            worldManager.getGameOverworld(),
            worldManager.getGameNether(),
            worldManager.getGameEnd()
        );
        ScoreboardClear.clearAll(server);

        CustomLevel liveOverworld = worldManager.getGameOverworld();
        CustomLevel liveNether = worldManager.getGameNether();
        CustomLevel liveEnd = worldManager.getGameEnd();

        PooledWorlds pooled = (pool != null) ? pool.claim() : null;
        boolean adopted = false;

        if (pooled != null) {
            ResetFlags.skipCloseSave.set(true);
            try {
                worldManager.getWorldPreloader().reset();
                for (CustomLevel level : new CustomLevel[]{liveOverworld, liveNether, liveEnd}) {
                    if (level != null) ArcadeDimensions.delete(server, level);
                }
                while (((MinecraftServerPollTaskAccessor) server).worldreset$invokePollTask()) {}
                worldManager.clearGameWorlds();
                pool.registerPooledWorlds(pooled);
                worldManager.setGameWorlds(pooled.overworld(), pooled.nether(), pooled.end());
                WorldReset.LOGGER.info("Adopted pooled worlds (seed={})", pooled.seed());
                adopted = true;
            } catch (Throwable e) {
                WorldReset.LOGGER.error("Pool adoption failed, falling back to synchronous create", e);
            } finally {
                ResetFlags.skipCloseSave.set(false);
            }
        }

        BlockPos customSpawn;
        if (adopted) {
            customSpawn = pooled.spawn();
        } else {
            WorldDeletion.resetWorldChunks(server, liveOverworld, liveNether, liveEnd);
            tickKeepAlive();
            createGameWorlds(seedLong);
            customSpawn = SpawnSearch.findSpawn(worldManager.getGameOverworld(), ConfigManager.config(), server);
        }

        postReset(customSpawn);
    }

    public void createGameWorlds(long seed) {
        VanillaLikeLevels levels = GameWorlds.create(
            server,
            WorldReset.GAME_OVERWORLD,
            WorldReset.GAME_NETHER,
            WorldReset.GAME_END,
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

    private void postReset(@Nullable BlockPos customSpawn) {
        stopConditions.reset();
        setTimeOfDay();
        clearWeather();

        var gameOverworld = worldManager.getGameOverworld();
        BlockPos respawnPos = customSpawn != null ? customSpawn : SpawnFinder.findSpawn(gameOverworld);
        server.setRespawnData(LevelData.RespawnData.of(gameOverworld.dimension(), respawnPos, 0.0F, 0.0F));

        ChunkPos spawnChunk = ChunkPos.containing(respawnPos);
        ServerChunkCache chunkSource = gameOverworld.getChunkSource();
        chunkSource.addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH, spawnChunk, 2);
        ((ServerChunkCacheAccessor) chunkSource).worldreset$invokeRunDistanceManagerUpdates();

        Set<UUID> processedPlayers = new HashSet<>();
        boolean spawnNearNone = ConfigManager.config().spawnNear.type.equals("none");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (customSpawn != null && !spawnNearNone) {
                player.teleportTo(gameOverworld,
                    customSpawn.getX() + 0.5, customSpawn.getY(), customSpawn.getZ() + 0.5,
                    Set.of(), 0.0F, 0.0F, true);
            } else {
                playerManager.teleportToOverworldSpawn(player);
            }
            me.libreh.worldreset.world.PlayerReset.applyConfiguredResets(player);
            processedPlayers.add(player.getUUID());
        }
        playerResetState.resetCycle(processedPlayers);
    }

    private void setTimeOfDay() {
        int timeOfDay = ConfigManager.config().resetOnLoad.timeOfDay;
        if (timeOfDay >= 0) {
            var overworldClock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            server.clockManager().setTotalTicks(overworldClock, timeOfDay);
            WorldReset.LOGGER.debug("Set time of day to {}", timeOfDay);
        }
    }

    private void clearWeather() {
        if (ConfigManager.config().resetOnLoad.clearWeather) {
            server.setWeatherParameters(0, 0, false, false);
            WorldReset.LOGGER.debug("Cleared weather");
        }
    }
}
