package me.libreh.worldreset.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import me.libreh.worldreset.mixin.world.EnderDragonFightAccessor;
import me.libreh.worldreset.mixin.world.RaidsAccessor;
import me.libreh.worldreset.mixin.world.WitherBossAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

// Clears transient, cross-player state that would otherwise leak from the worlds being replaced
// into the new ones: boss bars (dragon fight, withers, raids) and scoreboard objectives.
public final class ResetCleanup {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResetCleanup.class);

    private ResetCleanup() {}

    public static void clear(MinecraftServer server, ServerLevel... levels) {
        clearBossEvents(levels);
        clearScoreboard(server);
    }

    private static void clearBossEvents(ServerLevel... levels) {
        for (ServerLevel level : levels) {
            if (level == null) continue;
            EnderDragonFight fight = level.getDragonFight();
            if (fight != null) ((EnderDragonFightAccessor) fight).worldreset$getDragonEvent().removeAllPlayers();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof WitherBoss wither) ((WitherBossAccessor) wither).worldreset$getBossEvent().removeAllPlayers();
            }
            // Raid.stop() releases the raid's boss event the same way, on top of marking it stopped.
            RaidsAccessor raidsAccessor = (RaidsAccessor) level.getRaids();
            Int2ObjectMap<Raid> raids = raidsAccessor.getRaidMap();
            for (Raid raid : List.copyOf(raids.values())) {
                raid.stop();
            }
        }
    }

    private static void clearScoreboard(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        for (Objective objective : List.copyOf(scoreboard.getObjectives())) {
            try {
                scoreboard.removeObjective(objective);
            } catch (Throwable t) {
                LOGGER.error("Error removing scoreboard objective {}", objective.getName(), t);
            }
        }
    }
}
