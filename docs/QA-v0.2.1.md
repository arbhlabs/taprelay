# TapRelay 0.2.1 — QA record

**Build:** 0.2.1 / versionCode 17 · Room schema 11 · minSdk 30 / targetSdk 35 / compileSdk 35
**Device:** Pixel 7 (panther), **Android 17 / SDK 37**, wireless ADB, Xbox Wireless Controller
paired, LastDose 7.1.10 (vc109) installed.

---

## 1. Automated gates

| Gate | Result |
|---|---|
| `testDebugUnitTest` | PASS |
| `testReleaseUnitTest` | PASS |
| `lintVitalRelease` | PASS |
| `assembleRelease` (signed, R8 + resource shrinking) | PASS |
| Migration chain 1 → 11 complete, contiguous, no duplicate start versions | PASS (`MigrationChainTest`) |
| Error copy carries no technical jargon | PASS (`ErrorCopyTest`, incl. 8 new errors) |

New tests: `MagicActionTest` (sequence round-trip, unreadable JSON, step cap, delay clamping,
deleted-step naming), `WebActionTest` (scheme allow-list, log redaction, header round-trip, Home
Assistant URL normalisation), `MigrationChainTest`.

---

## 2. Real-device verification (Pixel 7)

### Magic Actions — VERIFIED END TO END

A "Bedtime" Magic Action was created from the built-in template, which pulled **five real items
out of Aaron's own setup** (Desk Lamp, Corner Lamp 70% Toggle, All ON/OFF, TV Lamp, Vent Lamp),
saved, and run.

Logcat, unedited:

```
TapRelayExec: toggle provider=govee_cloud read=0 sending=1
TapRelayExec: ok provider=govee_cloud action=TOGGLE targets=1/1
TapRelayExec: toggle provider=tuya_cloud  read=0 sending=1
TapRelayExec: ok provider=tuya_cloud  action=TOGGLE targets=1/1
TapRelayExec: toggle provider=govee_cloud read=0 sending=1
TapRelayExec: ok provider=govee_cloud action=TOGGLE targets=4/5
TapRelayExec: toggle provider=govee_cloud read=1 sending=0
TapRelayExec: ok provider=govee_cloud action=TOGGLE targets=1/1
TapRelayExec: toggle provider=tuya_cloud  read=1 sending=0
TapRelayExec: ok provider=tuya_cloud  action=TOGGLE targets=1/2
TapRelayExec: magic steps=5/5 ok=true (3818ms)
```

Five steps, two cloud providers, **3.8 s end to end**, real lamps driven. The live progress pill
appeared **within one second** of the tap showing the current step by name.

### Verified by observation on device

- Cold launch `TotalTime` **277 ms** (target < 300 ms).
- Home list, overflow menu, Actions screen, Magic Action editor, always-on face settings all
  render correctly with real data and correct provider names.
- Template → editable sequence → save → list → run, complete.
- Xbox Wireless Controller reported connected with 7 button mappings intact after upgrade.
- Room migration 10 → 11 succeeded in place: all six pre-existing tags, seven controller mappings
  and both LastDose items survived and read back correctly.

### Fixed during device QA

1. **Selected chips and tonal buttons rendered Material's default lavender**, clashing with the
   teal brand, because `secondaryContainer` / `tertiaryContainer` were never defined in either
   colour scheme. Every reachable Material role is now defined. This affected the whole app.
2. **Provider labels read "Device" everywhere new.** The provider ids are `govee_cloud` and
   `tuya_cloud`, not `govee` / `tuya`; four separate copies of the label logic had guessed the
   short forms. Collapsed into one `ItemLabels` using the real constants.
3. **The feedback pill covered the top app bar.** It now sits below it, and carries a step count
   and progress line while a Magic Action is mid-flight.
4. **The connections chip listed every provider by name**, which does not fit once Home Assistant
   is connected. Past two it becomes a count.
5. **"Always-on face" used an NFC icon**; "About TapRelay" had no icon and sat out of line.

---

## 3. Not verified on device — stated honestly

These are implemented and compile-clean, but were **not** exercised on hardware before release,
because the owner called the release at that point:

- **Home Assistant.** No Home Assistant instance is available here. The REST contract is
  implemented against the official documentation and covered by unit tests for URL normalisation
  and service-call construction; the network path itself is unverified.
- **Web request items**, including the encrypted-header path.
- **Do Not Disturb** items (needs the Notification Policy Access grant) and **Open app / link**.
- **Home-screen widgets** — placement, resize across breakpoints, tap-to-run, launcher restart,
  reboot. The rendering and tap paths are implemented and build clean; none was placed on the
  launcher.
- **The redesigned always-on face itself** — the settings screen was verified, the face was not.
- **Physical NFC tap** against a sticker, and **controller button → Magic Action**. The controller
  is paired and its mappings survived, but no button was pressed against a 0.2.1 build.
- **Post-fix frame measurement.** A pre-fix `dumpsys gfxinfo` sample over 13 launch frames showed
  23.08% janky / 65 ms P95; the sample was too small to be meaningful and no post-fix comparison
  was taken.

**Nothing in this list is claimed as working.** The engine underneath all of it is the same
`ActionExecutor.run()` proven above, which is why they are expected to work — but expected is not
verified.

---

## 4. Regression check

| Area | Status |
|---|---|
| Existing tags, mappings, places, LastDose items survive the upgrade | Verified on device |
| Govee execution | Verified (Magic Action steps 1, 3, 4) |
| Tuya / Smart Life execution | Verified (steps 2, 5) |
| Sensibo | Item present and correctly labelled; not executed |
| LastDose logging | Not exercised this run (verified in 0.1.4) |
| Controller mappings | Present and counted after upgrade; no button pressed |
| Edge/system Back (the 0.1.5 fix) | Preserved by construction; new screens own their own `BackHandler` |
| Entitlements | Unchanged — `canAccess()` still returns `true` for everything |
