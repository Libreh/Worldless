package me.libreh.worldless.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * Executor for running tasks on the Minecraft server thread.
 */
public class ServerTaskExecutor implements Executor {
    private final MinecraftServer server;

    public ServerTaskExecutor(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Executes a runnable on the server thread.
     * @param runnable the task to execute
     */
    @Override
    public void execute(@NotNull Runnable runnable) {
        server.send(new ServerTask(server.getTicks() - 3, runnable));
    }
}
