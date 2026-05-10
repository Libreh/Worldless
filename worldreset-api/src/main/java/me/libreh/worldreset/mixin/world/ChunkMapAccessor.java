package me.libreh.worldreset.mixin.world;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @SuppressWarnings("rawtypes")
    @Accessor("entityMap")
    Int2ObjectMap worldreset$getEntityMap();

    @Invoker("removeEntity")
    void worldreset$invokeRemoveEntity(Entity entity);

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder worldreset$invokeGetVisibleChunkIfPresent(long pos);
}
