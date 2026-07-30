package me.libreh.worldreset.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

// T is host-defined data produced during spawn finding and carried on the pooled worlds
// (e.g. Manhunt's circle spawn layout). Hosts without extra data use Void.
public interface WorldPoolHost<T> {
    int maxPoolSize();

    long nextSeed();

    float resolvePreloadDistance(MinecraftServer server);

    // Owns the whole spawn stage: the pool waits for this future, preloads around the returned
    // spawn (falling back to 0,64,0 when null), and stores the data on the PooledWorlds.
    CompletableFuture<SpawnResult<T>> findSpawn(ServerLevel overworld);

    record SpawnResult<T>(@Nullable BlockPos spawn, @Nullable T data) {}
}
