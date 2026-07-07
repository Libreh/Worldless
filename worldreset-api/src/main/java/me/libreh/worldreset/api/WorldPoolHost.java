package me.libreh.worldreset.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface WorldPoolHost {
    int maxPoolSize();

    long nextSeed();

    float resolvePreloadDistance(MinecraftServer server);

    CompletableFuture<@Nullable BlockPos> findSpawn(ServerLevel overworld);
}
