package me.libreh.worldreset.api;

import net.casual.arcade.dimensions.level.CustomLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public record PooledWorlds<T>(
    CustomLevel overworld,
    CustomLevel nether,
    CustomLevel end,
    long seed,
    @Nullable BlockPos spawn,
    @Nullable T data
) {}
