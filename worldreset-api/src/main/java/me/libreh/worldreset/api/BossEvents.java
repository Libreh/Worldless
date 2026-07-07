package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.EnderDragonFightAccessor;
import me.libreh.worldreset.mixin.world.WitherBossAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.dimension.end.EnderDragonFight;

public final class BossEvents {
    private BossEvents() {}

    public static void clearForLevels(ServerLevel... levels) {
        for (ServerLevel level : levels) {
            if (level == null) continue;
            EnderDragonFight fight = level.getDragonFight();
            if (fight != null) ((EnderDragonFightAccessor) fight).worldreset$getDragonEvent().removeAllPlayers();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof WitherBoss wither) ((WitherBossAccessor) wither).worldreset$getBossEvent().removeAllPlayers();
            }
        }
    }
}
