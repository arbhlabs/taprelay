# TapRelay 0.1.4 — QA record

**0.1.4 / versionCode 15**, SHA-256 `53c3ff7f58f07467f82a4df8b629441e08b29dafbe4f733a14274b1b10754ba1`,
7,747,951 bytes. Published and hash-verified live. Room schema **10**.

0.1.3 (vc14, the paywall removal) was built and tested but never published; it ships inside 0.1.4.

---

## 1. What shipped

### Controller / NFC / place → LastDose

The headline. `Right Trigger → Bowl +1` writes one LastDose entry with LastDose closed and never
brought forward.

The architecture is the point: **there is no controller-specific LastDose code.** A LastDose log is
saved as an ordinary `TagEntity` with `targetType = LASTDOSE_LOG`, so it is an *item* like a lamp,
and every trigger already routes `trigger -> item -> activation mode` through `TriggerRouter`. The
controller path, the NFC path and the Places path reached the feature with no work each, and a
future trigger type will too.

`ActionExecutor.run()` branches once, on `tag.isLastDose`, before anything device-shaped happens —
no provider lookup, no power state, no toggle — and rejoins the shared path at the same tap-history
row, the same pill and the same haptics the lamps use.

The cross-process call is `LastDoseClient` → LastDose's `ExternalActionProvider` → LastDose's
`canonicalLog`. Full contract in `LASTDOSE/ARBH_RELEASE_7.1.10.md`. Needs **LastDose 7.1.10+**.

### Analog trigger correctness

An Xbox LT/RT is an analog axis (`AXIS_LTRIGGER`/`AXIS_BRAKE`, `AXIS_RTRIGGER`/`AXIS_GAS`), not a
`KeyEvent`. `ControllerInputProcessor` already knew that; what it did not have was **hysteresis**.
It compared against a single 0.6 threshold for both directions, so any wobble across 0.6 —
which is exactly what a finger resting on a half-pulled trigger produces — re-fired, held off only
by a 350 ms debounce.

Now: activate at **0.65**, release at **0.30**, and once armed the trigger produces nothing until
the axis falls all the way below the release threshold. One pull is one action; holding is one
action; a deliberate release-and-pull is two.

---

## 2. Verified

| | |
|---|---|
| Unit tests | **130 pass, 0 failures** (119 before + 11 new), `testReleaseUnitTest` |
| Lint | `lintVitalRelease` clean |
| Release build | R8 clean, signed `CN=ARBH Labs, OU=TapRelay` (`7ed0357892…`) |
| Live download | `TapRelay-0.1.4.apk` and `TapRelay.apk` both byte-identical to the local APK |
| No regression | `TapRelay-0.1.2.apk` still resolves with its original hash |

New tests:

- `AnalogTriggerTest` — one pull is one action; holding never repeats; wobbling between the two
  thresholds does not re-fire; a partial pull is not an action; releasing re-arms; L and R are
  independent; `reset()` drops a held trigger so a reconnect starts clean.
- `LastDoseItemTest` — a tag written before 0.1.4 is still a device; a scene is not a LastDose
  item; the trigger source does not change what the item is.

## 3. NOT verified — needs the Pixel 7

**No device was attached to ADB during this run.** Everything below is verified by construction and
by the contract, not by a physical press. Do these before treating 0.1.4 as field-proven:

1. Install both APKs (`adb install -r`, **never** `connectedAndroidTest`). Confirm existing tags,
   controller mappings and places survived the 9 → 10 migration.
2. Menu → **LastDose Logs** → Add. The picker should list real LastDose logs with their own default
   amounts. Save `Bowl +1`.
3. Controllers → map **Right Trigger** to that item. Open Remote Mode. Pull RT once with LastDose
   closed: exactly one entry, correct amount, correct timestamp, LastDose does not appear, and the
   pill reads `Bowl 1 logged` with a success rumble.
4. Hold RT for five seconds — still one entry. Pull-release-pull deliberately — two entries.
5. Delete the log inside LastDose, pull RT: `That LastDose log no longer exists.`, no entry.
6. Uninstall LastDose, pull RT: `LastDose isn't installed.`
7. Tap an existing smart-home NFC tag — unchanged behaviour, and a LastDose entry can also be
   written from a TapRelay NFC tag pointed at the same item.

## 4. Traps worth not re-deriving

- **Controller input reaches only the focused window.** There is no compliant background gamepad
  listener on Android — not a service, not a `MediaSession`, not a non-focusable overlay. TapRelay's
  Remote Mode is a real activity, which is why it works. Do not promise "logs while TapRelay is
  fully closed"; promise "logs while TapRelay's remote face is up, with LastDose closed", which is
  the actual use case and what is delivered.
- **Package visibility.** Without `<queries><package android:name="com.lastdose.app"/></queries>`
  the provider is simply invisible on Android 11+ and every call fails as "not installed".
- **A debug TapRelay cannot talk to a release LastDose.** The certificate pin is on the release key.
  This is correct behaviour, and `LastDoseClient` reports it as unavailable rather than crashing.
- **The main item list filters LastDose items out** (`filterNot { it.isLastDose }`). They have their
  own screen; a card offering brightness and colour for a log would be nonsense. The Controllers
  picker deliberately does *not* filter — that is where a button is bound to one.
- Two stale Gradle daemons on a foojay-downloaded JDK 25 held `app/build/intermediates/lint-cache`
  open and made `clean` fail. Kill them (`Stop-Process`) rather than fighting `rm`.
