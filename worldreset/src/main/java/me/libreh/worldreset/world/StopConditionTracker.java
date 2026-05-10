package me.libreh.worldreset.world;

import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.config.ConfigManager;
import me.libreh.worldreset.config.StopCondition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StopConditionTracker {
    private static final int PORTAL_TELEPORT_WINDOW_TICKS = 5;

    private final MinecraftServer server;

    private final Map<UUID, PendingPortal> pendingPortalEntries = new HashMap<>();
    private final Map<Integer, Set<UUID>> portalEntered = new HashMap<>();
    private final Set<Integer> entityDeathTriggered = new HashSet<>();
    private final Map<Integer, Set<UUID>> advancementAwarded = new HashMap<>();

    public StopConditionTracker(MinecraftServer server) {
        this.server = server;
    }

    public void reset() {
        pendingPortalEntries.clear();
        portalEntered.clear();
        entityDeathTriggered.clear();
        advancementAwarded.clear();
    }

    public void notePortalTouch(ServerPlayer player, Identifier blockId) {
        pendingPortalEntries.put(player.getUUID(), new PendingPortal(blockId, server.getTickCount()));
    }

    public boolean confirmPortalTeleport(ServerPlayer player) {
        PendingPortal pending = pendingPortalEntries.remove(player.getUUID());
        if (pending == null) return false;
        if (server.getTickCount() - pending.tick > PORTAL_TELEPORT_WINDOW_TICKS) return false;

        boolean any = false;
        var conditions = ConfigManager.config().stopConditions;
        for (int i = 0; i < conditions.size(); i++) {
            StopCondition c = conditions.get(i);
            if (c instanceof StopCondition.PortalEnter pe && matchesId(pe.block, pending.blockId)) {
                portalEntered.computeIfAbsent(i, k -> new HashSet<>()).add(player.getUUID());
                any = true;
            }
        }
        return any;
    }

    public boolean noteEntityDeath(Identifier entityId) {
        boolean any = false;
        var conditions = ConfigManager.config().stopConditions;
        for (int i = 0; i < conditions.size(); i++) {
            StopCondition c = conditions.get(i);
            if (c instanceof StopCondition.EntityDeath ed && matchesId(ed.entity, entityId)) {
                entityDeathTriggered.add(i);
                any = true;
            }
        }
        return any;
    }

    public boolean noteAdvancement(ServerPlayer player, Identifier advancementId) {
        boolean any = false;
        var conditions = ConfigManager.config().stopConditions;
        for (int i = 0; i < conditions.size(); i++) {
            StopCondition c = conditions.get(i);
            if (c instanceof StopCondition.Advancement adv && matchesId(adv.advancement, advancementId)) {
                advancementAwarded.computeIfAbsent(i, k -> new HashSet<>()).add(player.getUUID());
                any = true;
            }
        }
        return any;
    }

    public boolean shouldStop() {
        var conditions = ConfigManager.config().stopConditions;
        int playerCount = server.getPlayerList().getPlayers().size();
        for (int i = 0; i < conditions.size(); i++) {
            StopCondition c = conditions.get(i);
            if (c instanceof StopCondition.PortalEnter pe) {
                Set<UUID> entered = portalEntered.get(i);
                if (entered == null) continue;
                if (pe.requireAllPlayers) {
                    if (playerCount > 0 && entered.size() >= playerCount) return true;
                } else if (!entered.isEmpty()) {
                    return true;
                }
            } else if (c instanceof StopCondition.EntityDeath) {
                if (entityDeathTriggered.contains(i)) return true;
            } else if (c instanceof StopCondition.Advancement adv) {
                Set<UUID> awarded = advancementAwarded.get(i);
                if (awarded == null) continue;
                if (adv.requireAllPlayers) {
                    if (playerCount > 0 && awarded.size() >= playerCount) return true;
                } else if (!awarded.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesId(String configured, Identifier actual) {
        if (configured == null || configured.isEmpty()) return false;
        Identifier parsed;
        try {
            parsed = Identifier.parse(configured);
        } catch (Exception e) {
            WorldReset.LOGGER.warn("Invalid identifier in stop_conditions: {}", configured);
            return false;
        }
        return Objects.equals(parsed, actual);
    }

    private record PendingPortal(Identifier blockId, int tick) {}
}
