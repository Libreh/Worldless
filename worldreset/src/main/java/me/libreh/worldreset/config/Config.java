package me.libreh.worldreset.config;

import com.google.gson.annotations.SerializedName;
import me.libreh.worldreset.WorldReset;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public static final Config DEFAULT = new Config();

    @SerializedName("config_version")
    public int version = ConfigManager.VERSION;

    @SerializedName("pool_size")
    public int poolSize = 1;

    @SerializedName("preload_distance")
    public String preloadDistance = "4";

    @SerializedName("countdown_sounds")
    public boolean countdownSounds = true;

    @SerializedName("restart_message")
    public boolean restartMessage = true;

    @SerializedName("stop_conditions")
    public List<StopCondition> stopConditions = defaultStopConditions();

    private static List<StopCondition> defaultStopConditions() {
        List<StopCondition> list = new ArrayList<>();
        StopCondition.PortalEnter end = new StopCondition.PortalEnter();
        end.block = "minecraft:end_portal";
        end.requireAllPlayers = true;
        list.add(end);
        return list;
    }

    @SerializedName("seed")
    public String seed = "random";

    @SerializedName("spawn_near")
    public SpawnNear spawnNear = new SpawnNear();

    @SerializedName("reset_on_load")
    public ResetOnLoad resetOnLoad = new ResetOnLoad();

    public static class SpawnNear {
        @SerializedName("type")
        public String type = "none";

        @SerializedName("target")
        public String target = "";

        @SerializedName("offset")
        public int offset = 0;

        @SerializedName("require_surface")
        public boolean requireSurface = false;
    }

    public static class ResetOnLoad {
        @SerializedName("effects")
        public boolean effects = false;

        @SerializedName("health")
        public boolean health = false;

        @SerializedName("hunger")
        public boolean hunger = false;

        @SerializedName("statistics")
        public boolean statistics = false;

        @SerializedName("advancements")
        public boolean advancements = false;

        @SerializedName("experience")
        public boolean experience = false;

        @SerializedName("inventory")
        public boolean inventory = false;

        @SerializedName("recipes")
        public boolean recipes = false;

        @SerializedName("attributes")
        public boolean attributes = false;

        @SerializedName("time_of_day")
        public int timeOfDay = -1;

        @SerializedName("clear_weather")
        public boolean clearWeather = false;
    }

    public float resolvePreloadDistance(MinecraftServer server) {
        String raw = preloadDistance;
        if (raw.endsWith("%")) {
            float pct;
            try {
                pct = Float.parseFloat(raw.substring(0, raw.length() - 1));
            } catch (NumberFormatException e) {
                WorldReset.LOGGER.warn("Invalid preload_distance '{}', falling back to 0", raw);
                return 0;
            }
            return Math.max(1, server.getPlayerList().getViewDistance() * pct / 100.0f);
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            WorldReset.LOGGER.warn("Invalid preload_distance '{}', falling back to 0", raw);
            return 0;
        }
    }

}
