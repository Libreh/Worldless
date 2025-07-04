package me.libreh.worldless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import me.libreh.worldless.Worldless;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static ConfigManager INSTANCE;

    private ConfigManager() {}

    public static final int VERSION = 2;
    private final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("worldless.json");
    private final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Config CONFIG;

    public boolean loadConfig() {
        Config oldConfig = CONFIG;
        boolean success;
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    CONFIG = GSON.fromJson(reader, Config.class);
                }
                migrateIfNeeded();
            } else {
                CONFIG = new Config();
            }
            CONFIG.version = VERSION;
            saveConfig();
            success = true;
        } catch(Throwable exception) {
            success = false;
            CONFIG = oldConfig;
            Worldless.LOGGER.error("Something went wrong while reading config!");
            exception.printStackTrace();
        }
        return success;
    }

    public void saveConfig() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
        } catch (Exception e) {
            Worldless.LOGGER.error("Something went wrong while saving config!");
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
                CONFIG.timerStop.endFountainEnter = endFountainEnter;
                CONFIG.timerStop.dragonDeath = dragonDeath;
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
