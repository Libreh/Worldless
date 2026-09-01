package me.libreh.worldreset.api;

import net.minecraft.core.BlockPos;
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
import java.util.concurrent.atomic.AtomicInteger;

public class WorldPreloader {
    public static TicketType ASYNC_CHUNK_TICKET;

    private static final Logger LOGGER = LoggerFactory.getLogger(WorldPreloader.class);

    private static final int MAX_CONCURRENT_CHUNK_LOADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    private final ServerTaskExecutor taskExecutor;
    private boolean preloading = false;
    private boolean preloadingComplete = false;
    private final List<CompletableFuture<ChunkAccess>> chunkFutures = new ArrayList<>();
    private CompletableFuture<Void> currentFuture = CompletableFuture.completedFuture(null);
    private int generation = 0;

    public WorldPreloader(MinecraftServer server) {
        this.taskExecutor = new ServerTaskExecutor(server);
    }

    public CompletableFuture<Void> startPreloading(ServerLevel overworld, float distance) {
        return startPreloading(overworld, overworld.getRespawnData().pos(), distance);
    }

    public CompletableFuture<Void> startPreloading(ServerLevel overworld, BlockPos center, float distance) {
        preloading = true;
        preloadingComplete = false;
        int myGeneration = ++generation;

        ChunkPos spawnChunk = ChunkPos.containing(center);

        List<ChunkPos> chunksToLoad = calculateChunksToLoad(spawnChunk, distance);

        List<CompletableFuture<ChunkAccess>> futures = new ArrayList<>(chunksToLoad.size());
        for (int i = 0; i < chunksToLoad.size(); i++) {
            futures.add(new CompletableFuture<>());
        }

        chunkFutures.clear();
        chunkFutures.addAll(futures);

        AtomicInteger nextIndex = new AtomicInteger(0);
        int initialBatch = Math.min(MAX_CONCURRENT_CHUNK_LOADS, chunksToLoad.size());
        for (int i = 0; i < initialBatch; i++) {
            dispatchNextChunk(overworld, chunksToLoad, futures, nextIndex, myGeneration);
        }

        currentFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
            .whenCompleteAsync((result, throwable) -> {
                onPreloadingComplete();
                preloading = false;
            }, taskExecutor);
        return currentFuture;
    }

    private void dispatchNextChunk(
            ServerLevel overworld, List<ChunkPos> chunksToLoad,
            List<CompletableFuture<ChunkAccess>> futures, AtomicInteger nextIndex, int myGeneration
    ) {
        if (myGeneration != generation) return;
        int index = nextIndex.getAndIncrement();
        if (index >= chunksToLoad.size()) return;

        CompletableFuture<ChunkAccess> target = futures.get(index);
        getChunkAsync(overworld, chunksToLoad.get(index)).whenCompleteAsync((chunk, error) -> {
            if (error != null) target.completeExceptionally(error); else target.complete(chunk);
            dispatchNextChunk(overworld, chunksToLoad, futures, nextIndex, myGeneration);
        }, taskExecutor);
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

    public boolean isPreloading() {
        if (!preloading || chunkFutures.isEmpty()) {
            return false;
        }
        return chunkFutures.stream().anyMatch(future -> !future.isDone());
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<ChunkAccess> getChunkAsync(ServerLevel level, ChunkPos chunkPos) {
        MinecraftServer server = level.getServer();
        if (!server.isRunning()) return CompletableFuture.completedFuture(null);
        if (!server.isSameThread()) {
            return CompletableFuture.supplyAsync(() -> getChunkAsync(level, chunkPos), server).thenCompose(future -> future);
        }

        var chunkManager = level.getChunkSource();
        chunkManager.addTicketAndLoadWithRadius(ASYNC_CHUNK_TICKET, chunkPos, 0);

        ChunkLoading.runDistanceManagerUpdates(chunkManager);

        var loadingManager = chunkManager.chunkMap;
        var chunkHolder = ChunkLoading.getVisibleChunkNow(loadingManager, chunkPos);

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
        generation++;
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
