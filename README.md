# Worldless
A server-side Worldless mod for Fabric.

If you have any suggestions or found a bug feel free to open an issue! (PRs welcome)

```txt
⚠️⚠️⚠️ WARNING ⚠️⚠️⚠️
This mod DELETES the WORLD DIRECTORIES
region, poi, and entities on startup AND shutdown
⚠️⚠️⚠️ ⚠️⚠️⚠️ ⚠️⚠️⚠️
```

## Commands and permissions
- `/worldless reload` - Reloads configuration (requires `worldless.reload`)
- `/worldless <seconds>` - Makes the world vanish every X seconds (requires `worldless.main`)
- `/worldless stop` - Stops the world vanishing, `/worldless 0` also works (requires `worldless.main`)

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
  }
}
```

## Credits
- [The World Resets every 5 Minutes, Can we beat it?](https://www.youtube.com/watch?v=--IQ56rqYhE) [inspiration]
