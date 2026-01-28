package me.libreh.worldless.mixin.world;

import net.minecraft.util.thread.ThreadExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ThreadExecutor.class)
public interface ThreadExecutorAccessor {
    @Invoker("cancelTasks")
    void invokeDropAllTasks();
}
