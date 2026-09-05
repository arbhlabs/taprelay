# TapRelay — handoff, 2026-09-05 (after 0.2.1)

## TL;DR

**0.2.1 is shipped, live and hash-verified.** Nothing is half-finished in code.

The one thing left for a human: **hardware verification of the surfaces listed in
`docs/QA-v0.2.1.md` §3.** The release was called before they were exercised on the Pixel. They
compile, they ship, and the engine underneath them is proven on-device — but they were not pressed.

---

## 1. What is live

TapRelay **0.2.1 / versionCode 17**, SHA-256
`8ec11701c80201a0ffe38a2b995c05d8335799ee18d605564c3d62c872d079c4`, 7,982,103 bytes,
Room schema **11**.

- `https://arbhlabs.com/downloads/TapRelay-0.2.1.apk` and `/TapRelay.apk`, both hash-verified by
  download; 0.1.5, 0.1.4, 0.1.2 and 0.1.0 still resolve unchanged
- Tag `taprelay-0.2.1`; full record in `docs/QA-v0.2.1.md`, `docs/RESEARCH_0.2.1.md` and
  `dist/SITE_PUBLISH.md`
- Signing certificate SHA-256 `7ed03578…ba808` — the digest LastDose pins, so cross-app logging
  works with this build

---

## 2. The architecture that matters now

```
NFC tap ─────┐
Controller ──┤
Place ───────┼──> TriggerRouter.fire(tagId) ──> ActionExecutor.run()
Widget ──────┤                                    ├─ smart-home provider (Govee / Tuya /
Always-on ───┤                                    │   Sensibo / Home Assistant)
In-app ──────┘                                    ├─ LastDose        (targetType LASTDOSE_LOG)
                                                  ├─ web request     (WEBHOOK)
                                                  ├─ open app/link   (LAUNCH)
                                                  ├─ Do Not Disturb  (PHONE)
                                                  └─ Magic Action    (MAGIC_ACTION)
```

**A Magic Action step is a reference to another item, and running it re-enters
`ActionExecutor.run()` — the same call an NFC tap makes.** That is the whole design. The sequence
engine contains no knowledge of any integration, so every target type added from now on works
inside sequences on the day it lands. Do not give it one.

**Home Assistant is a `SmartHomeProvider`, not a feature.** Registering it in `ServiceLocator` was
the entire integration: the tag wizard, Quick Controls, controller mapping, places, Magic Actions,
the always-on face and widgets all understood HA entities without being touched. If somebody ever
proposes a "Home Assistant action type", this is why the answer is no.

**Adding a trigger type still means calling `fire()`. Adding an action target still means one
branch in `ActionExecutor.run()`.** Never a new execution path per trigger.

### Execution semantics (decided, not accidental)

- A failed step **does not stop the sequence**. There is deliberately no stop-on-failure switch.
- 12 s per step, 40 s overall on the widget path.
- One item runs once at a time (`ActionExecutor` single-flight claim), on top of the 1.5 s scan
  debounce. A repeated controller pull mid-sequence is one intent expressed twice.
- No retries. A nested Magic Action is refused at run time.
- `ActionExecutor.running` is a `StateFlow<Set<String>>`; **every surface subscribes to it and
  marks the thing the finger landed on**. That is what stops a cloud toggle feeling like a frozen
  button — a toggle must read the device's real state before it can invert it, and that round trip
  is hundreds of milliseconds. Do not "fix" that by guessing the state instead.

---

## 3. Open items

1. **Hardware verification of the untested surfaces** — `docs/QA-v0.2.1.md` §3 lists exactly what
   was not pressed: Home Assistant, widgets on a launcher, Do Not Disturb, open app/link, web
   requests, the redesigned always-on face itself, a physical NFC tap, and a controller button
   against a 0.2.1 build.
2. **Post-fix frame measurement.** The main-thread Keystore work is off the main thread now, but no
   before/after `gfxinfo` comparison was captured over a meaningful sample.
3. **Deferred by decision, documented in `docs/RESEARCH_0.2.1.md`:** decoupling `HomeUiState`,
   Baseline Profiles (needs a macrobenchmark module — adding `profileinstaller` alone does
   nothing), QR triggers, JSON backup/restore, controller chords.
4. **The paywall stays off.** The boundary is built and inert; `canAccess()` returns `true`. See
   `docs/RESEARCH_0.2.1.md` §5 for the three independent reasons and what would have to change.

---

## 4. Things learned the hard way (do not re-derive)

- **The provider ids are `govee_cloud` and `tuya_cloud`**, not `govee` / `tuya`. Four separate
  copies of a provider-label `when` had all guessed the short forms and all silently fell through
  to "Device". There is now one `ItemLabels`. Use it.
- **Define every Material colour role you can reach.** `secondaryContainer` was never set, so every
  selected `FilterChip` and every `FilledTonalButton` in the app was drawn in Material's baseline
  lavender next to TapRelay's teal. Leaving a role out does not disable it.
- **Room's `@Database` has CLASS retention** — you cannot read the schema version by reflection at
  run time. Hence `TAPRELAY_SCHEMA_VERSION`, which `MigrationChainTest` asserts the chain against.
- **Gamepad input reaches only the focused window.** No compliant background controller listener
  exists on Android. The always-on face works because it is a real, focused activity.
- **Widgets cannot animate.** "Buttery" is bought by writing the running state and redrawing
  *before* the asynchronous work starts, so the tile changes within a frame of the tap.
- **`RemoteViews(Map<SizeF, RemoteViews>)` is API 31+**; minSdk is 30, so it needs a guard and a
  single-layout fallback.
- **Tink decryption runs on whichever thread collects the flow.** Every `SecureKeyStorage` flow is
  `.flowOn(Dispatchers.IO)` for that reason; without it, launch does Keystore IPC on the main
  thread.
- Pixel 7 exposes only **60 Hz and 90 Hz** as app-selectable modes; lower render rates come from
  `View.REQUESTED_FRAME_RATE_CATEGORY_LOW`.
- `WindowManager.LayoutParams.screenBrightness = 0f` is the panel's dimmest, not off.
- Sensibo: `remoteCapabilities.modes[mode].fanLevels` is authoritative. Aaron's Pure air purifier
  reports only `low` and `high`. Never hardcode speeds.
- `ControllerInputProcessor.processTriggerAxes` has hysteresis (activate 0.65, release 0.30).
  `AnalogTriggerTest` pins it — do not simplify it back to one constant.
- Geofences are dropped on reboot and on app replace — `BootReceiver` rebuilds them from the DB.
- `adb` cannot start a non-exported activity.
- On Windows, a stale Gradle daemon holds `app/build/intermediates/lint-cache` open and makes
  `clean` fail. `Stop-Process` the daemon; `rm -rf` will not win.
- Build with **Microsoft JDK 21**. The Android Studio JBR is Java 25 and breaks the Kotlin compiler.
- **Never `connectedAndroidTest` against Aaron's device** — it has wiped app data before.
