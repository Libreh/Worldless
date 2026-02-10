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
  "config_version": 2,
  // Enable timer countdown sounds
  "countdown_sounds": true,
  // Enable restart chat message
  "restart_message": true,
  // Stop the timer if either condition is met
  "timer_stop_on": {
    // All players have to enter
    "end_fountain_enter": false,
    // Has to die "naturally" (no /kill)
    "dragon_death": true
  },
  // Supports long or random seed
  "seed": "random"
}
```

## Credits
- [The World Resets every 5 Minutes, Can we beat it?](https://www.youtube.com/watch?v=--IQ56rqYhE) [inspiration]

### If you have any suggestions or found a bug feel free to open an issue! (PRs welcome)