package me.libreh.worldreset.api;

public final class ResetFlags {
    public static final ThreadLocal<Boolean> skipCloseSave = ThreadLocal.withInitial(() -> false);

    public static volatile boolean skipChunkIoFlush = false;

    private ResetFlags() {}

    public static void setSkipCloseSave(boolean value) {
        skipCloseSave.set(value);
        skipChunkIoFlush = value;
    }
}
