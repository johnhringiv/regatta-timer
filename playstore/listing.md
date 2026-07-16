# Play Store listing — copy & console answers

Everything needed to fill in the Play Console, prepared while account verification pends.
Graphics regenerate via `generate-assets.ps1` (gitignored outputs); Wear screenshots live in
`docs/screenshots/`. Kept out of `docs/` deliberately — that directory deploys to the public site.

## App details

| Field                   | Value                                                                      |
| ----------------------- | -------------------------------------------------------------------------- |
| App name (≤30)          | `Regatta Timer`                                                            |
| Short description (≤80) | `Sailing race start timer: 5/3-min sequences, sync to the gun, works wet.` |
| Category                | Sports                                                                     |
| Tags                    | Boating, Clock alarm & timer, Racing, Sports                               |
| Website                 | https://regatta-timer.johnhringiv.com/                                     |
| Email (public)          | play@johnhringiv.com                                                       |
| Privacy policy URL      | https://regatta-timer.johnhringiv.com/privacy.html                         |

## Full description (≤4000 chars)

```
The race start timer sailing deserves — built for the wrist, the water, and the gun.

Generic watch timers fail on a start line: the screen falls back to the watch face
mid-sequence, wet fingers can't hit small buttons, and a missed start press can't be
corrected. Regatta Timer is built around the three things that matter:

⛵ THE DISPLAY NEVER LEAVES THE TIMER
The screen stays on through the whole sequence. If water on the screen forces the
watch into ambient mode, the countdown keeps ticking — display and haptic signals
included.

⛵ SYNC FIXES YOUR TIMING AT THE NEXT GUN
Missed the start press? Tap SYNC at any signal and the countdown rounds to the
nearest minute: 3:22 → 3:00, 3:40 → 4:00. You're back in sync without restarting.

⛵ EVERYTHING WORKS WET
Half-screen touch targets, a long-press-guarded reset so splashes can't kill your
sequence, and signals you feel instead of read.

FEATURES
• 5-minute (RRS 26) and 3-minute start sequences
• Automatic count-up after the start for elapsed race time
• Haptic signals: double buzz at the 4:00 preparatory, long buzz at 1:00, ticks
  through the final ten seconds (heavier from five), and an unmistakable start
  signal at 0:00
• Quick-launch tile: arm a 5 or 3 minute start straight from the watch face —
  and it shows when a timer is already running
• Watch-face complication: a ring where the dot is time remaining and a ticking
  countdown in the center — one tap starts the last-used sequence
• Survives interruptions: an in-flight countdown or race is restored at the
  correct time even if the app closes or the watch reboots
• Battery guard: an armed timer releases the screen after 10 idle minutes
• Fully standalone: no phone, no companion app, no account, no ads, no network,
  no data collection — about 2 MB
• Free and open source (GPL-3.0): github.com/johnhringiv/regatta-timer
```

## Console questionnaire answers

| Form                                | Answer                                                        |
| ----------------------------------- | ------------------------------------------------------------- |
| Data safety — data collected        | **None** (no network access; timer state stored locally only) |
| Data safety — data shared           | None                                                          |
| Data safety — encryption in transit | N/A (no transmission)                                         |
| Data safety — deletion request      | N/A (nothing collected)                                       |
| Content rating (IARC)               | Utility; no violence/UGC/gambling/ads → Everyone              |
| Target audience                     | 18 and over (avoids Families policy; not child-directed)      |
| Ads                                 | Contains no ads                                               |
| News app                            | No                                                            |
| COVID-19 app                        | No                                                            |
| Government app                      | No                                                            |
| Financial features                  | None                                                          |
| Health features                     | None                                                          |
| Login required                      | No                                                            |

## Assets checklist

- [x] App icon 512×512 + feature graphic 1024×500 — run `playstore/generate-assets.ps1` (gitignored outputs; regenerate on upload day)
- [x] Wear OS screenshots (1:1, ≥384px) — `docs/screenshots/*.png` (armed, countdown, ambient, countup, tile_armed, tile_running)
- [ ] Closed-test tester list — collect via the beta-tester issue form
- [ ] Upload `.aab` (`gradlew :app:bundleRelease`), enroll in Play App Signing on first upload

## Release-time reminders

- Play requires the **App Bundle** (`app-release.aab`), not the APK
- The R8 mapping is embedded in the AAB automatically (vitals deobfuscation just works)
- Wear OS form factor: opt in under Advanced settings → Form factors → Wear OS
- 12 testers × 14 continuous days on the closed track before production access
