package me.libreh.worldreset.world;

import me.libreh.worldreset.api.PlayerResetEvents;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.server.level.ServerPlayer;

public class PlayerReset {
    public static void applyConfiguredResets(ServerPlayer player) {
        PlayerResetEvents.fireBeforePlayerReset(player);
        Config.ResetOnLoad cfg = ConfigManager.config().resetOnLoad;
        if (cfg.effects) me.libreh.worldreset.api.PlayerReset.resetEffects(player);
        if (cfg.health) me.libreh.worldreset.api.PlayerReset.resetHealth(player);
        if (cfg.hunger) me.libreh.worldreset.api.PlayerReset.resetHunger(player);
        if (cfg.statistics) me.libreh.worldreset.api.PlayerReset.resetStatistics(player);
        if (cfg.advancements) me.libreh.worldreset.api.PlayerReset.resetAdvancements(player);
        if (cfg.experience) me.libreh.worldreset.api.PlayerReset.resetExperience(player);
        if (cfg.inventory) me.libreh.worldreset.api.PlayerReset.resetInventory(player);
        if (cfg.recipes) me.libreh.worldreset.api.PlayerReset.resetRecipes(player);
        if (cfg.attributes) me.libreh.worldreset.api.PlayerReset.resetAttributes(player);
        PlayerResetEvents.fireAfterPlayerReset(player);
    }
}
