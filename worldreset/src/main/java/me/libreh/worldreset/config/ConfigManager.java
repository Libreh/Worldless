package me.libreh.worldreset.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.pb4.predicate.api.GsonPredicateSerializer;
import eu.pb4.predicate.api.MinecraftPredicate;
import me.libreh.worldreset.WorldReset;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    public static final int VERSION = 8;
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("worldreset.json");
    private static final Path OLD_CONFIG_PATH = CONFIG_DIR.resolve("worldless.json");
    private static final HolderLookup.Provider LOOKUP = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(MinecraftPredicate.class, GsonPredicateSerializer.create(LOOKUP))
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
                String raw = Files.readString(CONFIG_PATH);
                config = loadFromString(raw);
            } else if (oldConfigFile.exists()) {
                String raw = Files.readString(OLD_CONFIG_PATH);
                config = loadFromString(raw);
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

    private static Config loadFromString(String raw) {
        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
        int configVersion = json.has("config_version") ? json.get("config_version").getAsInt() : 0;
        if (configVersion < VERSION) {
            migrate(json, configVersion);
        }
        return GSON.fromJson(json, Config.class);
    }

    private static void migrate(JsonObject json, int configVersion) {
        WorldReset.LOGGER.info("Migrating config from version {} to version {}", configVersion, VERSION);

        if (configVersion <= 4) {
            boolean endFountainEnter = true;
            boolean dragonDeath = false;

            if (configVersion == 1 && json.has("end_timer_on")) {
                String endTimerOn = json.get("end_timer_on").getAsString();
                if (endTimerOn.equals("end_fountain")) {
                    endFountainEnter = true;
                } else {
                    endFountainEnter = false;
                    dragonDeath = true;
                }
            } else if (configVersion >= 2 && configVersion <= 4 && json.has("stop_timer_on")) {
                JsonObject stopTimerOn = json.getAsJsonObject("stop_timer_on");
                if (stopTimerOn.has("end_fountain_enter")) {
                    endFountainEnter = stopTimerOn.get("end_fountain_enter").getAsBoolean();
                }
                if (stopTimerOn.has("dragon_death")) {
                    dragonDeath = stopTimerOn.get("dragon_death").getAsBoolean();
                }
            }

            json.remove("end_timer_on");
            json.remove("stop_timer_on");

            JsonArray triggers = new JsonArray();
            if (endFountainEnter) {
                JsonObject pe = new JsonObject();
                pe.addProperty("type", "worldreset:portal_enter");
                pe.addProperty("block", "minecraft:end_portal");
                pe.addProperty("origin_dimension", "minecraft:the_end");
                pe.addProperty("require_all_players", false);
                triggers.add(pe);
            }
            if (dragonDeath) {
                JsonObject ed = new JsonObject();
                ed.addProperty("type", "worldreset:entity_death");
                ed.addProperty("entity", "minecraft:ender_dragon");
                triggers.add(ed);
            }
            addWitherTriggers(triggers);
            json.add("stop_triggers", triggers);
        } else if (json.has("stop_triggers")) {
            JsonArray triggers = json.getAsJsonArray("stop_triggers");
            for (var element : triggers) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                if (!obj.has("type")) continue;
                String type = obj.get("type").getAsString();
                if (!type.contains(":")) {
                    obj.addProperty("type", "worldreset:" + type);
                }
            }
            addWitherTriggers(triggers);
        }

        if (!json.has("reset_triggers")) {
            JsonArray resetTriggers = new JsonArray();
            JsonObject playerDeath = new JsonObject();
            playerDeath.addProperty("type", "worldreset:entity_death");
            playerDeath.addProperty("entity", "minecraft:player");
            playerDeath.add("filter", survivalFilterJson());
            resetTriggers.add(playerDeath);
            json.add("reset_triggers", resetTriggers);
        }

        json.addProperty("config_version", VERSION);
    }

    private static void addWitherTriggers(JsonArray triggers) {
        boolean hasUneasyAlliance = false;
        for (var element : triggers) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            if (type.equals("worldreset:advancement") && obj.has("advancement")
                    && obj.get("advancement").getAsString().equals("minecraft:nether/uneasy_alliance")) {
                hasUneasyAlliance = true;
            }
        }
        if (!hasUneasyAlliance) {
            JsonObject adv = new JsonObject();
            adv.addProperty("type", "worldreset:advancement");
            adv.addProperty("advancement", "minecraft:nether/uneasy_alliance");
            adv.addProperty("require_all_players", false);
            triggers.add(adv);
        }
    }

    private static JsonObject survivalFilterJson() {
        JsonArray gamemodes = new JsonArray();
        gamemodes.add("survival");
        JsonObject playerSpecific = new JsonObject();
        playerSpecific.addProperty("type", "player");
        playerSpecific.add("gamemode", gamemodes);
        JsonObject entityValue = new JsonObject();
        entityValue.add("type_specific", playerSpecific);
        JsonObject filter = new JsonObject();
        filter.addProperty("type", "entity");
        filter.add("value", entityValue);
        return filter;
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(CONFIG));
        } catch (Exception e) {
            WorldReset.LOGGER.error("Error saving config!");
            e.printStackTrace();
        }
    }

    public static Config config() {
        if (CONFIG == null) {
            CONFIG = Config.DEFAULT;
        }
        return CONFIG;
    }
}
