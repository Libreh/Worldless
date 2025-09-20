package me.libreh.worldless.util;

import net.minecraft.util.math.random.RandomSeed;

public final class SeedUtils {
    private SeedUtils() {}

    public static long parseSeed(String seedString) {
        if ("random".equals(seedString)) {
            return RandomSeed.getSeed();
        }

        try {
            return Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid seed: '" + seedString + "'. Must be 'random' or a valid long value");
        }
    }
}