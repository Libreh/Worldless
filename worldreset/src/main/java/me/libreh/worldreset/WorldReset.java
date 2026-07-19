package me.libreh.worldreset;

import me.libreh.worldreset.api.LobbyWorld;
import me.libreh.worldreset.api.WorldPreloader;
import me.libreh.worldreset.command.ResetCommand;
import me.libreh.worldreset.command.WorldResetCommand;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.predicate.Predicates;
import me.libreh.worldreset.world.WorldManager;
import me.libreh.worldreset.world.WorldResetHolder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WorldReset implements ModInitializer {
	public static final String MOD_ID = "worldreset";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier LOBBY_WORLD_ID = Identifier.fromNamespaceAndPath(MOD_ID, "lobby");

	public static final ResourceKey<Level> LOBBY_WORLD = ResourceKey.create(Registries.DIMENSION, LOBBY_WORLD_ID);

    public static @Nullable WorldManager worlds(MinecraftServer server) {
        return ((WorldResetHolder) server).worldreset$worldManager();
    }

	@Override
	public void onInitialize() {
		WorldPreloader.ASYNC_CHUNK_TICKET = Registry.register(
			BuiltInRegistries.TICKET_TYPE,
			"worldreset:async_chunk",
			new TicketType(0L, TicketType.FLAG_LOADING)
		);

		Predicates.register();

		ConfigManager.load();

        LobbyWorld lobbyWorld = new LobbyWorld(MOD_ID);
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            installDatapack(lobbyWorld);
        }

		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            WorldResetCommand.register(dispatcher, access);
            ResetCommand.register(dispatcher);
        });
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				((WorldResetHolder) server).worldreset$setWorldManager(new WorldManager(server, lobbyWorld)));
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			WorldManager worlds = worlds(server);
			if (worlds != null) worlds.onServerTick();
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			WorldManager worlds = worlds(destination.getServer());
			if (worlds == null) return;
			if (worlds.triggers.confirmPortalTeleport(player)) {
				worlds.evaluateTriggers();
			}
		});
	}

    public static boolean hasPermission(CommandSourceStack source, String permission) {
        return Permissions.check(source, WorldReset.MOD_ID + "." + permission, PermissionLevel.GAMEMASTERS);
    }

    private void installDatapack(LobbyWorld lobbyWorld) {
        try {
            String levelName = getLevelNameFromProperties();
            Path datapackPath = FabricLoader.getInstance().getGameDir().resolve(levelName + "/datapacks/worldreset.zip");
            LOGGER.debug("Installing the {} datapack in {}", MOD_ID, datapackPath);
            lobbyWorld.copyDataPack(datapackPath);
        } catch (Exception e) {
            LOGGER.error("Error installing datapack", e);
        }
    }

    private String getLevelNameFromProperties() {
        try {
            Path serverProperties = FabricLoader.getInstance().getGameDir().resolve("server.properties");
            List<String> lines = Files.readAllLines(serverProperties);

            String levelName = lines.stream()
                    .filter(line -> line.startsWith("level-name="))
                    .map(line -> line.substring("level-name=".length()))
                    .findFirst()
                    .orElse(null);

            if (levelName != null) {
                return levelName.isEmpty() ? "." : levelName;
            }
            return "world";
        } catch (Exception e) {
            return "world";
        }
    }
}