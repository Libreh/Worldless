package me.libreh.worldreset.world;

import com.mojang.datafixers.util.Pair;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.SpawnFinder;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.SpawnType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.function.Predicate;

public final class SpawnSearch {
    private static final int SPAWN_SEARCH_MAX_DISTANCE = 10000;
    private static final int SPAWN_SEARCH_RETRIES = 10;

    private SpawnSearch() {}

    public static @Nullable BlockPos findSpawn(ServerLevel overworld, Config.SpawnNear spawnNear, MinecraftServer server) {
        if (spawnNear.type != SpawnType.NONE && !spawnNear.target.isEmpty()) {
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
                BlockPos spawnPos = SpawnFinder.findSpawnNear(overworld, new BlockPos(spawnX, 0, spawnZ));
                if (spawnPos == null) {
                    overworld.getChunk(spawnX >> 4, spawnZ >> 4);
                    int surfaceY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnX, spawnZ);
                    spawnPos = new BlockPos(spawnX, surfaceY, spawnZ);
                }
                WorldReset.LOGGER.info("Set world spawn near {}: {}", spawnNear.target, spawnPos);
                return spawnPos;
            }
            WorldReset.LOGGER.warn("Could not find {} '{}' within {} blocks; using default spawn",
                spawnNear.type, spawnNear.target, SPAWN_SEARCH_MAX_DISTANCE);
            return null;
        }

        BlockPos spawnPos = SpawnFinder.findSpawn(overworld);
        WorldReset.LOGGER.info("Found world spawn: {}", spawnPos);
        return spawnPos;
    }

    private static @Nullable BlockPos findSpawnTarget(ServerLevel overworld, Config.SpawnNear spawnNear, BlockPos searchOrigin) {
        if (spawnNear.type == SpawnType.STRUCTURE) {
            var registry = overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            HolderSet<Structure> holderSet;
            if (spawnNear.target.startsWith("#")) {
                TagKey<Structure> tagKey = TagKey.create(Registries.STRUCTURE, Identifier.parse(spawnNear.target.substring(1)));
                var tag = registry.get(tagKey);
                if (tag.isEmpty()) {
                    WorldReset.LOGGER.warn("Unknown structure tag: {}", spawnNear.target);
                    return null;
                }
                holderSet = tag.get();
            } else {
                var holder = registry.get(ResourceKey.create(Registries.STRUCTURE, Identifier.parse(spawnNear.target)));
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

        } else if (spawnNear.type == SpawnType.BIOME) {
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
