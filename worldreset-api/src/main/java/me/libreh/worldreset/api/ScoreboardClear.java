package me.libreh.worldreset.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ScoreboardClear {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreboardClear.class);

    private ScoreboardClear() {}

    public static void clearAll(MinecraftServer server) {
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
