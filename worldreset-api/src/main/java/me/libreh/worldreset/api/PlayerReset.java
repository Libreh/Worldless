package me.libreh.worldreset.api;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class PlayerReset {
    public static void resetAll(ServerPlayer player) {
        resetEffects(player);
        resetHealth(player);
        resetHunger(player);
        resetStatistics(player);
        resetAdvancements(player);
        resetExperience(player);
        resetInventory(player);
        resetRecipes(player);
        resetAttributes(player);
    }

    public static void resetEffects(ServerPlayer player) {
        player.removeAllEffects();
    }

    public static void resetHealth(ServerPlayer player) {
        player.setRemainingFireTicks(0);
        player.setSharedFlagOnFire(false);
        player.setAirSupply(player.getMaxAirSupply());
        player.setHealth(player.getMaxHealth());
        player.setDeltaMovement(Vec3.ZERO);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    public static void resetHunger(ServerPlayer player) {
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
    }

    public static void resetStatistics(ServerPlayer player) {
        for (StatType<?> statType : BuiltInRegistries.STAT_TYPE) {
            resetStatsForType(player, statType);
        }
    }

    public static void resetAdvancements(ServerPlayer player) {
        var advancements = player.level().getServer().getAdvancements().getAllAdvancements();
        for (var advancement : advancements) {
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            for (String criteria : progress.getCompletedCriteria()) {
                player.getAdvancements().revoke(advancement, criteria);
            }
        }
    }

    public static void resetExperience(ServerPlayer player) {
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setScore(0);
    }

    public static void resetInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    public static void resetRecipes(ServerPlayer player) {
        player.resetRecipes(player.level().getServer().getRecipeManager().getRecipes());
    }

    public static void resetAttributes(ServerPlayer player) {
        for (AttributeInstance attr : player.getAttributes().getAttributesToSync()) {
            player.getAttributes().resetBaseValue(attr.getAttribute());
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
}
