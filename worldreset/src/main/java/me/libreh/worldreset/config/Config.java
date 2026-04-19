package me.libreh.worldreset.config;

import com.google.gson.annotations.SerializedName;

public class Config {
    public static final Config DEFAULT = new Config();

    @SerializedName("config_version")
    public int version = ConfigManager.VERSION;

    @SerializedName("countdown_sounds")
    public boolean countdownSounds = true;

    @SerializedName("restart_message")
    public boolean restartMessage = true;

    @SerializedName("stop_timer_on")
    public StopTimerOn stopTimerOn = new StopTimerOn();

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
        public boolean effects = true;

        @SerializedName("health")
        public boolean health = true;

        @SerializedName("hunger")
        public boolean hunger = true;

        @SerializedName("statistics")
        public boolean statistics = true;

        @SerializedName("advancements")
        public boolean advancements = true;

        @SerializedName("experience")
        public boolean experience = true;

        @SerializedName("inventory")
        public boolean inventory = true;

        @SerializedName("recipes")
        public boolean recipes = true;

        @SerializedName("attributes")
        public boolean attributes = true;

        @SerializedName("time_of_day")
        public int timeOfDay = 1000;

        @SerializedName("clear_weather")
        public boolean clearWeather = true;
    }

    public static class StopTimerOn {
        @SerializedName("end_fountain_enter")
        public boolean endFountainEnter = true;

        @SerializedName("dragon_death")
        public boolean dragonDeath = false;
    }
}
