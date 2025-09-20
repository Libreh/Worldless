package me.libreh.worldless;

import me.libreh.worldless.command.Commands;
import me.libreh.worldless.config.ConfigManager;
import me.libreh.worldless.world.WorldManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldlessMod implements ModInitializer {
	public static final String MOD_ID = "worldless";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier LOBBY_WORLD_ID = Identifier.of(MOD_ID, "lobby");

	private static WorldManager worldManager;

	@Override
	public void onInitialize() {
		ConfigManager.getInstance().loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
				Commands.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTING.register(server ->
				worldManager = new WorldManager(server));
		ServerTickEvents.START_SERVER_TICK.register(server ->
				worldManager.onServerTick());
	}

	public static WorldManager getWorldManager() {
		return worldManager;
	}
}