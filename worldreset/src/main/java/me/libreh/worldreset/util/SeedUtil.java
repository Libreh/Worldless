package me.libreh.worldreset.util;

import net.minecraft.world.level.levelgen.RandomSupport;

public final class SeedUtil {
    public static long parseSeed(String seedString) {
        if ("random".equals(seedString)) {
            return RandomSupport.generateUniqueSeed();
        }

        if (seedString.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseLong(seedString);
        } catch (NumberFormatException e) {
            return seedString.hashCode();
        }
    }
}