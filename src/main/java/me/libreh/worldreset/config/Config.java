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
        @SerializedName("player_state")
        public boolean playerState = false;

        @SerializedName("hunger")
        public boolean hunger = false;

        @SerializedName("statistics")
        public boolean statistics = false;

        @SerializedName("advancements")
        public boolean advancements = false;

        @SerializedName("progression")
        public boolean progression = false;

        @SerializedName("attributes")
        public boolean attributes = false;
    }

    public static class StopTimerOn {
        @SerializedName("end_fountain_enter")
        public boolean endFountainEnter = true;

        @SerializedName("dragon_death")
        public boolean dragonDeath = false;
    }
}