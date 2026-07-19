package me.libreh.worldreset.world;

import org.jetbrains.annotations.Nullable;

// Duck interface on MinecraftServer: the manager is carried by the server, so it dies with the server
// instead of lingering in a static field (which pins the old server+worlds after quit-to-title).
public interface WorldResetHolder {
    @Nullable WorldManager worldreset$worldManager();

    void worldreset$setWorldManager(@Nullable WorldManager manager);
}
