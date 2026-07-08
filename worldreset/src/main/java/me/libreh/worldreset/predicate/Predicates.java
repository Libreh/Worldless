package me.libreh.worldreset.predicate;

import eu.pb4.predicate.api.PredicateRegistry;
import me.libreh.worldreset.WorldReset;

public final class Predicates {
    private Predicates() {}

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;
        PredicateRegistry.register(PortalEnterPredicate.ID, PortalEnterPredicate.CODEC);
        PredicateRegistry.register(EntityDeathPredicate.ID, EntityDeathPredicate.CODEC);
        PredicateRegistry.register(AdvancementPredicate.ID, AdvancementPredicate.CODEC);
        WorldReset.LOGGER.debug("Registered WorldReset predicates");
    }
}
