package me.libreh.worldreset.world;

import org.jetbrains.annotations.Nullable;

public interface WorldResetHolder {
    @Nullable WorldManager worldreset$worldManager();

    void worldreset$setWorldManager(@Nullable WorldManager manager);
}
