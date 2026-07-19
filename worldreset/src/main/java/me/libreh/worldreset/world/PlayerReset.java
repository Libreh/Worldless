package me.libreh.worldreset.world;

import me.libreh.worldreset.api.PlayerResetEvents;
import me.libreh.worldreset.config.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class PlayerReset {
    public static void applyConfiguredResets(Config.ResetOnLoad cfg, ServerPlayer player) {
        PlayerResetEvents.fireBeforePlayerReset(player);
        if (cfg.effects) me.libreh.worldreset.api.PlayerReset.resetEffects(player);
        if (cfg.health) me.libreh.worldreset.api.PlayerReset.resetHealth(player);
        if (cfg.hunger) me.libreh.worldreset.api.PlayerReset.resetHunger(player);
        if (cfg.statistics) me.libreh.worldreset.api.PlayerReset.resetStatistics(player);
        if (cfg.advancements) me.libreh.worldreset.api.PlayerReset.resetAdvancements(player);
        if (cfg.experience) me.libreh.worldreset.api.PlayerReset.resetExperience(player);
        if (cfg.inventory) me.libreh.worldreset.api.PlayerReset.resetInventory(player);
        if (cfg.recipes) me.libreh.worldreset.api.PlayerReset.resetRecipes(player);
        if (cfg.attributes) me.libreh.worldreset.api.PlayerReset.resetAttributes(player);
        if (!cfg.gamemode.equalsIgnoreCase("none")) {
            GameType type = GameType.byName(cfg.gamemode);
            me.libreh.worldreset.api.PlayerReset.resetGameMode(player, type);
        }
        PlayerResetEvents.fireAfterPlayerReset(player);
    }
}
