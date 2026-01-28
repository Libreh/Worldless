package me.libreh.worldless.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldless.Worldless;
import me.libreh.worldless.config.ConfigManager;
import me.libreh.worldless.mixin.world.RaidsAccessor;
import me.libreh.worldless.mixin.world.ThreadExecutorAccessor;
import me.libreh.worldless.mixin.world.LevelPropertiesAccessor;
import me.libreh.worldless.mixin.world.MinecraftServerAccessor;
import me.libreh.worldless.util.SeedUtils;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ResetManager {
    private static final String[] WORLD_DATA_DIRECTORIES = {"region", "poi", "entities"};
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final LobbyWorld lobbyWorldService;
    private final Set<UUID> fountainPlayers;
    private boolean shouldCancelSaving = false;

    public ResetManager(MinecraftServer server, PlayerManager playerManager, LobbyWorld lobbyWorldService, Set<UUID> fountainPlayers) {
        this.server = server;
        this.playerManager = playerManager;
        this.lobbyWorldService = lobbyWorldService;
        this.fountainPlayers = fountainPlayers;
    }

    public void resetWorlds() {
        String seedString = ConfigManager.getInstance().getConfig().seed;
        long seed = SeedUtils.parseSeed(seedString);

        tickKeepAlive();
        clearScoreboardObjectives();
        invalidateRaids();

        setSaving(true);
        try {
            saveWorldData();
            cancelTasks();
            shouldCancelSaving = true;
            closeAndDeleteWorlds();
            tickKeepAlive();
            loadNewWorlds(seed);
        } finally {
            setSaving(false);
            shouldCancelSaving = false;
        }
        completeWorldReset(seed);
    }

    private void clearScoreboardObjectives() {
        List<ScoreboardObjective> objectives = new ArrayList<>(server.getScoreboard().getObjectives());
        for (ScoreboardObjective objective : objectives) {
            try {
                server.getScoreboard().removeObjective(objective);
            } catch (Throwable e) {
                Worldless.LOGGER.error("Error removing objective:", e);
            }
        }
    }

    private void invalidateRaids() {
        for (ServerWorld world : server.getWorlds()) {
            RaidsAccessor raidManagerAccessor = (RaidsAccessor) world.getRaidManager();
            Int2ObjectMap<Raid> raids = raidManagerAccessor.getRaids();
            for (Raid raid : raids.values()) {
                raid.invalidate();
            }
        }
    }

    private void setSaving(boolean saving) {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        serverAccessor.setIsSaving(saving);
    }

    private void saveWorldData() {
        long saveStartTime = System.currentTimeMillis();
        Worldless.LOGGER.debug("Saving...");

        server.getPlayerManager().saveAllPlayerData();

        for (ServerWorld world : server.getWorlds()) {
            try {
                world.getPersistentStateManager().save();
            } catch (Exception e) {
                Worldless.LOGGER.error("Error saving persistent state for world {}: {}",
                        world.getRegistryKey().getValue(), e.getMessage(), e);
            }
        }

        long saveDuration = System.currentTimeMillis() - saveStartTime;
        Worldless.LOGGER.debug("Saving completed in {}ms", saveDuration);
        tickKeepAlive();
    }

    private void cancelTasks() {
        ThreadExecutorAccessor threadExecutorAccessor = (ThreadExecutorAccessor) server;
        threadExecutorAccessor.invokeDropAllTasks();
    }

    private void closeAndDeleteWorlds() {
        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;

        for (ServerWorld world : server.getWorlds()) {
            closeWorld(world);
            deleteWorldData(serverAccessor, world);
            tickKeepAlive();
        }
    }

    private void closeWorld(ServerWorld world) {
        long closeStartTime = System.currentTimeMillis();
        Worldless.LOGGER.debug("Closing {}...", world.getRegistryKey().getValue());

        try {
            world.close();
        } catch (IOException e) {
            Worldless.LOGGER.error("Error closing world {}", world.getRegistryKey().getValue(), e);
        }

        long closeDuration = System.currentTimeMillis() - closeStartTime;
        Worldless.LOGGER.debug("Closing {} completed in {}ms", world.getRegistryKey().getValue(), closeDuration);
    }

    private void deleteWorldData(MinecraftServerAccessor serverAccessor, ServerWorld world) {
        Path worldDirectory = serverAccessor.getStorageSource().getWorldDirectory(world.getRegistryKey());
        String[] subDirectories = {"region", "poi", "entities"};

        for (String subDir : subDirectories) {
            File directory = worldDirectory.resolve(subDir).toFile();
            if (directory.exists()) {
                deleteRecursively(directory);
            }
        }
    }

    private void loadNewWorlds(long seed) {
        long loadStartTime = System.currentTimeMillis();
        Worldless.LOGGER.debug("Loading new worlds...");

        LevelPropertiesAccessor levelPropertiesAccessor = (LevelPropertiesAccessor) server.getSaveProperties();
        server.getSaveProperties().setDragonFight(net.minecraft.entity.boss.dragon.EnderDragonFight.Data.DEFAULT);
        levelPropertiesAccessor.setGeneratorOptions(
                server.getSaveProperties().getGeneratorOptions().withSeed(OptionalLong.of(seed))
        );

        MinecraftServerAccessor serverAccessor = (MinecraftServerAccessor) server;
        serverAccessor.invokeLoadLevel();

        long loadDuration = System.currentTimeMillis() - loadStartTime;
        Worldless.LOGGER.debug("Loading new worlds completed in {}ms", loadDuration);
        tickKeepAlive();
    }

    private void tickKeepAlive() {
        server.tickNetworkIo();
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
        lobbyWorldService.unzipLobbyWorld();
        fountainPlayers.clear();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            playerManager.updatePlayer(player);
        }
        resetEnderDragonFight(seed);
    }

    private void resetEnderDragonFight(long seed) {
        server.getSaveProperties().setDragonFight(EnderDragonFight.Data.DEFAULT);
        ServerWorld endWorld = server.getWorld(World.END);
        endWorld.setEnderDragonFight(new EnderDragonFight(endWorld, seed, server.getSaveProperties().getDragonFight()));
    }
} 