package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.ChunkMapAccessor;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import me.libreh.worldreset.mixin.world.TrackedEntityAccessor;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.io.file.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldDeletion {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDeletion.class);

    private static final String[] CHUNK_SUBFOLDERS = {"region", "poi", "entities"};

    public static void resetWorldChunks(MinecraftServer server, CustomLevel... levels) {
        var storage = ((MinecraftServerAccessor) server).getStorageSource();

        for (CustomLevel level : levels) {
            ((MinecraftServerAccessor) server).getLevels().remove(level.dimension());
            ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
        }

        for (CustomLevel level : levels) {
            var chunkMap = (ChunkMapAccessor) level.getChunkSource().chunkMap;
            @SuppressWarnings("unchecked")
            var trackers = new ArrayList<>(chunkMap.worldreset$getEntityMap().values());
            for (Object tracker : trackers) {
                chunkMap.worldreset$invokeRemoveEntity(((TrackedEntityAccessor) tracker).worldreset$getEntity());
            }
        }

        ResetFlags.setSkipCloseSave(true);
        try {
            for (CustomLevel level : levels) {
                try {
                    level.close();
                } catch (Throwable t) {
                    LOGGER.error("Failed to close level {}", level.dimension().identifier(), t);
                }
            }
        } finally {
            ResetFlags.setSkipCloseSave(false);
        }

        List<Path> toDeleteAsync = new ArrayList<>();
        for (CustomLevel level : levels) {
            Path dimPath = storage.getDimensionPath(level.dimension());

            for (String sub : CHUNK_SUBFOLDERS) {
                Path subPath = dimPath.resolve(sub);
                if (!Files.exists(subPath)) continue;
                Path temp = subPath.resolveSibling(sub + "_deleting_" + System.nanoTime());
                try {
                    Files.move(subPath, temp);
                    toDeleteAsync.add(temp);
                } catch (IOException e) {
                    LOGGER.error("Failed to rename {}, falling back to direct delete", subPath, e);
                    toDeleteAsync.add(subPath);
                }
            }

            deleteQuietly(dimPath.resolve("data").resolve("arcade"));
            deleteQuietly(dimPath.resolve("data").resolve("minecraft"));
        }

        Path mapsDir = storage.getLevelPath(LevelResource.DATA).resolve("minecraft").resolve("maps");
        if (Files.isDirectory(mapsDir)) {
            try (var entries = Files.list(mapsDir)) {
                entries.forEach(WorldDeletion::deleteQuietly);
            } catch (IOException e) {
                LOGGER.error("Failed to list map data dir {}", mapsDir, e);
            }
        }

        if (!toDeleteAsync.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                for (Path path : toDeleteAsync) {
                    try {
                        PathUtils.deleteDirectory(path);
                    } catch (IOException e) {
                        LOGGER.error("Failed to delete {}", path, e);
                    }
                }
            });
        }
    }

    public static void deleteDimensionAsync(MinecraftServer server, CustomLevel level) {
        var accessor = (MinecraftServerAccessor) server;
        ResourceKey<Level> dimension = level.dimension();
        Path directory = accessor.getStorageSource().getDimensionPath(dimension);

        if (!accessor.getLevels().remove(dimension, level)) {
            return;
        }
        level.onUnload();
        ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
        try {
            level.close();
        } catch (IOException e) {
            LOGGER.error("Failed to close level {} before async delete", dimension.identifier(), e);
        }

        Path toDelete = directory;
        if (Files.isDirectory(directory)) {
            Path renamed = directory.resolveSibling(directory.getFileName() + "_deleting_" + System.nanoTime());
            try {
                Files.move(directory, renamed);
                toDelete = renamed;
            } catch (IOException e) {
                LOGGER.warn("Failed to rename {} before delete, deleting in place", directory, e);
            }
        }

        Path finalPath = toDelete;
        CompletableFuture.runAsync(() -> deleteQuietly(finalPath));
    }

    private static void deleteQuietly(Path path) {
        if (!Files.exists(path)) return;
        try {
            if (Files.isDirectory(path)) {
                PathUtils.deleteDirectory(path);
            } else {
                Files.delete(path);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete {}", path, e);
        }
    }
}
