package me.libreh.worldreset.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerResetState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<UUID>>(){}.getType();
    private static final String FILE_NAME = "worldreset_player_state.json";

    private final Path file;
    private final Set<UUID> processed;

    private PlayerResetState(Path file, Set<UUID> processed) {
        this.file = file;
        this.processed = processed;
    }

    public static PlayerResetState load(MinecraftServer server) {
        Path levelRoot = ((MinecraftServerAccessor) server).getStorageSource().getLevelPath(LevelResource.ROOT);
        Path file = levelRoot.resolve(FILE_NAME);
        Set<UUID> processed = new HashSet<>();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                Set<UUID> loaded = GSON.fromJson(reader, SET_TYPE);
                if (loaded != null) processed.addAll(loaded);
            } catch (Exception e) {
                WorldReset.LOGGER.error("Failed to load player reset state", e);
            }
        }
        return new PlayerResetState(file, processed);
    }

    public boolean isProcessed(UUID uuid) {
        return processed.contains(uuid);
    }

    public void markProcessed(UUID uuid) {
        if (processed.add(uuid)) save();
    }

    public void resetCycle(Set<UUID> initiallyProcessed) {
        processed.clear();
        processed.addAll(initiallyProcessed);
        save();
    }

    private void save() {
        try {
            Files.writeString(file, GSON.toJson(processed));
        } catch (Exception e) {
            WorldReset.LOGGER.error("Failed to save player reset state", e);
        }
    }
}
