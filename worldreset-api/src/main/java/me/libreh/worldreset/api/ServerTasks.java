package me.libreh.worldreset.api;

import me.libreh.worldreset.mixin.world.MinecraftServerPollTaskAccessor;
import net.minecraft.server.MinecraftServer;

public final class ServerTasks {
    private ServerTasks() {}

    public static void drainQueue(MinecraftServer server) {
        while (((MinecraftServerPollTaskAccessor) server).worldreset$invokePollTask()) {}
    }
}
