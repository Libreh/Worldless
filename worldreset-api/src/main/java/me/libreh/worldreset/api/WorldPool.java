package me.libreh.worldreset.api;

import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.dimensions.level.LevelPersistence;
import net.casual.arcade.dimensions.level.builder.CustomLevelBuilder;
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevels;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevelsBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorldPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldPool.class);

    private final MinecraftServer server;
    private final WorldPoolHost host;
    private final ServerTaskExecutor taskExecutor;
    private final WorldPreloader preloader;
    private final String modId;

    private final ConcurrentLinkedQueue<PooledWorlds> readyPool = new ConcurrentLinkedQueue<>();
    private PoolState state = PoolState.IDLE;

    private long pendingSeed;
    private VanillaLikeLevels pendingLevels;
    private ServerLevel pendingOverworld;
    private BlockPos pendingSpawn;
    private CompletableFuture<VanillaLikeLevels> worldCreationFuture;
    private CompletableFuture<BlockPos> spawnSearchFuture;
    private CompletableFuture<Void> preloadFuture;

    public enum PoolState {
        IDLE,
        CREATING,
        SPAWN_FINDING,
        PRELOADING
    }

    public WorldPool(MinecraftServer server, WorldPoolHost host, ServerTaskExecutor taskExecutor, WorldPreloader preloader, String modId) {
        this.server = server;
        this.host = host;
        this.taskExecutor = taskExecutor;
        this.preloader = preloader;
        this.modId = modId;
    }

    private ResourceKey<Level> generatePoolKey(String dimensionType) {
        return DimensionKeys.generate(modId, dimensionType);
    }

    public void kickOff(long seed) {
        if (state != PoolState.IDLE || readyPool.size() >= host.maxPoolSize()) {
            LOGGER.warn("WorldPool.kickOff called while state is {} (pool size={}), ignoring", state, readyPool.size());
            return;
        }
        this.pendingSeed = seed;
        this.state = PoolState.CREATING;
        LOGGER.info("WorldPool: starting background generation (seed={})", seed);
    }

    public void tick() {
        if (state == PoolState.IDLE) {
            if (readyPool.size() < host.maxPoolSize()) {
                kickOff(host.nextSeed());
            }
            return;
        }
        switch (state) {
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
            this.state = PoolState.SPAWN_FINDING;
        } catch (Exception e) {
            LOGGER.error("WorldPool: world creation failed", e);
            state = PoolState.IDLE;
            worldCreationFuture = null;
        }
    }

    private VanillaLikeLevels buildPoolWorlds(long seed) {
        ResourceKey<Level> overworldKey = generatePoolKey("overworld");
        ResourceKey<Level> netherKey = generatePoolKey("nether");
        ResourceKey<Level> endKey = generatePoolKey("end");

        VanillaLikeLevelsBuilder builder = new VanillaLikeLevelsBuilder();
        builder.set(VanillaDimension.Overworld, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.Overworld)
            .dimensionKey(overworldKey)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        builder.set(VanillaDimension.Nether, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.Nether)
            .dimensionKey(netherKey)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        builder.set(VanillaDimension.End, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.End)
            .dimensionKey(endKey)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        VanillaLikeLevels levels = builder.build(server);

        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.Overworld));
        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.Nether));
        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.End));

        return levels;
    }

    private void tickSpawnFinding() {
        if (spawnSearchFuture == null || !spawnSearchFuture.isDone()) return;

        try {
            BlockPos spawn = spawnSearchFuture.getNow(null);
            pendingSpawn = spawn;
            spawnSearchFuture = null;
            if (spawn != null) {
                LOGGER.info("WorldPool: spawn found at {}", spawn);
            } else {
                LOGGER.info("WorldPool: spawn search returned null, will use default on adoption");
            }
        } catch (Exception e) {
            LOGGER.error("WorldPool: spawn search failed, will use default on adoption", e);
            pendingSpawn = null;
            spawnSearchFuture = null;
        }
        BlockPos preloadCenter = pendingSpawn != null ? pendingSpawn : new BlockPos(0, 64, 0);
        startPreloading(preloadCenter);
    }

    private void startPreloading(BlockPos center) {
        float distance = host.resolvePreloadDistance(server);
        if (distance <= 0) {
            finalizePool();
            return;
        }

        preloader.reset();
        preloadFuture = preloader.startPreloading(pendingOverworld, center, distance);
        state = PoolState.PRELOADING;
        LOGGER.info("WorldPool: preloading terrain");
    }

    private void tickPreloading() {
        if (preloadFuture == null || !preloadFuture.isDone()) return;
        if (preloadFuture.isCompletedExceptionally()) {
            LOGGER.info("WorldPool: preload interrupted, restarting");
            startPreloading(pendingSpawn);
            return;
        }
        finalizePool();
    }

    private void finalizePool() {
        PooledWorlds pooled = new PooledWorlds(
            pendingLevels.getOrThrow(VanillaDimension.Overworld),
            pendingLevels.getOrThrow(VanillaDimension.Nether),
            pendingLevels.getOrThrow(VanillaDimension.End),
            pendingSeed,
            pendingSpawn
        );
        readyPool.add(pooled);
        state = PoolState.IDLE;
        pendingLevels = null;
        pendingOverworld = null;
        pendingSpawn = null;
        LOGGER.info("WorldPool: world ready (seed={}), pool size={}", pooled.seed(), readyPool.size());
    }

    @Nullable
    public PooledWorlds claim() {
        PooledWorlds result = readyPool.poll();
        if (result == null) return null;
        LOGGER.info("WorldPool: claimed pooled world, remaining={}", readyPool.size());
        return result;
    }

    public void registerPooledWorlds(PooledWorlds pooled) {
        server.getPlayerList().addWorldborderListener(pooled.overworld());
        server.getPlayerList().addWorldborderListener(pooled.nether());
        server.getPlayerList().addWorldborderListener(pooled.end());
    }

    public boolean isReady() {
        return !readyPool.isEmpty();
    }

    public boolean isGenerating() {
        return state != PoolState.IDLE;
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
        PooledWorlds pooled;
        while ((pooled = readyPool.poll()) != null) {
            deletePooledLevels(pooled.overworld(), pooled.nether(), pooled.end());
        }
        if (preloadFuture != null) preloadFuture.cancel(false);
        if (spawnSearchFuture != null) spawnSearchFuture.cancel(false);
        if (worldCreationFuture != null) worldCreationFuture.cancel(false);
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
        state = PoolState.IDLE;
    }

    private void deletePooledLevels(CustomLevel... levels) {
        for (CustomLevel level : levels) {
            if (ArcadeDimensions.hasCustomLevel(server, level)) {
                ArcadeDimensions.delete(server, level);
            }
        }
    }
}
