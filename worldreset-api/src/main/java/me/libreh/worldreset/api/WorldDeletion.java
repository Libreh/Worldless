package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.file.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class WorldDeletion {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDeletion.class);

    /**
     * Optimized world deletion: removes from server immediately, renames dirs (O(1)),
     * then closes levels and deletes files off-thread.
     */
    public static void deleteWorlds(MinecraftServer server, CustomLevel... levels) {
        var storage = ((MinecraftServerAccessor) server).getStorageSource();

        // Remove from server on the main thread (fast, prevents ticking)
        for (CustomLevel level : levels) {
            ((MinecraftServerAccessor) server).getLevels().remove(level.dimension());
            ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
        }

        // Rename directories to temp names before new worlds are created at the same paths
        // Rename is O(1) on the same filesystem and avoids race conditions with IO workers
        Path[] tempPaths = new Path[levels.length];
        for (int i = 0; i < levels.length; i++) {
            Path original = storage.getDimensionPath(levels[i].dimension());
            Path temp = original.resolveSibling(original.getFileName() + "_deleting_" + System.nanoTime());
            try {
                if (Files.exists(original)) {
                    Files.move(original, temp);
                    tempPaths[i] = temp;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to rename {}, falling back to direct delete", original, e);
                tempPaths[i] = original;
            }
        }

        // Close levels and delete renamed directories off-thread
        CompletableFuture.runAsync(() -> {
            for (CustomLevel level : levels) {
                try {
                    level.close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close level {}", level.dimension().identifier(), e);
                }
            }
            for (Path path : tempPaths) {
                if (path == null) continue;
                try {
                    PathUtils.deleteDirectory(path);
                } catch (IOException e) {
                    LOGGER.error("Failed to delete {}", path, e);
                }
            }
        });
    }
}
