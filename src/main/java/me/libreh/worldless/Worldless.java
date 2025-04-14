package me.libreh.worldless;

import me.libreh.worldless.command.Commands;
import me.libreh.worldless.config.ConfigManager;
import me.libreh.worldless.mixin.LevelPropertiesAccessor;
import me.libreh.worldless.world.ServerTaskExecutor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Worldless implements ModInitializer {
	public static final String MOD_ID = "worldless";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Identifier LOBBY_WORLD_ID = Identifier.of(MOD_ID, "lobby");
	public static final String LOBBY_WORLD_ZIP_PATH = "/worldless/lobby_world.zip";
	public static final int TICKS_PER_SECOND = 20;
	public static final int COUNTDOWN_SOUND_THRESHOLD = 10;
	private static final String[] WORLD_DATA_DIRECTORIES = {"region", "poi", "entities"};
	private static final int COUNTDOWN_SOUND_BASE_PITCH = 2;
	private static final float COUNTDOWN_SOUND_PITCH_DECREMENT = 0.2F;

	public static MinecraftServer server;
	public static boolean shouldCancelSaving;
	public static boolean isCountdownRunning;
	public static int tickCounter;
	public static int worldTimer;
	public static int resetTimer;
	public static ServerTaskExecutor taskExecutor;
	public static final Set<UUID> fountainPlayers = new HashSet<>();

	@Override
	public void onInitialize() {
		registerCommands();
		registerServerLifecycleEvents();
		registerTickHandler();
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
				Commands.registerCommands(dispatcher));
	}

	private void registerServerLifecycleEvents() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Worldless.server = server;
			ConfigManager.loadConfig();
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			taskExecutor = new ServerTaskExecutor(server);
			unzipLobbyWorld();
		});
	}

	private void registerTickHandler() {
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (!isCountdownRunning) return;

			if (++tickCounter < TICKS_PER_SECOND - 1) return;
			tickCounter = 0;

			worldTimer -= TICKS_PER_SECOND;

			if (worldTimer <= 0) {
				isCountdownRunning = false;
				resetWorlds(server, RandomSeed.getSeed());
				return;
			}

			updateTimerDisplay(server);
		});
	}

	private void updateTimerDisplay(MinecraftServer server) {
		int totalSeconds = worldTimer / TICKS_PER_SECOND;
		int minutes = (totalSeconds % 3600) / 60;
		int seconds = totalSeconds % 60;

		String timeString = String.format("%02d:%02d", minutes, seconds);
		Formatting formatting = getTimeFormatting(minutes, seconds);

		Text timerText = Text.literal(timeString).formatted(formatting);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(timerText, true);
        }

        playCountdownSounds(server.getPlayerManager().getPlayerList(), minutes, seconds);
	}

	private Formatting getTimeFormatting(int minutes, int seconds) {
		return minutes > 0 ? Formatting.GRAY :
				seconds > COUNTDOWN_SOUND_THRESHOLD ? Formatting.RED : Formatting.DARK_RED;
	}

	private void playCountdownSounds(List<ServerPlayerEntity> players, int minutes, int seconds) {
		if (!ConfigManager.getConfig().countdownSounds || minutes != 0 || seconds > COUNTDOWN_SOUND_THRESHOLD) {
			return;
		}

		float pitch = COUNTDOWN_SOUND_BASE_PITCH - (seconds * COUNTDOWN_SOUND_PITCH_DECREMENT);
        for (ServerPlayerEntity player : players) {
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 1.0F, pitch);
        }
    }

	public static void resetWorlds(MinecraftServer server, long seed) {
		performWithKeepAlive(() -> {
			server.saving = true;
			try {
				saveWorldData(server);
				deleteWorldFiles(server);
				regenerateWorld(server, seed);
			} catch (IOException e) {
				LOGGER.error("Failed to reset worlds", e);
			} finally {
				server.saving = false;
				shouldCancelSaving = false;
			}
			postResetActions(server);
		});
	}

	private static void performWithKeepAlive(Runnable action) {
		if (server.getNetworkIo() != null) {
			server.getNetworkIo().tick();
		}
		action.run();
	}

	private static void saveWorldData(MinecraftServer server) throws IOException {
		server.getPlayerManager().saveAllPlayerData();
		for (ServerWorld world : server.getWorlds()) {
			world.getPersistentStateManager().save();
			performWithKeepAlive(() -> {});
		}
	}

	private static void deleteWorldFiles(MinecraftServer server) throws IOException {
		for (ServerWorld world : server.getWorlds()) {
			world.close();
			performWithKeepAlive(() -> {});

			Path worldDir = server.session.getWorldDirectory(world.getRegistryKey());
			for (String dir : WORLD_DATA_DIRECTORIES) {
				File file = worldDir.resolve(dir).toFile();
				if (file.exists()) {
					deleteRecursively(file);
				}
			}
			performWithKeepAlive(() -> {});
		}
	}

	private static void regenerateWorld(MinecraftServer server, long seed) {
		LevelPropertiesAccessor accessor = (LevelPropertiesAccessor) server.getSaveProperties();
		accessor.setGeneratorOptions(accessor.getGeneratorOptions().withSeed(OptionalLong.of(seed)));
		server.loadWorld();
		performWithKeepAlive(() -> {});
	}

	private static void postResetActions(MinecraftServer server) {
		unzipLobbyWorld();
		fountainPlayers.clear();

        for (ServerPlayerEntity serverPlayer : server.getPlayerManager().getPlayerList()) {
            updatePlayer(serverPlayer);
        }

        worldTimer = resetTimer;
		isCountdownRunning = true;
	}

	public static void updatePlayer(ServerPlayerEntity player) {
		if (player.isAlive()) {
			teleportToLobby(player);
		} else {
			respawnPlayer(player);
		}
	}

	private static void teleportToLobby(ServerPlayerEntity player) {
		ServerWorld lobbyWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, LOBBY_WORLD_ID));
		if (lobbyWorld == null) {
			LOGGER.warn("Lobby world not found, teleporting to overworld spawn");
			teleportToOverworldSpawn(player);
			return;
		}

		player.teleport(lobbyWorld, 0, 1024, 0, Set.of(), 0.0F, 0.0F, true);
		teleportToOverworldSpawn(player);
	}

	private static void teleportToOverworldSpawn(ServerPlayerEntity player) {
		var spawnPos = player.getWorldSpawnPos(server.getOverworld(),
						server.getOverworld().getSpawnPos())
				.toBottomCenterPos();
		player.teleport(server.getOverworld(),
				spawnPos.getX(),
				spawnPos.getY(),
				spawnPos.getZ(),
				Set.of(),
				0.0F,
				0.0F,
				false);
	}

	private static void respawnPlayer(ServerPlayerEntity player) {
		player.networkHandler.onClientStatus(
				new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
		taskExecutor.execute(() -> updatePlayer(player.networkHandler.player));
	}

	public static void unzipLobbyWorld() {
		try (ZipInputStream zis = new ZipInputStream(World.class.getResourceAsStream(LOBBY_WORLD_ZIP_PATH))) {
			byte[] buffer = new byte[1024];
			ZipEntry entry;

			while ((entry = zis.getNextEntry()) != null) {
				File targetFile = FabricLoader.getInstance()
						.getGameDir()
						.resolve("world")
						.resolve(entry.getName())
						.toFile();

				if (entry.isDirectory()) {
					targetFile.mkdirs();
				} else {
					ensureParentDirectoryExists(targetFile);
					writeZipEntryToFile(zis, buffer, targetFile);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to unzip lobby world", e);
		}
	}

	private static void ensureParentDirectoryExists(File file) {
		File parent = file.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
	}

	private static void writeZipEntryToFile(ZipInputStream zis, byte[] buffer, File targetFile) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(targetFile)) {
			int length;
			while ((length = zis.read(buffer)) > 0) {
				fos.write(buffer, 0, length);
			}
		}
	}

	private static void deleteRecursively(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child);
				}
			}
		}

		if (!file.delete()) {
			LOGGER.warn("Failed to delete file: {}", file.getAbsolutePath());
		}
	}
}