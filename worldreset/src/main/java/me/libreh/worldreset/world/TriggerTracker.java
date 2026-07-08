package me.libreh.worldreset.world;

import eu.pb4.predicate.api.MinecraftPredicate;
import eu.pb4.predicate.api.PredicateContext;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.predicate.AdvancementPredicate;
import me.libreh.worldreset.predicate.EntityDeathPredicate;
import me.libreh.worldreset.predicate.PortalEnterPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import me.libreh.worldreset.WorldReset;

import java.util.*;

public class TriggerTracker {
    private static final int PORTAL_TELEPORT_WINDOW_TICKS = 5;

    private final MinecraftServer server;

    private final Map<UUID, PendingPortal> pendingPortalEntries = new HashMap<>();
    private final TriggerListState stopState = new TriggerListState();
    private final TriggerListState resetState = new TriggerListState();

    public TriggerTracker(MinecraftServer server) {
        this.server = server;
    }

    public void reset() {
        pendingPortalEntries.clear();
        stopState.clear();
        resetState.clear();
    }

    public void notePortalTouch(ServerPlayer player, Identifier blockId) {
        ResourceKey<Level> dim = player.level() instanceof ServerLevel sl
            ? WorldReset.worlds().toVanillaDimension(sl)
            : player.level().dimension();
        pendingPortalEntries.put(player.getUUID(), new PendingPortal(blockId, dim, server.getTickCount()));
    }

    public boolean confirmPortalTeleport(ServerPlayer player) {
        PendingPortal pending = pendingPortalEntries.remove(player.getUUID());
        if (pending == null) return false;
        if (server.getTickCount() - pending.tick > PORTAL_TELEPORT_WINDOW_TICKS) return false;

        boolean any = false;
        any |= checkPortal(stopState, ConfigManager.config().stopTriggers, player, pending.blockId, pending.originDimension);
        any |= checkPortal(resetState, ConfigManager.config().resetTriggers, player, pending.blockId, pending.originDimension);
        return any;
    }

    public boolean noteEntityDeath(Identifier entityId, Entity entity) {
        boolean any = false;
        any |= checkEntityDeath(stopState, ConfigManager.config().stopTriggers, entityId, entity);
        any |= checkEntityDeath(resetState, ConfigManager.config().resetTriggers, entityId, entity);
        return any;
    }

    public boolean noteAdvancement(ServerPlayer player, Identifier advancementId) {
        boolean any = false;
        any |= checkAdvancement(stopState, ConfigManager.config().stopTriggers, player, advancementId);
        any |= checkAdvancement(resetState, ConfigManager.config().resetTriggers, player, advancementId);
        return any;
    }

    public boolean shouldStop() {
        return stopState.shouldTrigger(ConfigManager.config().stopTriggers, server.getPlayerList().getPlayers().size());
    }

    public boolean shouldReset() {
        return resetState.shouldTrigger(ConfigManager.config().resetTriggers, server.getPlayerList().getPlayers().size());
    }

    private boolean checkPortal(TriggerListState state, List<MinecraftPredicate> triggers, ServerPlayer player, Identifier blockId, ResourceKey<Level> originDimension) {
        boolean any = false;
        for (int i = 0; i < triggers.size(); i++) {
            MinecraftPredicate c = triggers.get(i);
            if (c instanceof PortalEnterPredicate pe && pe.block().equals(blockId) && pe.matchesDimension(originDimension)) {
                if (pe.test(PredicateContext.of(player)).success()) {
                    state.portalEntered.computeIfAbsent(i, k -> new HashSet<>()).add(player.getUUID());
                    any = true;
                }
            }
        }
        return any;
    }

    private boolean checkEntityDeath(TriggerListState state, List<MinecraftPredicate> triggers, Identifier entityId, Entity entity) {
        boolean any = false;
        for (int i = 0; i < triggers.size(); i++) {
            MinecraftPredicate c = triggers.get(i);
            if (c instanceof EntityDeathPredicate ed && ed.entity().equals(entityId)) {
                if (ed.test(PredicateContext.of(entity)).success()) {
                    state.entityDeathTriggered.add(i);
                    any = true;
                }
            }
        }
        return any;
    }

    private boolean checkAdvancement(TriggerListState state, List<MinecraftPredicate> triggers, ServerPlayer player, Identifier advancementId) {
        boolean any = false;
        for (int i = 0; i < triggers.size(); i++) {
            MinecraftPredicate c = triggers.get(i);
            if (c instanceof AdvancementPredicate adv && adv.advancement().equals(advancementId)) {
                if (adv.test(PredicateContext.of(player)).success()) {
                    state.advancementAwarded.computeIfAbsent(i, k -> new HashSet<>()).add(player.getUUID());
                    any = true;
                }
            }
        }
        return any;
    }

    private static class TriggerListState {
        final Map<Integer, Set<UUID>> portalEntered = new HashMap<>();
        final Set<Integer> entityDeathTriggered = new HashSet<>();
        final Map<Integer, Set<UUID>> advancementAwarded = new HashMap<>();

        void clear() {
            portalEntered.clear();
            entityDeathTriggered.clear();
            advancementAwarded.clear();
        }

        boolean shouldTrigger(List<MinecraftPredicate> triggers, int playerCount) {
            for (int i = 0; i < triggers.size(); i++) {
                MinecraftPredicate c = triggers.get(i);
                if (c instanceof PortalEnterPredicate pe) {
                    Set<UUID> entered = portalEntered.get(i);
                    if (entered == null) continue;
                    if (pe.requireAllPlayers()) {
                        if (playerCount > 0 && entered.size() >= playerCount) return true;
                    } else if (!entered.isEmpty()) {
                        return true;
                    }
                } else if (c instanceof EntityDeathPredicate) {
                    if (entityDeathTriggered.contains(i)) return true;
                } else if (c instanceof AdvancementPredicate adv) {
                    Set<UUID> awarded = advancementAwarded.get(i);
                    if (awarded == null) continue;
                    if (adv.requireAllPlayers()) {
                        if (playerCount > 0 && awarded.size() >= playerCount) return true;
                    } else if (!awarded.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private record PendingPortal(Identifier blockId, ResourceKey<Level> originDimension, int tick) {}
}
