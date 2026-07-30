package me.libreh.worldreset.world;

import eu.pb4.predicate.api.MinecraftPredicate;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.predicate.Trigger;
import me.libreh.worldreset.predicate.TriggerState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class TriggerTracker {
    private final MinecraftServer server;

    private final Map<MinecraftPredicate, TriggerState> states = new IdentityHashMap<>();

    public TriggerTracker(MinecraftServer server) {
        this.server = server;
    }

    public void reset() {
        states.clear();
    }

    public boolean notePortalUse(ServerPlayer player, Identifier blockId, ResourceKey<Level> originDimension) {
        return note(state -> state.notePortal(player, blockId, originDimension));
    }

    public boolean noteEntityDeath(Identifier entityId, Entity entity) {
        return note(state -> state.noteDeath(entityId, entity));
    }

    public boolean noteAdvancement(ServerPlayer player, Identifier advancementId) {
        return note(state -> state.noteAdvancement(player, advancementId));
    }

    public boolean shouldStop() {
        return anySatisfied(ConfigManager.config().stopTriggers);
    }

    public boolean shouldReset() {
        return anySatisfied(ConfigManager.config().resetTriggers);
    }

    private boolean note(Predicate<TriggerState> event) {
        Config cfg = ConfigManager.config();
        // Both lists must be visited every time (not short-circuited): an event can advance a
        // trigger in one list even when a trigger in the other already fired this round.
        boolean stopAdvanced = noteAll(cfg.stopTriggers, event);
        boolean resetAdvanced = noteAll(cfg.resetTriggers, event);
        return stopAdvanced | resetAdvanced;
    }

    private boolean noteAll(List<MinecraftPredicate> triggers, Predicate<TriggerState> event) {
        boolean any = false;
        for (MinecraftPredicate trigger : triggers) {
            TriggerState state = stateFor(trigger);
            if (state != null && event.test(state)) any = true;
        }
        return any;
    }

    private boolean anySatisfied(List<MinecraftPredicate> triggers) {
        int playerCount = server.getPlayerList().getPlayers().size();
        for (MinecraftPredicate trigger : triggers) {
            TriggerState state = states.get(trigger);
            if (state != null && state.isSatisfied(playerCount)) return true;
        }
        return false;
    }

    private @Nullable TriggerState stateFor(MinecraftPredicate predicate) {
        if (!(predicate instanceof Trigger trigger)) return null;
        return states.computeIfAbsent(predicate, k -> trigger.newState());
    }
}
