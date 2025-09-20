package me.libreh.worldless.world;

import me.libreh.worldless.WorldlessMod;
import me.libreh.worldless.config.ConfigManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;

public class PlayerManager {
    private final MinecraftServer server;
    private final ServerTaskExecutor taskExecutor;

    public PlayerManager(MinecraftServer server, ServerTaskExecutor taskExecutor) {
        this.server = server;
        this.taskExecutor = taskExecutor;
    }

    public void updatePlayer(ServerPlayerEntity player) {
        if (player.isAlive()) {
            teleportToLobby(player);
            teleportToOverworldSpawn(player);
        } else {
            respawnPlayer(player);
        }
    }

    public void teleportToLobby(ServerPlayerEntity player) {
        ServerWorld lobbyWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, WorldlessMod.LOBBY_WORLD_ID));
        if (lobbyWorld == null) {
            WorldlessMod.LOGGER.warn("Lobby world not found, using overworld spawn");
            teleportToOverworldSpawn(player);
            return;
        }
        player.teleport(
                lobbyWorld,
                0, 1024, 0,
                Set.of(),
                0.0F, 0.0F,
                true
        );
    }

    public void teleportToOverworldSpawn(ServerPlayerEntity player) {
        ServerWorld overworld = server.getOverworld();
        var spawnPos = overworld.getSpawnPos().toBottomCenterPos();
        player.teleport(
                overworld,
                spawnPos.getX(),
                spawnPos.getY(),
                spawnPos.getZ(),
                Set.of(),
                0.0F,
                0.0F,
                false
        );
    }

    public void respawnPlayer(ServerPlayerEntity player) {
        player.networkHandler.onClientStatus(
                new net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket(net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode.PERFORM_RESPAWN)
        );
        taskExecutor.execute(() -> updatePlayer(player));
    }

    public boolean shouldStop(ServerPlayerEntity player) {
        if (!ConfigManager.getInstance().getConfig().stopTimerOn.endFountainEnter) return false;
        int fountainPlayersCount = WorldlessMod.getWorldManager().fountainPlayers.size();
        int playerCount = player.getServer().getPlayerManager().getPlayerList().size();
        return fountainPlayersCount == playerCount;
    }
} 