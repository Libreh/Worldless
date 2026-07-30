package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.ChunkMapAccessor;
import me.libreh.worldreset.mixin.world.ServerChunkCacheAccessor;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

public final class ChunkLoading {
    private ChunkLoading() {}

    public static void runDistanceManagerUpdates(ServerChunkCache chunkSource) {
        ((ServerChunkCacheAccessor) chunkSource).worldreset$invokeRunDistanceManagerUpdates();
    }

    public static @Nullable ChunkHolder getVisibleChunkNow(ChunkMap chunkMap, ChunkPos pos) {
        return ((ChunkMapAccessor) chunkMap).worldreset$invokeGetVisibleChunkIfPresent(pos.pack());
    }
}
