package me.libreh.worldreset.world;

import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.ConfigManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PlayerReset {
    public static void applyConfiguredResets(ServerPlayer player) {
        Config.ResetOnLoad cfg = ConfigManager.config().resetOnLoad;
        if (cfg.effects) resetEffects(player);
        if (cfg.health) resetHealth(player);
        if (cfg.hunger) resetHunger(player);
        if (cfg.statistics) resetStatistics(player);
        if (cfg.advancements) resetAdvancements(player);
        if (cfg.experience) resetExperience(player);
        if (cfg.inventory) resetInventory(player);
        if (cfg.recipes) resetRecipes(player);
        if (cfg.attributes) resetAttributes(player);
    }

    private static void resetEffects(ServerPlayer player) {
        player.removeAllEffects();
    }

    private static void resetHealth(ServerPlayer player) {
        player.setRemainingFireTicks(0);
        player.setSharedFlagOnFire(false);
        player.setAirSupply(player.getMaxAirSupply());
        player.setHealth(player.getMaxHealth());
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void resetHunger(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
    }

    private static void resetStatistics(ServerPlayer player) {
        for (StatType<?> statType : BuiltInRegistries.STAT_TYPE) {
            resetStatsForType(player, statType);
        }
    }

    private static <T> void resetStatsForType(ServerPlayer player, StatType<T> statType) {
        var registry = statType.getRegistry();
        for (Identifier id : registry.keySet()) {
            Optional<? extends Holder.Reference<T>> entry = registry.get(id);
            if (entry.isPresent()) {
                player.resetStat(statType.get(entry.get().value()));
            }
        }
    }

    private static void resetAdvancements(ServerPlayer player) {
        var advancements = player.level().getServer().getAdvancements().getAllAdvancements();
        for (var advancement : advancements) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criteria : progress.getCompletedCriteria()) {
                player.getAdvancements().revoke(advancement, criteria);
            }
        }
    }

    private static void resetExperience(ServerPlayer player) {
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setScore(0);
    }

    private static void resetInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    private static void resetRecipes(ServerPlayer player) {
        player.resetRecipes(player.level().getServer().getRecipeManager().getRecipes());
    }

    private static void resetAttributes(ServerPlayer player) {
        for (AttributeInstance attr : player.getAttributes().getAttributesToSync()) {
            player.getAttributes().resetBaseValue(attr.getAttribute());
        }
    }
}