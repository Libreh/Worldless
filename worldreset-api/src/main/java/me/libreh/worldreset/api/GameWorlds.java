package me.libreh.worldreset.api;

import net.casual.arcade.dimensions.ArcadeDimensions;
import net.casual.arcade.dimensions.level.LevelPersistence;
import net.casual.arcade.dimensions.level.builder.CustomLevelBuilder;
import net.casual.arcade.dimensions.level.vanilla.VanillaDimension;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevels;
import net.casual.arcade.dimensions.level.vanilla.VanillaLikeLevelsBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class GameWorlds {
    private GameWorlds() {}

    public static VanillaLikeLevels create(
            MinecraftServer server,
            ResourceKey<Level> overworldKey,
            ResourceKey<Level> netherKey,
            ResourceKey<Level> endKey,
            long seed,
            boolean registerWorldborderListeners
    ) {
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

        if (registerWorldborderListeners) {
            server.getPlayerList().addWorldborderListener(levels.getOrThrow(VanillaDimension.Overworld));
            server.getPlayerList().addWorldborderListener(levels.getOrThrow(VanillaDimension.Nether));
            server.getPlayerList().addWorldborderListener(levels.getOrThrow(VanillaDimension.End));
        }

        return levels;
    }
}
