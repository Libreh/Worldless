# World Reset

Reset the server world without closing the server, using command, timer or custom predicate.
The timer can be stopped using /worldreset stop or a stop predicate.

Make world spawn be set near a specific biome or structure, fine-tune player state resets, and change world pool size.

Integrates with [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) (used by [LuckPerms](https://modrinth.com/plugin/luckperms), [PlayerRoles](https://modrinth.com/mod/player-roles)), and [Predicate API](https://github.com/Patbox/PredicateAPI) for custom predicates.

## Commands and permissions

| Command | Permission | Description                                                              |
|---|---|--------------------------------------------------------------------------|
| /worldreset reset [seed] | worldreset.reset | Immediately resets the world (optional seed, "random" supported)         |
| /worldreset reload | worldreset.reload | Reloads configuration                                                    |
| /worldreset timer <duration> | worldreset.timer | Sets the reset timer (e.g. "5m", "300s")                                 |
| /worldreset stop | worldreset.stop | Stops the timer                                                          |
| /worldreset seed <seed> | worldreset.seed | Sets the seed (supports "random")                                        |
| /worldreset spawn none | worldreset.spawn | Disables spawn near                                                      |
| /worldreset spawn structure <id> | worldreset.spawn | Spawns near a structure (e.g. "minecraft:stronghold")                    |
| /worldreset spawn biome <id> | worldreset.spawn | Spawns near a biome (e.g. "minecraft:plains")                            |
| /worldreset spawn offset <blocks> | worldreset.spawn | Block offset from the target location                                    |
| /worldreset spawn require_surface <bool> | worldreset.spawn | Requires a surface block for spawning                                    |
| /worldreset triggers list [stop\|reset] | worldreset.triggers | Lists triggers (default: both)                                           |
| /worldreset triggers clear [stop\|reset] | worldreset.triggers | Clears triggers (default: both)                                          |
| /worldreset triggers remove <stop\|reset> <index> | worldreset.triggers | Removes a trigger by index                                               |
| /worldreset triggers add <stop\|reset> portal <block> [origin_dimension] [require_all_players] | worldreset.triggers | Adds a portal enter trigger (origin_dimension: e.g. minecraft:overworld) |
| /worldreset triggers add <stop\|reset> death <entity> | worldreset.triggers | Adds an entity death trigger                                             |
| /worldreset triggers add <stop\|reset> advancement <id> [require_all_players] | worldreset.triggers | Adds an advancement trigger                                              |
| /reset [seed] | worldreset.reset | Alias for /worldreset reset                                              |

`/wr` is an alias for `/worldreset`. All permissions default to operator level 2.

Durations support seconds (`15`/`15s`), minutes (`3m`), hours (`2h`), days (`7d`), and combinations (`1h30m`).

## Configuration

```json5
{
  // Don't touch!
  "config_version": 8,
  // Number of worlds kept preloaded for instant resets, if 0 players need to be teleported to worldreset:lobby
  "pool_size": 1,
  // Chunk radius to preload per world (number, or percentage of view distance e.g. "50%")
  "preload_distance": "4",
  // Spoofs custom worldreset:dimension id to show vanilla minecraft:dimension id
  // This is useful for compatibility with shaders which rely on vanilla dimensions ids
  // If enabled players are teleported to worldreset:lobby to avoid chunks bleeding into eachother
  // If disabled players are immediately teleported to next pooled world which is slightly faster
  "spoof_dimension": true,
  // Enable timer countdown sounds
  "countdown_sounds": true,
  // Enable restart chat message
  "restart_message": true,
  // Triggers that stop the countdown timer early
  "stop_triggers": [
    {
      "type": "worldreset:portal_enter",
      "block": "minecraft:end_portal",
      "origin_dimension": "minecraft:the_end",
      "require_all_players": false
    },
    {
      "type": "worldreset:advancement",
      "advancement": "minecraft:nether/uneasy_alliance",
      "require_all_players": false
    }
  ],
  // Triggers that immediately reset the world
  "reset_triggers": [
    {
      "type": "worldreset:entity_death",
      "entity": "minecraft:player",
      "filter": {
        "type": "entity",
        "value": {
          "type_specific": {
            "type": "player",
            "gamemode": ["survival"]
          }
        }
      }
    }
  ],
  // Supports long, string, or "random" seed
  "seed": "random",
  // Configure spawn location
  "spawn_near": {
    // Type of spawn: "none", "structure", or "biome"
    "type": "none",
    // Target structure or biome (e.g. "minecraft:stronghold", "minecraft:plains")
    "target": "",
    // Block offset from the target location
    "offset": 0,
    // Whether to require a surface block for spawning
    "require_surface": false
  },
  // Reset player state on world reset
  "reset_on_load": {
    // Remove all potion effects
    "effects": false,
    // Reset health, air supply, fire, and velocity
    "health": false,
    // Reset food level and saturation
    "hunger": false,
    // Reset all game statistics
    "statistics": false,
    // Reset all advancements
    "advancements": false,
    // Reset XP levels, points, and score
    "experience": false,
    // Clear inventory and ender chest contents
    "inventory": false,
    // Reset recipe knowledge
    "recipes": false,
    // Reset player attributes (health boost, speed, etc.)
    "attributes": false,
    // Gamemode applied to each player after reset (overrides hardcore spectator lock); "none" to skip
    "gamemode": "survival",
    // Set time of day in game ticks (0-24000, -1 to disable)
    "time_of_day": -1,
    // Reset weather to clear
    "clear_weather": false
  }
}
```

Trigger types (used in both `stop_triggers` and `reset_triggers`):
- `worldreset:portal_enter` - triggers when a player enters a portal block
  - `block`: the portal block identifier (e.g. `minecraft:end_portal`)
  - `origin_dimension`: optional, the dimension the player must be in (e.g. `minecraft:overworld` for stronghold portal, `minecraft:the_end` for exit portal)
  - `require_all_players`: if true, all players must enter; if false, any player (default: false)
  - `filter`: optional [Predicate API](https://github.com/Patbox/PredicateAPI) predicate for player-level matching
- `worldreset:entity_death` - triggers when an entity dies
  - `entity`: the entity type identifier (e.g. `minecraft:ender_dragon`)
  - `filter`: optional Predicate API predicate for entity-level matching
- `worldreset:advancement` - triggers when a player earns an advancement
  - `advancement`: the advancement identifier (e.g. `minecraft:end/kill_dragon`)
  - `require_all_players`: if true, all players must earn it; if false, any player (default: false)
  - `filter`: optional Predicate API predicate for player-level matching

Example with a filter (only counts survival players entering the end portal):
```json5
{
  "type": "worldreset:portal_enter",
  "block": "minecraft:end_portal",
  "require_all_players": true,
  "filter": {
    "type": "entity",
    "value": {
      "condition": "minecraft:entity_properties",
      "predicate": { "gamemode": "survival" }
    }
  }
}
```

## Credits
- [The World Resets every 5 Minutes, Can we beat it?](https://www.youtube.com/watch?v=--IQ56rqYhE) [inspiration]
- [WorldReset (Bukkit plugin)](https://modrinth.com/plugin/worldreset) [inspiration]
