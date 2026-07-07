package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.ChunkMapAccessor;
import me.libreh.worldreset.mixin.world.ServerChunkCacheAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorldPreloader {
    public static TicketType ASYNC_CHUNK_TICKET;

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldPreloader.class);

    private final ServerTaskExecutor taskExecutor;
    private boolean preloading = false;
    private boolean preloadingComplete = false;
    private final List<CompletableFuture<ChunkAccess>> chunkFutures = new ArrayList<>();
    private CompletableFuture<Void> currentFuture = CompletableFuture.completedFuture(null);

    public WorldPreloader(MinecraftServer server) {
        this.taskExecutor = new ServerTaskExecutor(server);
    }

    public CompletableFuture<Void> startPreloading(ServerLevel overworld, float distance) {
        return startPreloading(overworld, overworld.getRespawnData().pos(), distance);
    }

    public CompletableFuture<Void> startPreloading(ServerLevel overworld, BlockPos center, float distance) {
        preloading = true;
        preloadingComplete = false;

        ChunkPos spawnChunk = ChunkPos.containing(center);

        List<ChunkPos> chunksToLoad = calculateChunksToLoad(spawnChunk, distance);

        List<CompletableFuture<ChunkAccess>> futures = chunksToLoad.stream()
            .map(chunk -> getChunkAsync(overworld, chunk))
            .toList();

        chunkFutures.clear();
        chunkFutures.addAll(futures);

        currentFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .whenCompleteAsync((result, throwable) -> {
                onPreloadingComplete();
                preloading = false;
            }, taskExecutor);
        return currentFuture;
    }

    public CompletableFuture<Void> getCurrentFuture() {
        return currentFuture;
    }

    private List<ChunkPos> calculateChunksToLoad(ChunkPos center, float distance) {
        List<ChunkPos> chunks = new ArrayList<>();
        int inner = (int) Math.floor(distance);

        for (int x = -inner; x <= inner; x++) {
            for (int z = -inner; z <= inner; z++) {
                chunks.add(new ChunkPos(center.x() + x, center.z() + z));
            }
        }

        float fraction = distance - inner;
        if (fraction > 0 && inner + 1 > 0) {
            List<ChunkPos> outerRing = new ArrayList<>();
            int outer = inner + 1;
            for (int x = -outer; x <= outer; x++) {
                for (int z = -outer; z <= outer; z++) {
                    if (Math.abs(x) > inner || Math.abs(z) > inner) {
                        outerRing.add(new ChunkPos(center.x() + x, center.z() + z));
                    }
                }
            }
            int toSample = Math.max(1, (int) Math.round(fraction * outerRing.size()));
            Collections.shuffle(outerRing);
            chunks.addAll(outerRing.subList(0, toSample));
        }

        return chunks;
    }

    private void onPreloadingComplete() {
        preloadingComplete = true;
        LOGGER.info("Chunk preloading completed");
    }

    public boolean isPreloadingComplete() {
        return preloadingComplete;
    }

    public Component getChunkLoadingMessage() {
        long loaded = chunkFutures.stream()
            .filter(CompletableFuture::isDone)
            .count();
        long total = chunkFutures.size();

        return Component.translatable("worldreset.world.loading_terrain", loaded, total);
    }

    public boolean isPreloading() {
        if (!preloading || chunkFutures.isEmpty()) {
            return false;
        }
        return chunkFutures.stream().anyMatch(future -> !future.isDone());
    }

    public boolean hasChunks() {
        return !chunkFutures.isEmpty();
    }

    public int loadedChunkCount() {
        int loaded = 0;
        for (CompletableFuture<ChunkAccess> future : chunkFutures) {
            if (future.isDone()) loaded++;
        }
        return loaded;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ChunkAccess> getChunkAsync(ServerLevel level, ChunkPos chunkPos) {
        if (!level.getServer().isSameThread()) {
            return CompletableFuture.supplyAsync(() -> getChunkAsync(level, chunkPos), level.getServer()).thenCompose(future -> future);
        }

        var chunkManager = level.getChunkSource();
        chunkManager.addTicketAndLoadWithRadius(ASYNC_CHUNK_TICKET, chunkPos, 0);

        ((ServerChunkCacheAccessor) chunkManager).worldreset$invokeRunDistanceManagerUpdates();

        var loadingManager = chunkManager.chunkMap;
        var chunkHolder = ((ChunkMapAccessor) loadingManager)
            .worldreset$invokeGetVisibleChunkIfPresent(chunkPos.pack());

        var chunkFuture = (chunkHolder != null
            ? chunkHolder.scheduleChunkGenerationTask(ChunkStatus.FULL, loadingManager)
            .thenApply(optional -> optional.orElse(null))
            : CompletableFuture.completedFuture(null));

        chunkFuture.whenCompleteAsync((chunk, error) ->
                chunkManager.removeTicketWithRadius(ASYNC_CHUNK_TICKET, chunkPos, 0),
            taskExecutor
        );

        return (CompletableFuture<ChunkAccess>) chunkFuture;
    }

    public void reset() {
        currentFuture.cancel(false);
        for (CompletableFuture<ChunkAccess> future : chunkFutures) {
            future.cancel(false);
        }
        preloading = false;
        preloadingComplete = false;
        chunkFutures.clear();
        currentFuture = CompletableFuture.completedFuture(null);
    }
}
