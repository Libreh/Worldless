package me.libreh.worldless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import me.libreh.worldless.WorldlessMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static ConfigManager INSTANCE;

    private ConfigManager() {}

    public static final int VERSION = 2;
    private static final String CONFIG_NAME = "worldless.json";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_NAME);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Config CONFIG;

    public boolean loadConfig() {
        Config oldConfig = CONFIG;
        boolean success;
        try {
            Config config;
            File configFile = CONFIG_PATH.toFile();

            if (configFile.exists()) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, Config.class);
                }
                migrateIfNeeded();
            } else {
                config = new Config();
            }
            config.version = VERSION;
            CONFIG = config;
            saveConfig();
            success = true;
        } catch(Throwable exception) {
            success = false;
            CONFIG = oldConfig;
            WorldlessMod.LOGGER.error("Error reading config!");
            exception.printStackTrace();
        }
        return success;
    }

    public void saveConfig() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
        } catch (Exception e) {
            WorldlessMod.LOGGER.error("Error saving config!");
            e.printStackTrace();
        }
    }

    private void migrateIfNeeded() throws IOException {
        try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
            var config = JsonParser.parseReader(reader);
            var configJson = config.getAsJsonObject();

            if (configJson.get("config_version").getAsInt() == 1) {
                var endTimerOn = configJson.get("end_timer_on").getAsString();
                boolean endFountainEnter = false;
                boolean dragonDeath = false;
                if (endTimerOn.equals("end_fountain")) {
                    endFountainEnter = true;
                } else {
                    dragonDeath = true;
                }
                CONFIG.stopTimerOn.endFountainEnter = endFountainEnter;
                CONFIG.stopTimerOn.dragonDeath = dragonDeath;
            }
        }
    }

    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigManager();
        }
        return INSTANCE;
    }

    public Config getConfig() {
        if (CONFIG == null) {
            CONFIG = Config.DEFAULT;
        }
        return CONFIG;
    }
}
