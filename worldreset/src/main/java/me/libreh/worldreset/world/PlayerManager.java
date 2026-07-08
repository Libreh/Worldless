package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class PlayerManager {
    private final MinecraftServer server;
    private final me.libreh.worldreset.api.ServerTaskExecutor taskExecutor;

    public PlayerManager(MinecraftServer server, me.libreh.worldreset.api.ServerTaskExecutor taskExecutor) {
        this.server = server;
        this.taskExecutor = taskExecutor;
    }

    public void preparePlayerForReset(ServerPlayer player) {
        if (!player.isAlive()) {
            respawnPlayer(player);
        }
        teleportToLobby(player);
    }

    public void teleportToLobby(ServerPlayer player) {
        ServerLevel lobbyWorld = server.getLevel(WorldReset.LOBBY_WORLD);
        if (lobbyWorld == null) {
            WorldReset.LOGGER.warn("Lobby world not found");
            return;
        }
        player.teleportTo(
                lobbyWorld,
                1, 65, 1,
                Set.of(),
                0.0F, 0.0F,
                true
        );
    }

    public void teleportToOverworldSpawn(ServerPlayer player) {
        var overworld = WorldReset.worlds().getGameOverworld();
        BlockPos worldSpawnPos = overworld.getRespawnData().pos();
        Vec3 spawnPos = Vec3.atBottomCenterOf(player.adjustSpawnLocation(overworld, worldSpawnPos));
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
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(overworld.dimension(), worldSpawnPos, 0.0F, 0.0F),
                        true
                ),
                false
        );
    }

    public void respawnPlayer(ServerPlayer player) {
        var connection = player.connection;
        connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)
        );
        taskExecutor.execute(() -> preparePlayerForReset(connection.player));
    }

    public ServerPlayer respawnInto(ServerPlayer player, ServerLevel level, BlockPos spawnPos) {
        player.setRespawnPosition(
                new ServerPlayer.RespawnConfig(
                        LevelData.RespawnData.of(level.dimension(), spawnPos, 0.0F, 0.0F),
                        true
                ),
                false
        );
        var connection = player.connection;
        connection.handleClientCommand(
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)
        );
        return connection.player;
    }

} 