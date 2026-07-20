package me.libreh.worldreset.predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

// Per-cycle accumulator for one Trigger. Each note* returns true when the event advanced this
// trigger (which gates re-evaluation); a trigger ignores the event types it does not care about.
public interface TriggerState {
    default boolean notePortal(ServerPlayer player, Identifier block, ResourceKey<Level> originDimension) {
        return false;
    }

    default boolean noteDeath(Identifier entityId, Entity entity) {
        return false;
    }

    default boolean noteAdvancement(ServerPlayer player, Identifier advancementId) {
        return false;
    }

    boolean isSatisfied(int playerCount);
}
