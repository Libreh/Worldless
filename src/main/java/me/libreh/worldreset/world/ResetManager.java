package me.libreh.worldreset.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.mixin.world.BlockableEventLoopAccessor;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import me.libreh.worldreset.mixin.world.PrimaryLevelDataAccessor;
import me.libreh.worldreset.mixin.world.RaidsAccessor;
import me.libreh.worldreset.util.SeedUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.scores.Objective;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ResetManager {
    private static final String[] WORLD_DATA_DIRECTORIES = {"region", "poi", "entities"};
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final LobbyWorld lobbyWorld;
    private final Set<UUID> fountainPlayers;

    public ResetManager(MinecraftServer server, PlayerManager playerManager, LobbyWorld lobbyWorld, Set<UUID> fountainPlayers) {
        this.server = server;
        this.playerManager = playerManager;
        this.lobbyWorld = lobbyWorld;
        this.fountainPlayers = fountainPlayers;
    }

    public void resetWorlds(String seed) {
        String seedString = seed;
        if (seedString.isEmpty()) {
            seedString = ConfigManager.config().seed;
        }
        long seedLong = SeedUtil.parseSeed(seedString);

        tickKeepAlive();
        clearScoreboardObjectives();
        invalidateRaids();

        setSaving(true);
        try {
            saveWorldData();
            cancelTasks();
            WorldReset.worlds().setCancelSaving(true);
            closeAndDeleteWorlds();
            lobbyWorld.prepareLobbyFiles(server);
            tickKeepAlive();
            loadNewWorlds(seedLong);
        } finally {
            setSaving(false);
            WorldReset.worlds().setCancelSaving(false);
        }
        completeWorldReset(seedLong);
    }

    private void clearScoreboardObjectives() {
        List<Objective> objectives = new ArrayList<>(server.getScoreboard().getObjectives());
        for (Objective objective : objectives) {
            try {
                server.getScoreboard().removeObjective(objective);
            } catch (Throwable e) {
                WorldReset.LOGGER.error("Error removing objective:", e);
            }
        }
    }

    private void invalidateRaids() {
        for (ServerLevel world : server.getAllLevels()) {
            RaidsAccessor raidManagerAccessor = (RaidsAccessor) world.getRaids();
            Int2ObjectMap<Raid> raids = raidManagerAccessor.getRaidMap();
            for (Raid raid : raids.values()) {
                raid.stop();
            }
        }
    }

    private void setSaving(boolean saving) {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        serverAccessor.setIsSaving(saving);
    }

    private void saveWorldData() {
        long saveStartTime = System.currentTimeMillis();
        WorldReset.LOGGER.debug("Saving...");

        server.getPlayerList().saveAll();

        for (ServerLevel world : server.getAllLevels()) {
            try {
                world.getDataStorage().saveAndJoin();
            } catch (Exception e) {
                WorldReset.LOGGER.error("Error saving persistent state for world {}: {}",
                        world.dimension().registry(), e.getMessage(), e);
            }
        }

        long saveDuration = System.currentTimeMillis() - saveStartTime;
        WorldReset.LOGGER.debug("Saving completed in {}ms", saveDuration);
        tickKeepAlive();
    }

    private void cancelTasks() {
        BlockableEventLoopAccessor blockableEventLoopAccessor = (BlockableEventLoopAccessor) server;
        blockableEventLoopAccessor.invokeDropAllTasks();
    }

    private void closeAndDeleteWorlds() {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;

        for (ServerLevel world : server.getAllLevels()) {
            closeWorld(world);
            tickKeepAlive();
            deleteWorldData(serverAccessor, world);
            tickKeepAlive();
        }
    }

    private void closeWorld(ServerLevel world) {
        long closeStartTime = System.currentTimeMillis();
        WorldReset.LOGGER.debug("Closing {}...", world.dimension().registry());

        try {
            world.close();
        } catch (IOException e) {
            WorldReset.LOGGER.error("Error closing world {}", world.dimension().registry(), e);
        }

        long closeDuration = System.currentTimeMillis() - closeStartTime;
        WorldReset.LOGGER.debug("Closing {} completed in {}ms", world.dimension().registry(), closeDuration);
    }

    private void deleteWorldData(MinecraftServerAccessor serverAccessor, ServerLevel world) {
        Path worldDirectory = serverAccessor.getStorageSource().getDimensionPath(world.dimension());

        for (String subDir : WORLD_DATA_DIRECTORIES) {
            File directory = worldDirectory.resolve(subDir).toFile();
            if (directory.exists()) {
                deleteRecursively(directory);
            }
        }
    }

    private void loadNewWorlds(long seed) {
        long loadStartTime = System.currentTimeMillis();
        WorldReset.LOGGER.debug("Loading new worlds...");

        PrimaryLevelDataAccessor levelPropertiesAccessor = (PrimaryLevelDataAccessor) server.getWorldData();
        server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
        levelPropertiesAccessor.setWorldOptions(
                server.getWorldData().worldGenOptions().withSeed(OptionalLong.of(seed))
        );

        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        serverAccessor.invokeLoadLevel();

        long loadDuration = System.currentTimeMillis() - loadStartTime;
        WorldReset.LOGGER.debug("Loading new worlds completed in {}ms", loadDuration);
        tickKeepAlive();
    }

    private void tickKeepAlive() {
        server.tickConnection();
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void completeWorldReset(long seed) {
        fountainPlayers.clear();
        setServerSpawn();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playerManager.updatePlayer(player);
        }
//        resetEnderDragonFight(seed);
    }

    private void setServerSpawn() {
        var overworld = server.overworld();

        ServerLevelData serverLevelData = server.getWorldData().overworldData();

        WorldOptions worldOptions = server.getWorldData().worldGenOptions();
        boolean debug = server.getWorldData().isDebugWorld();

        MinecraftServer.setInitialSpawn(overworld, serverLevelData, worldOptions.generateBonusChest(), debug, server.levelLoadListener);
    }

//    private void resetEnderDragonFight(long seed) {
//        server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
//        ServerLevel endWorld = server.getLevel(Level.END);
//        endWorld.setDragonFight(new EndDragonFight(endWorld, seed, server.getWorldData().endDragonFightData()));
//    }
} 