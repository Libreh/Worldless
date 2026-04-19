package me.libreh.worldreset.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

public class SpawnFinder {
    private static final int MAX_STEPS = Mth.square(11);

    @Nullable
    public static BlockPos findSpawnNear(ServerLevel world, BlockPos center) {
        ChunkPos startChunk = new ChunkPos(center);

        int dx = 0, dz = 0, ddx = 0, ddz = -1;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (dx >= -5 && dx <= 5 && dz >= -5 && dz <= 5) {
                ChunkPos candidate = new ChunkPos(startChunk.x + dx, startChunk.z + dz);
                world.getChunk(candidate.x, candidate.z);
                BlockPos pos = PlayerSpawnFinder.getSpawnPosInChunk(world, candidate);
                if (pos != null) {
                    return pos;
                }
            }
            if (dx == dz || (dx < 0 && dx == -dz) || (dx > 0 && dx == 1 - dz)) {
                int tmp = ddx;
                ddx = -ddz;
                ddz = tmp;
            }
            dx += ddx;
            dz += ddz;
        }

        return null;
    }

    public static BlockPos findSpawn(ServerLevel world) {
        ServerChunkCache chunkSource = world.getChunkSource();

        BlockPos bestPos = chunkSource.randomState().sampler().findSpawnPosition();
        ChunkPos startChunk = new ChunkPos(bestPos);

        int spawnHeight = chunkSource.getGenerator().getSpawnHeight(world);
        if (spawnHeight < world.getMinY()) {
            BlockPos worldPos = startChunk.getWorldPosition();
            spawnHeight = world.getHeight(Heightmap.Types.WORLD_SURFACE,
                    worldPos.getX() + 8, worldPos.getZ() + 8);
        }
        BlockPos fallback = startChunk.getWorldPosition().offset(8, spawnHeight, 8);

        int dx = 0, dz = 0, ddx = 0, ddz = -1;
        for (int step = 0; step < MAX_STEPS; step++) {
            if (dx >= -5 && dx <= 5 && dz >= -5 && dz <= 5) {
                ChunkPos candidate = new ChunkPos(startChunk.x + dx, startChunk.z + dz);
                world.getChunk(candidate.x, candidate.z);
                BlockPos pos = PlayerSpawnFinder.getSpawnPosInChunk(world, candidate);
                if (pos != null) {
                    return pos;
                }
            }
            if (dx == dz || (dx < 0 && dx == -dz) || (dx > 0 && dx == 1 - dz)) {
                int tmp = ddx;
                ddx = -ddz;
                ddz = tmp;
            }
            dx += ddx;
            dz += ddz;
        }

        return fallback;
    }
}
