package me.libreh.worldless.config;

import com.google.gson.annotations.SerializedName;

public class Config {
    public static final Config DEFAULT = new Config();

    @SerializedName("config_version")
    public int version = ConfigManager.VERSION;

    @SerializedName("countdown_sounds")
    public boolean countdownSounds = true;

    @SerializedName("timer_stop_on")
    public TimerStopOn timerStop = new TimerStopOn();

    public static class TimerStopOn {
        @SerializedName("end_fountain_enter")
        public boolean endFountainEnter = true;

        @SerializedName("dragon_death")
        public boolean dragonDeath = false;
    }
}