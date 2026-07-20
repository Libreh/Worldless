package me.libreh.worldreset.predicate;

// A config predicate that can drive world resets. Each trigger produces its own per-cycle tracking
// state, so TriggerTracker dispatches events without branching on the concrete predicate type.
public interface Trigger {
    TriggerState newState();

    String describe();
}
