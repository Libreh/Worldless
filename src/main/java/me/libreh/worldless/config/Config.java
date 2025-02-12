package me.libreh.worldless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.libreh.worldless.Worldless;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Config CONFIG;

    public static Config getConfig() {
        return CONFIG;
    }

    public boolean countdownSounds = true;
    public String endTimerOn = "END_FOUNTAIN";

    public static void loadConfig() {
        Config oldConfig = CONFIG;

        CONFIG = null;
        try {
            File configFile = getConfigFile();

            CONFIG = configFile.exists() ? GSON.fromJson(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8), Config.class) : new Config();

            saveConfig();
        } catch (IOException exception) {
            CONFIG = oldConfig;
        }
    }

    public static void saveConfig() {
        try {
            File configFile = getConfigFile();

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8));
            writer.write(GSON.toJson(CONFIG));
            writer.close();
        } catch (Exception exception) {
            Worldless.LOGGER.error("Something went wrong while saving config!", exception);
        }
    }

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), Worldless.MOD_ID + ".json");
    }
}