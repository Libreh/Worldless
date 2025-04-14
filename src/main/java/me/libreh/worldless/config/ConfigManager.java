package me.libreh.worldless.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.libreh.worldless.Worldless;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static me.libreh.worldless.Worldless.MOD_ID;

public class ConfigManager {
    public static int VERSION = 1;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Config CONFIG;

    public static Config getConfig() {
        if (CONFIG == null) {
            return Config.DEFAULT;
        }
        return CONFIG;
    }

    public static boolean loadConfig() {
        boolean ENABLED;

        try {
            Config config;

            if (Files.exists(CONFIG_PATH)) {
                config = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
            } else {
                config = new Config();
            }
            config.version = VERSION;
            overrideConfig(config);
            CONFIG = config;
            ENABLED = true;
        } catch(Throwable exception) {
            ENABLED = false;
            Worldless.LOGGER.error("Something went wrong while reading config!");
            exception.printStackTrace();
        }

        return ENABLED;
    }

    public static void overrideConfig(Config config) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
            CONFIG = config;
        } catch (Exception e) {
            Worldless.LOGGER.error("Something went wrong while saving config!");
            e.printStackTrace();
        }
    }
}
