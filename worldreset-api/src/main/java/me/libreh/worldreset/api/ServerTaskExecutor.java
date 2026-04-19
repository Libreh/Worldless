package me.libreh.worldreset.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class ServerTaskExecutor implements Executor {
    private final MinecraftServer server;

    public ServerTaskExecutor(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void execute(@NotNull Runnable runnable) {
        server.execute(new TickTask(server.getTickCount() - 3, runnable));
    }
}
