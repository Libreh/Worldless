package me.libreh.worldreset.mixin.c2me;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.thread.AbstractConsecutiveExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Mixin(AbstractConsecutiveExecutor.class)
public class ConsecutiveExecutorMixin {
    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("WorldReset/C2ME Compat");

    @WrapOperation(
        method = "registerForExecution",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Executor;execute(Ljava/lang/Runnable;)V")
    )
    private void worldreset$swallowRejectionDuringReset(Executor instance, Runnable command, Operation<Void> original) {
        try {
            original.call(instance, command);
        } catch (RejectedExecutionException e) {
            LOGGER.debug("Caught RejectedExecutionException during world reset, discarding task");
        }
    }
}
