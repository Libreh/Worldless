package me.libreh.worldreset.world;

import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.SpawnType;

public record WorldSnapshot(
    String seed,
    SpawnType spawnType,
    String spawnTarget,
    int spawnOffset,
    boolean spawnRequireSurface
) {
    public static WorldSnapshot fromConfig(Config config) {
        var spawnNear = config.spawnNear;
        return new WorldSnapshot(
            config.seed,
            spawnNear.type,
            spawnNear.target,
            spawnNear.offset,
            spawnNear.requireSurface
        );
    }

    public Config.SpawnNear toSpawnNear() {
        var spawnNear = new Config.SpawnNear();
        spawnNear.type = spawnType;
        spawnNear.target = spawnTarget;
        spawnNear.offset = spawnOffset;
        spawnNear.requireSurface = spawnRequireSurface;
        return spawnNear;
    }
}
