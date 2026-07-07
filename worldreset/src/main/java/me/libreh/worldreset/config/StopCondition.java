package me.libreh.worldreset.config;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;

public abstract class StopCondition {
    @SerializedName("type")
    public final String type;

    protected StopCondition(String type) {
        this.type = type;
    }

    public static class PortalEnter extends StopCondition {
        public static final String TYPE = "portal_enter";

        @SerializedName("block")
        public String block = "minecraft:end_portal";

        @SerializedName("require_all_players")
        public boolean requireAllPlayers = true;

        public PortalEnter() {
            super(TYPE);
        }
    }

    public static class EntityDeath extends StopCondition {
        public static final String TYPE = "entity_death";

        @SerializedName("entity")
        public String entity = "minecraft:ender_dragon";

        public EntityDeath() {
            super(TYPE);
        }
    }

    public static class Advancement extends StopCondition {
        public static final String TYPE = "advancement";

        @SerializedName("advancement")
        public String advancement = "minecraft:end/kill_dragon";

        @SerializedName("require_all_players")
        public boolean requireAllPlayers = false;

        public Advancement() {
            super(TYPE);
        }
    }

    public static class Adapter implements JsonDeserializer<StopCondition>, JsonSerializer<StopCondition> {
        @Override
        public StopCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            JsonElement typeEl = obj.get("type");
            if (typeEl == null) throw new JsonParseException("stop_conditions entry missing 'type'");
            String type = typeEl.getAsString();
            return switch (type) {
                case PortalEnter.TYPE -> ctx.deserialize(obj, PortalEnter.class);
                case EntityDeath.TYPE -> ctx.deserialize(obj, EntityDeath.class);
                case Advancement.TYPE -> ctx.deserialize(obj, Advancement.class);
                default -> throw new JsonParseException("Unknown stop_condition type: " + type);
            };
        }

        @Override
        public JsonElement serialize(StopCondition src, Type typeOfSrc, JsonSerializationContext ctx) {
            return ctx.serialize(src, src.getClass());
        }
    }
}
