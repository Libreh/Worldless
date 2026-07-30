package me.libreh.worldreset.api;

import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldPool<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldPool.class);
    private static final int STALL_TICKS = 20 * 90;
    private static final BlockPos FALLBACK_PRELOAD_CENTER = new BlockPos(0, 64, 0);

    private final MinecraftServer server;
    private final WorldPoolHost<T> host;
    private final ServerTaskExecutor taskExecutor;
    private final WorldPreloader preloader;
    private final String modId;

    private final ConcurrentLinkedQueue<PooledWorlds<T>> readyPool = new ConcurrentLinkedQueue<>();
    private PoolState state = PoolState.IDLE;
    private long stateEnteredAtTick = -1;

    private long pendingSeed;
    private VanillaLikeLevels pendingLevels;
    private ServerLevel pendingOverworld;
    private BlockPos pendingSpawn;
    private @Nullable T pendingData;
    private CompletableFuture<VanillaLikeLevels> worldCreationFuture;
    private CompletableFuture<WorldPoolHost.SpawnResult<T>> spawnSearchFuture;
    private CompletableFuture<Void> preloadFuture;

    private List<ResourceKey<Level>> currentKeys = List.of();
    private List<ResourceKey<Level>> previousKeys = List.of();

    public enum PoolState {
        IDLE,
        CREATING,
        SPAWN_FINDING,
        PRELOADING
    }

    public WorldPool(MinecraftServer server, WorldPoolHost<T> host, ServerTaskExecutor taskExecutor, WorldPreloader preloader, String modId) {
        this.server = server;
        this.host = host;
        this.taskExecutor = taskExecutor;
        this.preloader = preloader;
        this.modId = modId;
    }

    private void setState(PoolState newState) {
        this.state = newState;
        this.stateEnteredAtTick = server.getTickCount();
    }

    private boolean isStalled() {
        return stateEnteredAtTick >= 0 && server.getTickCount() - stateEnteredAtTick > STALL_TICKS;
    }

    private ResourceKey<Level> generatePoolKey(String dimensionType) {
        return DimensionKeys.generate(modId, dimensionType);
    }

    public void kickOff(long seed) {
        if (state != PoolState.IDLE || readyPool.size() >= host.maxPoolSize()) {
            if (readyPool.size() >= host.maxPoolSize()) {
                LOGGER.warn("WorldPool.kickOff called while pool is full (size={}), ignoring", readyPool.size());
            } else {
                LOGGER.warn("WorldPool.kickOff called while state is {}, ignoring", state);
            }
            return;
        }
        this.pendingSeed = seed;
        this.pendingData = null;
        setState(PoolState.CREATING);
        LOGGER.info("WorldPool: starting background generation (seed={})", seed);
    }

    public void tick() {
        tick(true);
    }

    public void tick(boolean allowKickOff) {
        if (state != PoolState.IDLE && isStalled()) {
            LOGGER.error("WorldPool: stuck in {} for over {} ticks, forcing recovery", state, STALL_TICKS);
            cleanup();
        }
        switch (state) {
            case IDLE -> {
                if (allowKickOff && readyPool.size() < host.maxPoolSize()) {
                    kickOff(host.nextSeed());
                }
            }
            case CREATING -> tickCreating();
            case SPAWN_FINDING -> tickSpawnFinding();
            case PRELOADING -> tickPreloading();
        }
    }

    private void tickCreating() {
        if (worldCreationFuture == null) {
            long seed = pendingSeed;
            worldCreationFuture = CompletableFuture.supplyAsync(() -> {
                long t0 = System.currentTimeMillis();
                VanillaLikeLevels levels = buildPoolWorlds(seed);
                LOGGER.info("WorldPool: worlds created in {} ms", System.currentTimeMillis() - t0);
                return levels;
            }, taskExecutor);
            return;
        }

        if (!worldCreationFuture.isDone()) return;

        try {
            VanillaLikeLevels levels = worldCreationFuture.getNow(null);
            worldCreationFuture = null;
            if (levels == null) return;

            this.pendingLevels = levels;
            this.pendingOverworld = levels.getOrThrow(VanillaDimension.Overworld);
            this.spawnSearchFuture = host.findSpawn(pendingOverworld);
            setState(PoolState.SPAWN_FINDING);
        } catch (Exception e) {
            LOGGER.error("WorldPool: world creation failed", e);
            setState(PoolState.IDLE);
            worldCreationFuture = null;
        }
    }

    private VanillaLikeLevels buildPoolWorlds(long seed) {
        return GameWorlds.create(
            server,
            generatePoolKey("overworld"),
            generatePoolKey("nether"),
            generatePoolKey("end"),
            seed
        );
    }

    private void tickSpawnFinding() {
        if (spawnSearchFuture == null || !spawnSearchFuture.isDone()) return;

        try {
            WorldPoolHost.SpawnResult<T> result = spawnSearchFuture.getNow(null);
            spawnSearchFuture = null;
            pendingSpawn = result != null ? result.spawn() : null;
            pendingData = result != null ? result.data() : null;
            if (pendingSpawn != null) {
                LOGGER.info("WorldPool: spawn found at {}", pendingSpawn);
            } else {
                LOGGER.info("WorldPool: spawn search returned null, will use default on adoption");
            }
        } catch (Exception e) {
            LOGGER.error("WorldPool: spawn search failed, will use default on adoption", e);
            pendingSpawn = null;
            pendingData = null;
            spawnSearchFuture = null;
        }
        startPreloading(pendingSpawn != null ? pendingSpawn : FALLBACK_PRELOAD_CENTER);
    }

    private void startPreloading(BlockPos center) {
        float distance = host.resolvePreloadDistance(server);
        if (distance <= 0) {
            finalizePool();
            return;
        }

        preloader.reset();
        preloadFuture = preloader.startPreloading(pendingOverworld, center, distance);
        setState(PoolState.PRELOADING);
        LOGGER.info("WorldPool: preloading terrain");
    }

    private void tickPreloading() {
        if (preloadFuture == null || !preloadFuture.isDone()) return;
        if (preloadFuture.isCompletedExceptionally()) {
            LOGGER.info("WorldPool: preload interrupted, restarting");
            startPreloading(pendingSpawn != null ? pendingSpawn : FALLBACK_PRELOAD_CENTER);
            return;
        }
        finalizePool();
    }

    private void finalizePool() {
        PooledWorlds<T> pooled = new PooledWorlds<>(
            pendingLevels.getOrThrow(VanillaDimension.Overworld),
            pendingLevels.getOrThrow(VanillaDimension.Nether),
            pendingLevels.getOrThrow(VanillaDimension.End),
            pendingSeed,
            pendingSpawn,
            pendingData
        );
        readyPool.add(pooled);
        setState(PoolState.IDLE);
        pendingLevels = null;
        pendingOverworld = null;
        pendingSpawn = null;
        pendingData = null;
        LOGGER.info("WorldPool: world ready (seed={}), pool size={}", pooled.seed(), readyPool.size());
    }

    @Nullable
    public PooledWorlds<T> claim() {
        PooledWorlds<T> result = readyPool.poll();
        if (result == null) return null;
        LOGGER.info("WorldPool: claimed pooled world, remaining={}", readyPool.size());
        return result;
    }

    public void registerPooledWorlds(PooledWorlds<T> pooled) {
        this.previousKeys = this.currentKeys;
        this.currentKeys = List.of(pooled.overworld().dimension(), pooled.nether().dimension(), pooled.end().dimension());

        server.getPlayerList().addWorldborderListener(pooled.overworld());
        server.getPlayerList().addWorldborderListener(pooled.nether());
        server.getPlayerList().addWorldborderListener(pooled.end());
    }

    // Keys of the previously adopted trio; lets hosts delete leftovers that survived a reset.
    public List<ResourceKey<Level>> getPreviousPoolKeys() {
        return previousKeys;
    }

    public boolean isReady() {
        return !readyPool.isEmpty();
    }

    public PoolState getState() {
        return state;
    }

    public int getReadyCount() {
        return readyPool.size();
    }

    public void discardAndKickOff(long seed) {
        cleanup();
        kickOff(seed);
    }

    public void cleanup() {
        PooledWorlds<T> pooled;
        while ((pooled = readyPool.poll()) != null) {
            deletePooledLevels(pooled.overworld(), pooled.nether(), pooled.end());
        }
        preloader.reset();
        // Cancelling leaves these non-null; a cancelled future still reports isDone(), so the next
        // kickOff's tick* would see it as "done" and getNow() would throw CancellationException.
        if (preloadFuture != null) {
            preloadFuture.cancel(false);
            preloadFuture = null;
        }
        if (spawnSearchFuture != null) {
            spawnSearchFuture.cancel(false);
            spawnSearchFuture = null;
        }
        if (worldCreationFuture != null) {
            worldCreationFuture.cancel(false);
            worldCreationFuture = null;
        }
        if (pendingLevels != null) {
            deletePooledLevels(
                pendingLevels.getOrThrow(VanillaDimension.Overworld),
                pendingLevels.getOrThrow(VanillaDimension.Nether),
                pendingLevels.getOrThrow(VanillaDimension.End)
            );
        }
        pendingLevels = null;
        pendingOverworld = null;
        pendingSpawn = null;
        pendingData = null;
        setState(PoolState.IDLE);
    }

    private void deletePooledLevels(CustomLevel... levels) {
        for (CustomLevel level : levels) {
            WorldDeletion.deleteDimensionAsync(server, level);
        }
    }
}
