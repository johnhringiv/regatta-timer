# Regatta Timer — Product Requirements & Behavior

The source of truth for every deliberate behavior in the app. If the app disagrees with
this document, one of them has a bug. Update this file in the same PR as any behavior change.

**Product statement:** a standalone Wear OS race start timer for sailors, built around three
requirements no generic timer meets: the display never leaves the timer mid-sequence,
everything works wet, and a missed start press is correctable at the next gun.

Target device: Pixel Watch 3 (primary); any Wear OS 5+ round watch (minSdk 34).
No phone, no companion app, no account, no network, no data collection.

---

## 1. States & transitions

```
Idle(mode) ──START──▶ Countdown(mode, deadline) ──deadline──▶ CountUp(mode, zero)
   ▲  ▲                       │        │                            │
   │  └──────── long-press reset ──────┴──────── long-press reset ──┘
   └── mode toggle (Idle only)
```

- **Idle**: armed at the mode's full duration (5:00 or 3:00), waiting for the warning signal.
- **Countdown**: running toward the gun. `deadline` is a `SystemClock.elapsedRealtime()` anchor.
- **CountUp**: race in progress; `zero` is the gun's elapsedRealtime anchor. Entered
  automatically at 0:00 — the displayed zero uses the _deadline_ (not "now") so count-up is exact.
- All timing derives from elapsedRealtime anchors — never accumulated ticks — so delayed
  coroutine wakeups can never drift the clock.

## 2. Modes

| Mode  | Duration | Signal model                              |
| ----- | -------- | ----------------------------------------- |
| FIVE  | 5:00     | RRS 26 (5-4-1-0)                          |
| THREE | 3:00     | club dinghy start (3-2-1-0, every minute) |

Mode is toggled by tapping the top zone while Idle, or pre-armed via the tile. Toggling is
impossible once a sequence runs.

## 3. Sync (the signature feature)

Tapping SYNC during a countdown rounds the remaining time to the **nearest whole minute**
(exact :30 rounds up): 3:22 → 3:00, 3:40 → 4:00. Under 0:30 remaining, sync fires the gun
immediately. Result can mathematically never exceed the armed duration. Purpose: correct the
timer against any gun/signal without restarting the sequence.

## 4. Controls (wet-first design)

| Phase     | Top half tap     | Bottom half tap | Long-press (~500 ms) |
| --------- | ---------------- | --------------- | -------------------- |
| Idle      | toggle 5:00/3:00 | START           | —                    |
| Countdown | SYNC             | (nothing)       | bottom: RESET        |
| CountUp   | (nothing)        | (nothing)       | anywhere: RESET      |

- Half-screen zones because wet fingers can't hit small buttons.
- Reset requires a deliberate long-press so splashes can't kill a sequence; it double-buzzes.
- Reset returns to Idle **preserving the mode** and clears persisted race state.
- **No crown/rotary controls** — tried in v0.3, removed in v0.4: accidental rotation silently
  re-synced the timer. Do not reintroduce.
- Every tap (including no-ops) re-arms the idle screen guard (§6).

## 5. Haptic vocabulary (no audio — watch speakers are useless on the water)

| Event                               | Feel                                                                           |
| ----------------------------------- | ------------------------------------------------------------------------------ |
| Button press (start/sync/toggle)    | light click (EFFECT_CLICK)                                                     |
| 4:00 — preparatory (FIVE mode only) | double buzz (250 ms × 2)                                                       |
| 2:00 (THREE mode only)              | single buzz (500 ms)                                                           |
| 1:00 — one-minute (both modes)      | one long buzz (700 ms)                                                         |
| 10…6 s                              | light tick each second (EFFECT_TICK)                                           |
| 5…1 s                               | heavy click each second (EFFECT_HEAVY_CLICK) — stage change marks "five to go" |
| 0:00 — gun                          | short+long double blast (400 ms, 150 ms gap, 800 ms)                           |
| Reset confirm                       | double pulse (80 ms × 2)                                                       |

FIVE mode is deliberately **silent at 3:00 and 2:00** — no real signal exists there (RRS 26),
and phantom buzzes could be mistaken for signals. Cues are edge-triggered on the displayed
second so a sync can never double-fire or skip them. Two-stage final count (not a gradual
ramp) because discrete stage changes are countable on the wrist; amplitude ramps are not.

## 6. Screen policy

| Phase     | Behavior                                                                                                                      |
| --------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Idle      | screen held full-on; released silently after **10 min without any tap** (battery guard); any tap re-holds                     |
| Countdown | screen held full-on, always (≤5 min, bounded); partial wake lock for the duration so ticking + haptics survive forced ambient |
| CountUp   | screen released; ambient always-on display takes over                                                                         |

**Wet-forced ambient is a first-class case**: water triggers the palm gesture and forces
ambient at any time. In ambient: Idle/Countdown render the same layout dimmed (gray digits,
live seconds, cues keep firing) with the labels replaced by "rotate crown to wake" — touch is
never delivered in ambient, so advertising buttons there would lie. CountUp ambient renders
minute-precision ("RACE / N min") with a burn-in offset, ticker paused; wake recomputes exact
seconds from the anchor.

## 7. Persistence (survives process death and reboot)

State transitions (start / sync / gun / reset) persist to SharedPreferences keyed on
**wall-clock** timestamps. On launch, a valid persisted race restores silently:
in-flight countdown resumes at the correct remaining; if the deadline passed while dead,
the app opens directly in CountUp at the correct elapsed (no gun haptic on restore).
Validity window: **12 hours**; reset clears. Wall-clock (not elapsedRealtime) so restore
works across reboots. Single source of validity: `RaceStore.activeRace()`.

## 8. Tile

Swipe-accessible from the watch face. Two states, driven by the same persisted race record:

- **Nothing running**: "REGATTA" + two chips (5 min / 3 min) that open the app pre-armed in
  that mode (never disturbs a running sequence if raced past them).
- **Timer in flight**: status line (COUNTDOWN or RACING, green) + single "Open" chip.
  The app requests a tile refresh on every phase transition.

## 9. Complication

One-tap starts from the watch face itself. The complication supplies typed data; the face
renders it (Pixel faces: weather-style ring + dot, center text, small icon at the bottom).
Configured once when added to a face (5 or 3 minutes, per-instance); "SET" while armed.

| State     | Ring (min 0, max duration)             | Center text        | Tap                       |
| --------- | -------------------------------------- | ------------------ | ------------------------- |
| Armed     | full                                   | "SET"              | open app **auto-started** |
| Countdown | dot sweeps (platform-evaluated, ~1 Hz) | ticking count-down | open app                  |
| Count-up  | empty                                  | ticking count-up   | open app                  |

- Sailboat monochromatic icon in all states (the face may hide it in tight slots).
- **Zero-push design**: countdown text ticks via time-difference text; the dot sweeps via a
  dynamic value; a data timeline flips countdown → count-up at the gun — all rendered by the
  platform even if the app process is dead. Pushes happen only on phase transitions
  (start / sync / gun / reset), same hook as the tile.
- Tap-to-start opens the app running (not a silent background start): the ticker, haptics,
  wake lock, and screen policy live in the app process — a mute start would skip every cue.
  Repeated deliveries can't restart a sequence (start() no-ops unless Idle).
- Types: RANGED_VALUE (preferred) + SHORT_TEXT fallback (same text/icon/tap, no ring).
- 12 h validity mirrors persistence (§7).

## 10. Visual language

- Black background always (OLED, sunlight).
- Digits: white (idle/countdown) → **amber** final 10 s → **green** count-up.
  Sunlight readability beats branding: digits are never gold/green-on-gold.
- Labels: burgee gold `#F5C518` (brand); dim gray in ambient.
- Time text ~68 sp, tabular figures (no jitter). System TimeText clock shown when interactive.
- Icon: gold burgee (flying right) + green Bootstrap stopwatch, colors from the
  johnhringiv.com burgee favicon; all pennant vertices r=30 from icon center (circular-mask safe).

## 11. Explicitly out of scope (decided against)

- Audio signals (inaudible on the water; haptics only)
- Crown/rotary input (accidental-activation risk — see §4)
- Telemetry/analytics of any kind (Play Console platform stats suffice)
- Live ticking time on the tile (refresh-budget rabbit hole)
- Continuous haptic amplitude ramps (indistinguishable on the wrist)

## 12. Versioning & release

- `versionCode`: bumped on every feature-branch change (CI-enforced > main).
- `versionName`: bumped once per PR to main (CI-enforced ≠ main). Squash-merge only;
  each commit on main is a release. Merge auto-publishes a GitHub release with the signed
  APK + R8 mapping, and deploys the site (regatta-timer.johnhringiv.com).
- Distribution: GitHub releases (sideload) now; Google Play closed test → production later
  (see `playstore/listing.md`).

## 13. Roadmap (open issues)

- [#4](https://github.com/johnhringiv/regatta-timer/issues/4) Custom sequence lengths
- [#5](https://github.com/johnhringiv/regatta-timer/issues/5) Race log (start timestamps)
- [#6](https://github.com/johnhringiv/regatta-timer/issues/6) Ongoing Activity chip during count-up
