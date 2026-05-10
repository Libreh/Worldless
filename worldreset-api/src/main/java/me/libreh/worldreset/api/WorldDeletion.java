package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.ChunkMapAccessor;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import me.libreh.worldreset.mixin.world.TrackedEntityAccessor;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.io.file.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class WorldDeletion {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldDeletion.class);

    public static void deleteWorlds(MinecraftServer server, CustomLevel... levels) {
        var storage = ((MinecraftServerAccessor) server).getStorageSource();

        // Remove from server on the main thread (fast, prevents ticking)
        for (CustomLevel level : levels) {
            ((MinecraftServerAccessor) server).getLevels().remove(level.dimension());
            ServerLevelEvents.UNLOAD.invoker().onLevelUnload(server, level);
        }

        // Call removeEntity for each tracked entity before close. This lets mods
        // that hook entity tracking (e.g. VMP's use_optimized_entity_tracking) clean up their
        // per-world state, which otherwise never happens because close() skips the unload path.
        for (CustomLevel level : levels) {
            var chunkMap = (ChunkMapAccessor) level.getChunkSource().chunkMap;
            @SuppressWarnings("unchecked")
            var trackers = new ArrayList<>(chunkMap.worldreset$getEntityMap().values());
            for (Object tracker : trackers) {
                chunkMap.worldreset$invokeRemoveEntity(((TrackedEntityAccessor) tracker).worldreset$getEntity());
            }
        }

        // Close on server thread, not async: chunk-system close has main-thread asserts.
        // Off-thread call throws and gets swallowed, leaving C2MEStorageThread alive and
        // pinning the whole ChunkMap.
        for (CustomLevel level : levels) {
            try {
                level.close();
            } catch (Throwable t) {
                LOGGER.error("Failed to close level {}", level.dimension().identifier(), t);
            }
        }

        // Rename original paths so new worlds can be created at them immediately.
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

        // Delete renamed directories off-thread. No level refs captured here.
        CompletableFuture.runAsync(() -> {
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
