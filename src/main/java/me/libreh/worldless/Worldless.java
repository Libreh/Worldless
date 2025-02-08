package me.libreh.worldless;

import me.libreh.worldless.command.Commands;
import me.libreh.worldless.config.Config;
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
import java.util.OptionalLong;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Worldless implements ModInitializer {
	public static final String MOD_ID = "worldvanisher";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static MinecraftServer SERVER;
	public static boolean shouldCancelSaving;
	public static boolean startTimer;
	public static int tickCount;
	public static int worldTimer;
	public static int resetTimer;
	public static ServerTaskExecutor taskExecutor;

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			Commands.worldlessCommand(dispatcher);
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			SERVER = server;
			Config.load();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> unzipLobbyWorld());

		ServerTickEvents.START_SERVER_TICK.register(server -> {
			if (startTimer) {
				tickCount++;
				if (tickCount == 19) {
					worldTimer -= 20;

					if (worldTimer <= 0) {
						startTimer = false;
						resetWorlds(RandomSeed.getSeed());
					} else {
						String minutesString;
						int minutes = (int) Math.floor((double) worldTimer % (20 * 60 * 60) / (20 * 60));
						if (minutes <= 9) {
							minutesString = "0" + minutes;
						} else {
							minutesString = String.valueOf(minutes);
						}
						String secondsString;
						int seconds = (int) Math.floor((double) worldTimer % (20 * 60) / (20));
						if (seconds <= 9) {
							secondsString = "0" + seconds;
						} else {
							secondsString = String.valueOf(seconds);
						}

						Formatting formatting = Formatting.GRAY;
						if (minutes == 0) {
							if (seconds > 10) {
								formatting = Formatting.RED;
							} else {
								formatting = Formatting.DARK_RED;
							}
						}

						for (ServerPlayerEntity player : SERVER.getPlayerManager().getPlayerList()) {
							player.sendMessage(Text.literal(minutesString + ":" + secondsString).formatted(formatting), true);
							if (Config.getConfig().countdownSounds) {
								if (minutes == 0 && seconds <= 10) {
									var pitch = switch (seconds) {
										case 5, 6, 7, 8, 9, 10 -> 1.03F;
										case 4 -> 1.2F;
										case 3 -> 1.4F;
										case 2 -> 1.6F;
										case 1 -> 1.8F;
										default -> 2.0F;
									};
									player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.RECORDS, 1.0F, pitch);
								}
							}
						}
					}
					tickCount = 0;
				}
			}
		});
	}

	private static void tickKeepAlive(MinecraftServer server) {
		if (server.getNetworkIo() != null) {
			server.getNetworkIo().tick();
		}
	}

	public static void resetWorlds(long seed) {
		tickKeepAlive(SERVER);

		SERVER.saving = true;
		try {
			SERVER.getPlayerManager().saveAllPlayerData();
			for (ServerWorld world : SERVER.getWorlds()) {
				world.getPersistentStateManager().save();
			}

			tickKeepAlive(SERVER);
			SERVER.cancelTasks();
			shouldCancelSaving = true;

			for (World world : SERVER.getWorlds()) {
				world.close();
				tickKeepAlive(SERVER);
				String[] directories = {"region", "poi", "entities"};
				for (String dir : directories) {
					File file = SERVER.session.getWorldDirectory(world.getRegistryKey()).resolve(dir).toFile();
					if (file.exists()) {
						deleteRecursively(file);
					}
				}
				tickKeepAlive(SERVER);
			}

			LevelPropertiesAccessor levelPropertiesAccessor = (LevelPropertiesAccessor) SERVER.getSaveProperties();
			levelPropertiesAccessor.setGeneratorOptions(levelPropertiesAccessor.getGeneratorOptions().withSeed(OptionalLong.of(seed)));
			SERVER.loadWorld();
			tickKeepAlive(SERVER);
		} catch (IOException e) {
			LOGGER.info("Failed to reset", e);
		} finally {
			SERVER.saving = false;
			shouldCancelSaving = false;
		}

		unzipLobbyWorld();

		for (ServerPlayerEntity player : SERVER.getPlayerManager().getPlayerList()) {
			updatePlayer(player);
		}
		worldTimer = resetTimer;
		startTimer = true;
	}

	public static void updatePlayer(ServerPlayerEntity player) {
		if (!player.isAlive()) {
			var newPlayer = player;
			var networkHandler = newPlayer.networkHandler;
			networkHandler.onClientStatus(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN));
			newPlayer = networkHandler.player;

			if (taskExecutor == null) {
				taskExecutor = new ServerTaskExecutor(SERVER);
			}

			ServerPlayerEntity finalNewPlayer = newPlayer;
			taskExecutor.execute(() -> updatePlayer(finalNewPlayer));
			return;
		}
		player.teleport(SERVER.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of("worldless", "lobby"))), 0, 1024, 0, Set.of(), 0.0F, 0.0F, true);
		var spawnPos = player.getWorldSpawnPos(SERVER.getOverworld(), SERVER.getOverworld().getSpawnPos()).toBottomCenterPos();
		player.teleport(SERVER.getOverworld(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), Set.of(), 0.0F, 0.0F, false);
	}

	public static void unzipLobbyWorld() {
		try (ZipInputStream zis = new ZipInputStream(World.class.getResourceAsStream("/worldless/lobby_world.zip"))) {
			ZipEntry entry;
			byte[] buffer = new byte[1024];
			while ((entry = zis.getNextEntry()) != null) {
				File newFile = new File(FabricLoader.getInstance().getGameDir().resolve("world") + File.separator + entry.getName());
				if (entry.isDirectory()) {
					newFile.mkdirs();
				} else {
					new File(newFile.getParent()).mkdirs();
					try (FileOutputStream fos = new FileOutputStream(newFile)) {
						int length;
						while ((length = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, length);
						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.info("Failed to unzip world", e);
		}
	}

	private static void deleteRecursively(File file) {
		if (file.isDirectory()) {
			for (File subFile : file.listFiles()) {
				deleteRecursively(subFile);
			}
		}

		file.delete();
	}
}