package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class PlayerManager {
    private final MinecraftServer server;
    private final ServerTaskExecutor taskExecutor;

    public PlayerManager(MinecraftServer server, ServerTaskExecutor taskExecutor) {
        this.server = server;
        this.taskExecutor = taskExecutor;
    }

    public void updatePlayer(ServerPlayer player) {
        if (player.isAlive()) {
            teleportToLobby(player);
            teleportToOverworldSpawn(player);
        } else {
            respawnPlayer(player);
        }
    }

    public void teleportToLobby(ServerPlayer player) {
        ServerLevel lobbyWorld = server.getLevel(ResourceKey.create(Registries.DIMENSION, WorldReset.LOBBY_WORLD_ID));
        if (lobbyWorld == null) {
            WorldReset.LOGGER.warn("Lobby world not found");
            return;
        }
        player.teleportTo(
                lobbyWorld,
                0, 1024, 0,
                Set.of(),
                0.0F, 0.0F,
                true
        );
    }

    public void teleportToOverworldSpawn(ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        BlockPos worldSpawnPos = overworld.getRespawnData().pos();
        Vec3 spawnPos = player.adjustSpawnLocation(overworld, worldSpawnPos).getBottomCenter();
        player.teleportTo(
                overworld,
                spawnPos.x(),
                spawnPos.y(),
                spawnPos.z(),
                Set.of(),
                overworld.getRespawnData().pitch(),
                overworld.getRespawnData().yaw(),
                true
        );
    }

    public void respawnPlayer(ServerPlayer player) {
        var connection = player.connection;
        connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)
        );
        taskExecutor.execute(() -> updatePlayer(connection.player));
    }

    public boolean shouldStop(ServerPlayer player) {
        if (!ConfigManager.config().stopTimerOn.endFountainEnter) return false;
        int fountainPlayersCount = WorldReset.worlds().fountainPlayers.size();
        int playerCount = player.level().getServer().getPlayerList().getPlayers().size();
        return fountainPlayersCount == playerCount;
    }
} 