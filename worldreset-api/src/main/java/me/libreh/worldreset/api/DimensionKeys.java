package me.libreh.worldreset.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Random;

public final class DimensionKeys {
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    private DimensionKeys() {}

    public static String randomSuffix() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    public static ResourceKey<Level> generate(String modId, String dimensionType) {
        return ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(modId, dimensionType + "_" + randomSuffix()));
    }
}