package me.libreh.worldreset;

import me.libreh.worldreset.command.ResetCommand;
import me.libreh.worldreset.command.WorldResetCommand;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.world.LobbyWorld;
import me.libreh.worldreset.world.WorldManager;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WorldReset implements ModInitializer {
	public static final String MOD_ID = "worldreset";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier LOBBY_WORLD_ID = Identifier.fromNamespaceAndPath(MOD_ID, "lobby");

	private static WorldManager worldManager;
    public static WorldManager worlds() {
        return worldManager;
    }

	@Override
	public void onInitialize() {
		ConfigManager.load();

        LobbyWorld lobbyWorld = new LobbyWorld();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            copyDatapack(lobbyWorld);
        }

		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
            WorldResetCommand.register(dispatcher);
            ResetCommand.register(dispatcher);
        });
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
				worldManager = new WorldManager(server, lobbyWorld));
		ServerTickEvents.START_SERVER_TICK.register(server ->
				worldManager.onServerTick());
	}

    public static boolean hasPermission(CommandSourceStack source, String permission) {
        return Permissions.check(source, WorldReset.MOD_ID + "." + permission, PermissionLevel.GAMEMASTERS);
    }

    private void copyDatapack(LobbyWorld lobbyWorld) {
        try {
            String levelName = readLevelName();
            Path datapackPath = FabricLoader.getInstance().getGameDir().resolve(levelName + "/datapacks/worldreset.zip");
            LOGGER.debug("Installing the {} datapack in {}", MOD_ID, datapackPath);
            lobbyWorld.copyDataPack(datapackPath);
        } catch (Exception e) {
            LOGGER.error("Error installing datapack", e);
        }
    }

    private String readLevelName() {
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