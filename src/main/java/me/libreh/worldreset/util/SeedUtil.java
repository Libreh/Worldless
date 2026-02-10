package me.libreh.worldreset.util;

import net.minecraft.world.level.levelgen.RandomSupport;

public final class SeedUtil {
    public static long parseSeed(String seedString) {
        if ("random".equals(seedString)) {
            return RandomSupport.generateUniqueSeed();
        }

        try {
            return Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid seed: '" + seedString + "'. Must be 'random' or a valid long value");
        }
    }
}