package me.libreh.worldreset.world;

import org.jetbrains.annotations.Nullable;

public final class ResetScheduler {
    public record Request(String seed, boolean fromCountdown) {}

    private @Nullable Request pending;

    public boolean hasPending() {
        return pending != null;
    }

    public void enqueue(String seed) {
        boolean fromCountdown = pending != null && pending.fromCountdown();
        pending = new Request(seed, fromCountdown);
    }

    public void enqueueFromCountdown() {
        pending = new Request("", true);
    }

    public void clear() {
        pending = null;
    }

    public @Nullable Request take() {
        Request taken = pending;
        pending = null;
        return taken;
    }
}
