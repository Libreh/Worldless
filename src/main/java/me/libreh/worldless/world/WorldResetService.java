package me.libreh.worldless.world;

import me.libreh.worldless.Worldless;
import me.libreh.worldless.mixin.LevelPropertiesAccessor;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

public class WorldResetService {
    private static final String[] WORLD_DATA_DIRECTORIES = {"region", "poi", "entities"};
    private final MinecraftServer server;
    private final PlayerManager playerManager;
    private final LobbyWorldService lobbyWorldService;
    private final Set<UUID> fountainPlayers;

    public WorldResetService(
        MinecraftServer server,
        PlayerManager playerManager,
        LobbyWorldService lobbyWorldService,
        Set<UUID> fountainPlayers
    ) {
        this.server = server;
        this.playerManager = playerManager;
        this.lobbyWorldService = lobbyWorldService;
        this.fountainPlayers = fountainPlayers;
    }

    public void resetWorlds(long seed) {
        try {
            prepareWorldReset();
            saveWorldData();
            deleteWorldFiles();
            regenerateWorld(seed);
        } catch (IOException e) {
            Worldless.LOGGER.error("World reset failed", e);
        } finally {
            server.saving = false;
        }
        completeWorldReset();
    }

    private void prepareWorldReset() {
        server.saving = true;
        tickKeepAlive();
    }

    private void saveWorldData() throws IOException {
        server.getPlayerManager().saveAllPlayerData();
        for (ServerWorld world : server.getWorlds()) {
            world.getPersistentStateManager().save();
            tickKeepAlive();
        }
    }

    private void deleteWorldFiles() throws IOException {
        for (ServerWorld world : server.getWorlds()) {
            world.close();
            tickKeepAlive();
            Path worldDir = server.session.getWorldDirectory(world.getRegistryKey());
            for (String dir : WORLD_DATA_DIRECTORIES) {
                Path targetDir = worldDir.resolve(dir);
                if (Files.exists(targetDir)) {
                    deleteDirectoryRecursively(targetDir);
                }
            }
            tickKeepAlive();
        }
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        Worldless.LOGGER.warn("Failed to delete file: {}", p, e);
                    }
                });
    }

    private void regenerateWorld(long seed) {
        LevelPropertiesAccessor accessor = (LevelPropertiesAccessor) server.getSaveProperties();
        accessor.setGeneratorOptions(accessor.getGeneratorOptions().withSeed(OptionalLong.of(seed)));
        server.loadWorld();
        tickKeepAlive();
    }

    private void completeWorldReset() {
        lobbyWorldService.unzipLobbyWorld();
        fountainPlayers.clear();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            playerManager.updatePlayer(player);
        }
        server.getSaveProperties().setDragonFight(EnderDragonFight.Data.DEFAULT);
    }

    private void tickKeepAlive() {
        if (server.getNetworkIo() != null) {
            server.getNetworkIo().tick();
        }
    }
} 