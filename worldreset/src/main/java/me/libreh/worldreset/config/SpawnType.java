package me.libreh.worldreset.config;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public enum SpawnType {
    @SerializedName("none") NONE,
    @SerializedName("structure") STRUCTURE,
    @SerializedName("biome") BIOME;

    // Lowercase for logs and command feedback; JSON serialization is driven by @SerializedName, not this.
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
