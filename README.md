# World Reset
A world resetting utility for Fabric.

## Commands and permissions
- `/worldreset reload` - Reloads configuration (requires `worldreset.reload`)
- `/worldreset <seconds>` - Makes the world reset every X seconds (requires `worldreset.main`)
- `/worldreset stop` - Stops the world reset, `/worldreset 0` also works (requires `worldreset.main`)
- `/reset` - Performs a world reset immediately (requires `worldreset.reset`)

All permissions are available with operator as well

## Configuration
```json5
{
  // Don't touch!
  "config_version": 4,
  // Enable timer countdown sounds
  "countdown_sounds": true,
  // Enable restart chat message
  "restart_message": true,
  // Stop the timer if either condition is met
  "stop_timer_on": {
    // All players have to enter
    "end_fountain_enter": true,
    // Has to die "naturally" (no /kill)
    "dragon_death": false
  },
  // Supports long, string, or "random" seed
  "seed": "random",
  // Configure spawn location
  "spawn_near": {
    // Type of spawn: "structure" or "biome"
    "type": "structure",
    // Target structure or biome (e.g., "minecraft:stronghold", "minecraft:plains")
    "target": "minecraft:stronghold",
    // Block offset from the target location
    "offset": 0,
    // Whether to require a surface block for spawning
    "require_surface": true
  },
  // Reset player state on world reset
  "reset_on_load": {
    // Remove all potion effects
    "effects": true,
    // Reset health, air supply, fire, and velocity
    "health": true,
    // Reset food level and saturation
    "hunger": true,
    // Reset all game statistics
    "statistics": true,
    // Reset all advancements
    "advancements": true,
    // Reset XP levels, points, and score
    "experience": true,
    // Clear inventory and ender chest contents
    "inventory": true,
    // Reset recipe knowledge
    "recipes": true,
    // Reset player attributes (health boost, speed, etc.)
    "attributes": true,
    // Set time of day in game ticks (0-24000, -1 to disable)
    // Common values: 0 (dawn), 6000 (noon), 12000 (dusk), 18000 (midnight)
    "time_of_day": -1
  }
}
```

## Credits
- [The World Resets every 5 Minutes, Can we beat it?](https://www.youtube.com/watch?v=--IQ56rqYhE) [inspiration]

### If you have any suggestions or found a bug feel free to open an issue! (PRs welcome)