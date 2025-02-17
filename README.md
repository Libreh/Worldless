﻿![Logo](https://i.imgur.com/udSDGS8.png)

# Worldless
A server-side Worldless mod for Fabric.

```txt
⚠️⚠️⚠️ WARNING ⚠️⚠️⚠️
This mod DELETES WORLD FILES on startup and shutdown!
```

## Commands (and permissions):
- `/worldless reload` - Reloads configuration (requires `worldless.reload`)
- `/worldless <seconds>` - Makes the world vanish every X seconds (`worldless.main`, requires permission)
- `/worldless stop` - Stops the world vanishing, `/worldless 0` also works (`worldless.main`, requires permission)

All permissions are available with operator as well

## Configuration
```json5
{
  // Is heard at 10 seconds or lower
  "countdownSounds": true,
  // Ends timer on DRAGON_DEATH or END_FOUNDATION (all players have to be in it)
  "endTimerOn": "DRAGON_DEATH"
}
```

## Credits
- [The World Resets every 5 Minutes, Can we beat it?](https://www.youtube.com/watch?v=--IQ56rqYhE&t=34s) [inspiration]
