package me.libreh.worldreset.world;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.LobbyWorld;
import me.libreh.worldreset.api.WorldDeletion;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.mixin.world.RaidsAccessor;
import me.libreh.worldreset.util.SeedUtil;
import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.LevelPersistence;
import net.casual.arcade.dimensions.level.builder.CustomLevelBuilder;
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevels;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevelsBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelData;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class ResetManager {
    private static final int SPAWN_SEARCH_MAX_DISTANCE = 10000; // blocks
    private static final int SPAWN_SEARCH_RETRIES = 10;
    private final MinecraftServer server;
    private final WorldManager worldManager;
    private final PlayerManager playerManager;
    private final Set<UUID> fountainPlayers;
    private final me.libreh.worldreset.api.ServerTaskExecutor taskExecutor;

    public ResetManager(MinecraftServer server, WorldManager worldManager, PlayerManager playerManager, LobbyWorld lobbyWorld, Set<UUID> fountainPlayers, me.libreh.worldreset.api.ServerTaskExecutor taskExecutor) {
        this.server = server;
        this.worldManager = worldManager;
        this.playerManager = playerManager;
        this.fountainPlayers = fountainPlayers;
        this.taskExecutor = taskExecutor;
    }

    public CompletableFuture<Void> resetWorlds(String seed) {
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            playerManager.preparePlayerForReset(player);
        }

        String seedString = seed.isEmpty() ? ConfigManager.config().seed : seed;
        long seedLong = SeedUtil.parseSeed(seedString);

        tickKeepAlive();
        stopAllRaids();
        saveWorldData();
        deleteGameWorlds();
        tickKeepAlive();
        createGameWorlds(seedLong);
        return postResetAsync();
    }

    public void createGameWorlds(long seed) {
        VanillaLikeLevelsBuilder builder = new VanillaLikeLevelsBuilder();
        builder.set(VanillaDimension.Overworld, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.Overworld)
            .dimensionKey(WorldReset.GAME_OVERWORLD)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        builder.set(VanillaDimension.Nether, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.Nether)
            .dimensionKey(WorldReset.GAME_NETHER)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        builder.set(VanillaDimension.End, new CustomLevelBuilder()
            .vanillaDefaults(VanillaDimension.End)
            .dimensionKey(WorldReset.GAME_END)
            .seed(seed)
            .persistence(LevelPersistence.Persistent));
        VanillaLikeLevels levels = builder.build(server);
        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.Overworld));
        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.Nether));
        ArcadeDimensions.add(server, levels.getOrThrow(VanillaDimension.End));
        worldManager.setGameWorlds(
            levels.getOrThrow(VanillaDimension.Overworld),
            levels.getOrThrow(VanillaDimension.Nether),
            levels.getOrThrow(VanillaDimension.End)
        );
    }

    private void deleteGameWorlds() {
        WorldDeletion.deleteWorlds(server,
            worldManager.getGameOverworld(),
            worldManager.getGameNether(),
            worldManager.getGameEnd()
        );
    }

    private void stopAllRaids() {
        for (ServerLevel world : List.of(worldManager.getGameOverworld(), worldManager.getGameNether(), worldManager.getGameEnd())) {
            RaidsAccessor raidManagerAccessor = (RaidsAccessor) world.getRaids();
            Int2ObjectMap<Raid> raids = raidManagerAccessor.getRaidMap();
            for (Raid raid : List.copyOf(raids.values())) {
                raid.stop();
            }
        }
    }

    private void saveWorldData() {
        WorldReset.LOGGER.debug("Saving player data...");
        server.getPlayerList().saveAll();
        tickKeepAlive();
    }

    private void tickKeepAlive() {
        server.tickConnection();
    }

    private CompletableFuture<Void> postResetAsync() {
        fountainPlayers.clear();
        setTimeOfDay();
        clearWeather();
        return findAndSetSpawnAsync().thenAcceptAsync(customSpawn -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (customSpawn != null) {
                    var gameOverworld = worldManager.getGameOverworld();
                    player.teleportTo(gameOverworld,
                            customSpawn.getX() + 0.5, customSpawn.getY(), customSpawn.getZ() + 0.5,
                            Set.of(), 0.0F, 0.0F, true);
                } else {
                    playerManager.teleportToOverworldSpawn(player);
                }
                me.libreh.worldreset.world.PlayerReset.applyConfiguredResets(player);
            }
        }, taskExecutor);
    }

    private void setTimeOfDay() {
        int timeOfDay = ConfigManager.config().resetOnLoad.timeOfDay;
        if (timeOfDay >= 0) {
            var overworldClock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            server.clockManager().setTotalTicks(overworldClock, timeOfDay);
            WorldReset.LOGGER.debug("Set time of day to {}", timeOfDay);
        }
    }

    private void clearWeather() {
        if (ConfigManager.config().resetOnLoad.clearWeather) {
            server.setWeatherParameters(0, 0, false, false);
            WorldReset.LOGGER.debug("Cleared weather");
        }
    }

    private CompletableFuture<@Nullable BlockPos> findAndSetSpawnAsync() {
        var overworld = worldManager.getGameOverworld();
        var spawnNear = ConfigManager.config().spawnNear;

        if (!spawnNear.type.equals("none") && !spawnNear.target.isEmpty()) {
            return CompletableFuture.supplyAsync(() -> {
                BlockPos located = null;
                BlockPos searchOrigin = BlockPos.ZERO;

                for (int attempt = 0; attempt <= SPAWN_SEARCH_RETRIES; attempt++) {
                    BlockPos candidate = findSpawnTarget(overworld, spawnNear, searchOrigin);
                    if (candidate == null) break;

                    if (spawnNear.requireSurface) {
                        overworld.getChunk(candidate.getX() >> 4, candidate.getZ() >> 4);
                        int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
                        if (candidate.getY() < surfaceY - 10) {
                            WorldReset.LOGGER.warn("Found {} '{}' is underground (y={}, surface={}); {}",
                                    spawnNear.type, spawnNear.target, candidate.getY(), surfaceY,
                                    attempt < SPAWN_SEARCH_RETRIES ? "retrying..." : "using default spawn");
                            double angle = attempt * (Math.PI / 2);
                            searchOrigin = new BlockPos(
                                    (int) (Math.cos(angle) * SPAWN_SEARCH_MAX_DISTANCE),
                                    0,
                                    (int) (Math.sin(angle) * SPAWN_SEARCH_MAX_DISTANCE));
                            continue;
                        }
                    }

                    located = candidate;
                    break;
                }

                if (located != null) {
                    int spawnX = located.getX();
                    int spawnZ = located.getZ();
                    if (spawnNear.offset > 0) {
                        double angle = new Random(overworld.getSeed()).nextDouble() * 2 * Math.PI;
                        spawnX += (int) Math.round(Math.cos(angle) * spawnNear.offset);
                        spawnZ += (int) Math.round(Math.sin(angle) * spawnNear.offset);
                    }
                    BlockPos spawnPos = me.libreh.worldreset.api.SpawnFinder.findSpawnNear(overworld, new BlockPos(spawnX, 0, spawnZ));
                    if (spawnPos == null) {
                        overworld.getChunk(spawnX >> 4, spawnZ >> 4);
                        int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);
                        spawnPos = new BlockPos(spawnX, surfaceY, spawnZ);
                    }
                    server.setRespawnData(LevelData.RespawnData.of(overworld.dimension(), spawnPos, 0.0F, 0.0F));
                    WorldReset.LOGGER.info("Set world spawn near {}: {}", spawnNear.target, spawnPos);
                    return spawnPos;
                }
                WorldReset.LOGGER.warn("Could not find {} '{}' within {} blocks; using default spawn",
                        spawnNear.type, spawnNear.target, SPAWN_SEARCH_MAX_DISTANCE);
                return null;
            }, Util.backgroundExecutor());
        }

        return CompletableFuture.supplyAsync(() -> {
            BlockPos spawnPos = me.libreh.worldreset.api.SpawnFinder.findSpawn(overworld);
            server.setRespawnData(LevelData.RespawnData.of(overworld.dimension(), spawnPos, 0.0F, 0.0F));
            WorldReset.LOGGER.info("Found world spawn: {}", spawnPos);
            return (BlockPos) null;
        }, Util.backgroundExecutor());
    }

    @Nullable
    private BlockPos findSpawnTarget(ServerLevel overworld, Config.SpawnNear spawnNear, BlockPos searchOrigin) {
        if (spawnNear.type.equals("structure")) {
            var registry = overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            HolderSet<Structure> holderSet;
            if (spawnNear.target.startsWith("#")) {
                TagKey<Structure> tagKey = TagKey.create(Registries.STRUCTURE, Identifier.parse(spawnNear.target.substring(1)));
                Optional<HolderSet.Named<Structure>> tag = registry.get(tagKey);
                if (tag.isEmpty()) {
                    WorldReset.LOGGER.warn("Unknown structure tag: {}", spawnNear.target);
                    return null;
                }
                holderSet = tag.get();
            } else {
                Optional<Holder.Reference<Structure>> holder = registry.get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(spawnNear.target)));
                if (holder.isEmpty()) {
                    WorldReset.LOGGER.warn("Unknown structure: {}", spawnNear.target);
                    return null;
                }
                holderSet = HolderSet.direct(holder.get());
            }
            int radiusChunks = Math.min(SPAWN_SEARCH_MAX_DISTANCE / 16, 500);
            Pair<BlockPos, Holder<Structure>> result = overworld.getChunkSource().getGenerator()
                .findNearestMapStructure(overworld, holderSet, searchOrigin, radiusChunks, false);
            if (result == null) return null;

            BlockPos pos = result.getFirst();
            var chunk = overworld.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            StructureStart start = chunk.getAllStarts().get(result.getSecond().value());
            if (start != null && start.isValid()) {
                return new BlockPos(pos.getX(), start.getBoundingBox().maxY(), pos.getZ());
            }
            return pos;

        } else if (spawnNear.type.equals("biome")) {
            Predicate<Holder<Biome>> predicate;
            if (spawnNear.target.startsWith("#")) {
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, Identifier.parse(spawnNear.target.substring(1)));
                predicate = h -> h.is(tagKey);
            } else {
                ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(spawnNear.target));
                predicate = h -> h.is(biomeKey);
            }
            Pair<BlockPos, Holder<Biome>> result = overworld.findClosestBiome3d(
                predicate, searchOrigin, SPAWN_SEARCH_MAX_DISTANCE, 32, 64
            );
            return result != null ? result.getFirst() : null;
        }

        return null;
    }
}
