package me.libreh.worldreset.world;

import eu.pb4.predicate.api.MinecraftPredicate;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.Config;
import me.libreh.worldreset.predicate.Trigger;
import me.libreh.worldreset.predicate.TriggerState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TriggerTracker {
    private static final int PORTAL_TELEPORT_WINDOW_TICKS = 5;

    private final MinecraftServer server;
    private final Supplier<Config> config;

    private final Map<UUID, PendingPortal> pendingPortalEntries = new HashMap<>();
    private final Map<MinecraftPredicate, TriggerState> states = new IdentityHashMap<>();

    public TriggerTracker(MinecraftServer server, Supplier<Config> config) {
        this.server = server;
        this.config = config;
    }

    public void reset() {
        pendingPortalEntries.clear();
        states.clear();
    }

    public void notePortalTouch(ServerPlayer player, Identifier blockId) {
        ResourceKey<Level> dim = player.level() instanceof ServerLevel sl
            ? WorldReset.worlds(server).toVanillaDimension(sl)
            : player.level().dimension();
        pendingPortalEntries.put(player.getUUID(), new PendingPortal(blockId, dim, server.getTickCount()));
    }

    public boolean confirmPortalTeleport(ServerPlayer player) {
        PendingPortal pending = pendingPortalEntries.remove(player.getUUID());
        if (pending == null) return false;
        if (server.getTickCount() - pending.tick > PORTAL_TELEPORT_WINDOW_TICKS) return false;
        return note(state -> state.notePortal(player, pending.blockId, pending.originDimension));
    }

    public boolean noteEntityDeath(Identifier entityId, Entity entity) {
        return note(state -> state.noteDeath(entityId, entity));
    }

    public boolean noteAdvancement(ServerPlayer player, Identifier advancementId) {
        return note(state -> state.noteAdvancement(player, advancementId));
    }

    public boolean shouldStop() {
        return anySatisfied(config.get().stopTriggers);
    }

    public boolean shouldReset() {
        return anySatisfied(config.get().resetTriggers);
    }

    private boolean note(Predicate<TriggerState> event) {
        boolean any = false;
        for (MinecraftPredicate trigger : allTriggers()) {
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

    private List<MinecraftPredicate> allTriggers() {
        Config cfg = config.get();
        List<MinecraftPredicate> all = new ArrayList<>(cfg.stopTriggers);
        all.addAll(cfg.resetTriggers);
        return all;
    }

    private record PendingPortal(Identifier blockId, ResourceKey<Level> originDimension, int tick) {}
}
