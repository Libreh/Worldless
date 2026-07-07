package me.libreh.worldreset.api;

public final class ResetFlags {
    public static final ThreadLocal<Boolean> skipCloseSave = ThreadLocal.withInitial(() -> false);

    private ResetFlags() {}
}
