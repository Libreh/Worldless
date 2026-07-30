package me.libreh.worldreset.world;

import me.libreh.worldreset.config.ConfigManager;
import net.casual.arcade.dimensions.level.CustomLevel;
import net.casual.arcade.utils.level.LevelUtilsKt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class ActiveWorlds {
    private final MinecraftServer server;

    private CustomLevel overworld;
    private CustomLevel nether;
    private CustomLevel end;

    public ActiveWorlds(MinecraftServer server) {
        this.server = server;
    }

    public CustomLevel overworld() {
        return overworld;
    }

    public CustomLevel nether() {
        return nether;
    }

    public CustomLevel end() {
        return end;
    }

    public void set(CustomLevel overworld, CustomLevel nether, CustomLevel end) {
        this.overworld = overworld;
        this.nether = nether;
        this.end = end;
        if (ConfigManager.config().spoofDimension) {
            LevelUtilsKt.setSpoofedDimension(overworld, Level.OVERWORLD);
            LevelUtilsKt.setSpoofedDimension(nether, Level.NETHER);
            LevelUtilsKt.setSpoofedDimension(end, Level.END);
        }
        ActiveWorldsState.save(server, overworld.dimension(), nether.dimension(), end.dimension());
    }
}
