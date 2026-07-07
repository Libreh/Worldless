package me.libreh.worldreset.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.mixin.world.MinecraftServerAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ActiveWorldsState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "worldreset_active_worlds.json";

    public record ActiveKeys(ResourceKey<Level> overworld, ResourceKey<Level> nether, ResourceKey<Level> end) {}

    private ActiveWorldsState() {}

    public static void save(MinecraftServer server, ResourceKey<Level> overworld, ResourceKey<Level> nether, ResourceKey<Level> end) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("overworld", overworld.identifier().toString());
            json.addProperty("nether", nether.identifier().toString());
            json.addProperty("end", end.identifier().toString());
            Files.writeString(resolveFile(server), GSON.toJson(json));
        } catch (Exception e) {
            WorldReset.LOGGER.error("Failed to save active worlds state", e);
        }
    }

    public static @Nullable ActiveKeys load(MinecraftServer server) {
        Path file = resolveFile(server);
        if (!Files.exists(file)) return null;
        try (var reader = Files.newBufferedReader(file)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return null;
            ResourceKey<Level> overworld = parseKey(json, "overworld");
            ResourceKey<Level> nether = parseKey(json, "nether");
            ResourceKey<Level> end = parseKey(json, "end");
            if (overworld == null || nether == null || end == null) return null;
            return new ActiveKeys(overworld, nether, end);
        } catch (Exception e) {
            WorldReset.LOGGER.error("Failed to load active worlds state", e);
            return null;
        }
    }

    public static void clear(MinecraftServer server) {
        try {
            Files.deleteIfExists(resolveFile(server));
        } catch (Exception e) {
            WorldReset.LOGGER.error("Failed to clear active worlds state", e);
        }
    }

    private static Path resolveFile(MinecraftServer server) {
        Path levelRoot = ((MinecraftServerAccessor) server).getStorageSource().getLevelPath(LevelResource.ROOT);
        return levelRoot.resolve(FILE_NAME);
    }

    private static @Nullable ResourceKey<Level> parseKey(JsonObject json, String field) {
        if (!json.has(field)) return null;
        String id = json.get(field).getAsString();
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id));
    }
}
