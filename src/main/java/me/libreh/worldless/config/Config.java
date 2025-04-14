package me.libreh.worldless.config;

import com.google.gson.annotations.SerializedName;

public class Config {
    public static final Config DEFAULT = new Config();
    public String _comment = "Before changing anything, see https://github.com/Libreh/Worldless#configuration";

    @SerializedName("config_version")
    public int version = ConfigManager.VERSION;

    @SerializedName("countdown_sounds")
    public boolean countdownSounds = true;
    @SerializedName("end_timer_on")
    public String endTimerOn = "end_fountain";
}