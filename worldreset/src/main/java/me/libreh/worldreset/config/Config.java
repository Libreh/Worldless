package me.libreh.worldreset.config;

import com.google.gson.annotations.SerializedName;
import eu.pb4.predicate.api.BuiltinPredicates;
import eu.pb4.predicate.api.MinecraftPredicate;
import me.libreh.worldreset.WorldReset;
import me.libreh.worldreset.api.PlayerReset;
import me.libreh.worldreset.api.PlayerResetEvents;
import me.libreh.worldreset.predicate.AdvancementPredicate;
import me.libreh.worldreset.predicate.EntityDeathPredicate;
import me.libreh.worldreset.predicate.PortalEnterPredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.GameTypePredicate;
import net.minecraft.advancements.criterion.PlayerPredicate;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Config {
    @SerializedName("config_version")
    public int version = ConfigManager.VERSION;

    @SerializedName("pool_size")
    public int poolSize = 1;

    @SerializedName("preload_distance")
    public String preloadDistance = "4";

    @SerializedName("spoof_dimension")
    public boolean spoofDimension = true;

    @SerializedName("countdown_sounds")
    public boolean countdownSounds = true;

    @SerializedName("restart_message")
    public boolean restartMessage = true;

    @SerializedName("stop_triggers")
    public List<MinecraftPredicate> stopTriggers = defaultStopTriggers();

    @SerializedName("reset_triggers")
    public List<MinecraftPredicate> resetTriggers = defaultResetTriggers();

    private static List<MinecraftPredicate> defaultStopTriggers() {
        List<MinecraftPredicate> list = new ArrayList<>();
        list.add(new PortalEnterPredicate(
            Identifier.parse("minecraft:end_portal"),
            false,
            Optional.of(Identifier.parse("minecraft:the_end")),
            Optional.empty()
        ));
        list.add(new AdvancementPredicate(
            Identifier.parse("minecraft:nether/uneasy_alliance"),
            false,
            Optional.empty()
        ));
        return list;
    }

    private static List<MinecraftPredicate> defaultResetTriggers() {
        List<MinecraftPredicate> list = new ArrayList<>();
        EntityPredicate filter = EntityPredicate.Builder.entity()
            .subPredicate(PlayerPredicate.Builder.player().setGameType(GameTypePredicate.of(GameType.SURVIVAL)).build())
            .build();
        list.add(new EntityDeathPredicate(
            Identifier.parse("minecraft:player"),
            Optional.of(BuiltinPredicates.vanillaEntityPredicate(filter))
        ));
        return list;
    }

    @SerializedName("seed")
    public String seed = "random";

    @SerializedName("spawn_near")
    public SpawnNear spawnNear = new SpawnNear();

    @SerializedName("reset_on_load")
    public ResetOnLoad resetOnLoad = new ResetOnLoad();

    public static class SpawnNear {
        @SerializedName("type")
        public SpawnType type = SpawnType.NONE;

        @SerializedName("target")
        public String target = "";

        @SerializedName("offset")
        public int offset = 0;

        @SerializedName("require_surface")
        public boolean requireSurface = false;

        public SpawnNear copy() {
            SpawnNear copy = new SpawnNear();
            copy.type = this.type;
            copy.target = this.target;
            copy.offset = this.offset;
            copy.requireSurface = this.requireSurface;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpawnNear other)) return false;
            return type == other.type && offset == other.offset
                && requireSurface == other.requireSurface && target.equals(other.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, target, offset, requireSurface);
        }
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

        @SerializedName("gamemode")
        public String gamemode = "survival";

        @SerializedName("time_of_day")
        public int timeOfDay = 1000;

        @SerializedName("clear_weather")
        public boolean clearWeather = true;

        public void applyTo(ServerPlayer player) {
            PlayerResetEvents.BEFORE_PLAYER_RESET.invoker().onPlayerReset(player);
            if (effects) PlayerReset.resetEffects(player);
            if (health) PlayerReset.resetHealth(player);
            if (hunger) PlayerReset.resetHunger(player);
            if (statistics) PlayerReset.resetStatistics(player);
            if (advancements) PlayerReset.resetAdvancements(player);
            if (experience) PlayerReset.resetExperience(player);
            if (inventory) PlayerReset.resetInventory(player);
            if (recipes) PlayerReset.resetRecipes(player);
            if (attributes) PlayerReset.resetAttributes(player);
            if (!gamemode.equalsIgnoreCase("none")) {
                PlayerReset.resetGameMode(player, GameType.byName(gamemode));
            }
            PlayerResetEvents.AFTER_PLAYER_RESET.invoker().onPlayerReset(player);
        }
    }

    public float resolvePreloadDistance(MinecraftServer server) {
        String raw = preloadDistance;
        if (raw.endsWith("%")) {
            float pct;
            try {
                pct = Float.parseFloat(raw.substring(0, raw.length() - 1));
            } catch (NumberFormatException e) {
                WorldReset.LOGGER.warn("Invalid preload_distance '{}', falling back to 0", raw);
                return 0;
            }
            return Math.max(1, server.getPlayerList().getViewDistance() * pct / 100.0f);
        }
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            WorldReset.LOGGER.warn("Invalid preload_distance '{}', falling back to 0", raw);
            return 0;
        }
    }

}
