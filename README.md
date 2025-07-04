# Worldless
A server-side Worldless mod for Fabric.

```txt
⚠️⚠️⚠️ WARNING ⚠️⚠️⚠️
This mod DELETES WORLD FILES on startup and shutdown!
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
  // Is heard at 10 seconds or lower.
  "countdown_sounds": true,
  // Stops the timer if condition is met
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
