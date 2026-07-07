package me.libreh.worldreset.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class VanillaDims {
    private VanillaDims() {}

    public static boolean is(ResourceKey<Level> key) {
        return key == Level.OVERWORLD || key == Level.NETHER || key == Level.END;
    }

    public static boolean is(ServerLevel level) {
        return is(level.dimension());
    }
}
