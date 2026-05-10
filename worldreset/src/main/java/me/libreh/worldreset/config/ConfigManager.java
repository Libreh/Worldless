package me.libreh.worldreset.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.libreh.worldreset.WorldReset;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class ConfigManager {
    public static final int VERSION = 5;
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("worldreset.json");
    private static final Path OLD_CONFIG_PATH = CONFIG_DIR.resolve("worldless.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(StopCondition.class, new StopCondition.Adapter())
            .create();

    private static Config CONFIG;

    public static boolean load() {
        Config oldConfig = CONFIG;
        boolean success;
        try {
            Config config;
            File configFile = CONFIG_PATH.toFile();
            File oldConfigFile = OLD_CONFIG_PATH.toFile();

            if (configFile.exists()) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    config = GSON.fromJson(reader, Config.class);
                }
                CONFIG = config;
                migrate();
            } else if (oldConfigFile.exists()) {
                try (var reader = Files.newBufferedReader(OLD_CONFIG_PATH)) {
                    config = GSON.fromJson(reader, Config.class);
                }
                CONFIG = config;
                migrate();
                Files.delete(OLD_CONFIG_PATH);
                WorldReset.LOGGER.info("Migrated config from worldless.json to worldreset.json");
            } else {
                config = new Config();
            }
            config.version = VERSION;
            CONFIG = config;
            save();
            success = true;
        } catch(Throwable exception) {
            success = false;
            CONFIG = oldConfig;
            WorldReset.LOGGER.error("Error reading config!");
            exception.printStackTrace();
        }
        return success;
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
        } catch (Exception e) {
            WorldReset.LOGGER.error("Error saving config!");
            e.printStackTrace();
        }
    }

    private static void migrate() throws IOException {
        Path configPath = CONFIG_PATH.toFile().exists() ? CONFIG_PATH : OLD_CONFIG_PATH;
        try (var reader = Files.newBufferedReader(configPath)) {
            var config = JsonParser.parseReader(reader);
            var configJson = config.getAsJsonObject();

            int configVersion = configJson.get("config_version").getAsInt();

            boolean endFountainEnter = true;
            boolean dragonDeath = false;

            if (configVersion == 1) {
                var endTimerOn = configJson.get("end_timer_on").getAsString();
                if (endTimerOn.equals("end_fountain")) {
                    endFountainEnter = true;
                } else {
                    endFountainEnter = false;
                    dragonDeath = true;
                }
            } else if (configVersion >= 2 && configVersion <= 4 && configJson.has("stop_timer_on")) {
                JsonObject stopTimerOn = configJson.getAsJsonObject("stop_timer_on");
                if (stopTimerOn.has("end_fountain_enter")) {
                    endFountainEnter = stopTimerOn.get("end_fountain_enter").getAsBoolean();
                }
                if (stopTimerOn.has("dragon_death")) {
                    dragonDeath = stopTimerOn.get("dragon_death").getAsBoolean();
                }
            }

            if (configVersion < VERSION) {
                WorldReset.LOGGER.info("Migrating config from version {} to version {}", configVersion, VERSION);
                if (configVersion <= 4) {
                    CONFIG.stopConditions = new ArrayList<>();
                    if (endFountainEnter) {
                        StopCondition.PortalEnter pe = new StopCondition.PortalEnter();
                        pe.block = "minecraft:end_portal";
                        pe.requireAllPlayers = true;
                        CONFIG.stopConditions.add(pe);
                    }
                    if (dragonDeath) {
                        StopCondition.EntityDeath ed = new StopCondition.EntityDeath();
                        ed.entity = "minecraft:ender_dragon";
                        CONFIG.stopConditions.add(ed);
                    }
                }
            }
        }
    }

    public static Config config() {
        if (CONFIG == null) {
            CONFIG = Config.DEFAULT;
        }
        return CONFIG;
    }
}