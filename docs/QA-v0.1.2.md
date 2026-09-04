# TapRelay 0.1.2 — QA record

**Build**: versionName `0.1.2`, versionCode `13`, DB schema `8`
**Device**: Google Pixel 7 (`panther`), Android 15, in-place upgrade from vc12
**Toolchain**: Microsoft JDK 21 (`jdk-21.0.12.101-hotspot`); the Studio JBR is Java 25 and breaks the compiler.

## Why 0.1.2 and not a re-cut of 0.1.1

`0.1.1` (vc12) was already published to R2 with SHA-256 `489a7c9f…` and 6,937,482 bytes.
Published versioned objects are immutable, so this work ships as a new version rather than
overwriting bytes someone may already have downloaded.

## Automated

| Check | Result |
|---|---|
| `testReleaseUnitTest` | **112 / 112 passed**, 0 failures (was 100 at 0.1.1) |
| `lintVitalRelease` | clean |
| `assembleRelease` (R8 + resource shrink) | clean |
| `apksigner verify` | Signer #1 `CN=ARBH Labs, OU=TapRelay` — signature matches the installed app, so the upgrade kept all data |

New test files: `ActivationModeTest` (activation resolution, converter round-trip, MIGRATION_7_8
bounds), `SensiboCapabilitiesTest` (capability parsing, snapshot, capability-driven fan toggle,
mode set).

## Real-hardware verification (Pixel 7)

### Xbox Wireless Controller
| Item | Result |
|---|---|
| Android sees the pad | Yes — `dumpsys input` device 113/114, `KEYBOARD \| GAMEPAD \| JOYSTICK \| VIBRATOR`, HAT_X/HAT_Y and L/R trigger axes present |
| TapRelay identifies it | Yes — "Xbox Wireless Controller · Connected · Ready" |
| A / B / X / Y | All four mapped and firing |
| D-pad | Mapped via the hat-axis path (`D-Pad Up` → Pure Air Purifier) |
| Mapping creation | Works (see the focus-window fix below) |
| Mapping persistence | 5 mappings survived `force-stop`, reinstall, and the vc12 → vc13 upgrade |
| Debounce | 350 ms per input in `ControllerInputProcessor`, plus 1500 ms per tag in `ActionExecutor`; no double execution observed |
| Controller → action execution | Confirmed on real Govee, Smart Life and Sensibo hardware |
| Reconnect | Pad slept mid-session and TapRelay correctly showed "No controller detected"; on wake it reappeared automatically via `InputDeviceListener` |
| Quick Controls as a controller target | Yes — `A Button` set to Quick Controls |

### Fixed during this run: controller could not be assigned
The learning step used a Compose `AlertDialog`. A Compose `Dialog` gets **its own window**, and
Android's `InputDispatcher` delivers gamepad events only to the focused window — so the pad drove
system focus and `MainActivity.dispatchKeyEvent` never saw a press. The learning surface is now
drawn in the activity's own window, and `ControllerManager` swallows every controller key while
learning (including the ups and repeats the processor discards) so a press assigns instead of
moving focus.

### Sensibo
Aaron's Pure air purifier reports exactly `low` and `high` in `remoteCapabilities` — no `medium`,
no `quiet`, no `auto`, and a single `fan` mode.

| Behaviour | Result |
|---|---|
| tap → toggle Low ↔ High | Works, and now uses the unit's real slowest/fastest instead of assuming the strings "low"/"high" |
| tap → turn ON directly at a speed | Works; the wizard only offers Low and High |
| tap → normal power toggle | Works |
| trigger → Quick Controls → power / fan in the moment | Works; verified setting Fan Low against the live unit |

**Mismatch fixed**: the wizard previously offered High / Med / Low / Quiet / Auto — three of which
this unit cannot do. Fan levels and modes are now read from the device everywhere.

### Quick Controls
- Govee light: power, brightness, white temperature, colour swatches. No fan controls.
- Sensibo purifier: power + Low/High only. No brightness, no colour, no mode picker (one mode is
  not a choice).
- Verified live: Desk Lamp turned on, purifier set to Fan Low, both reflected in the header.

### Remote Mode (AOD)
Clock and date, controller identity and battery (shown only when the pad reports one), a drawn pad
that rings mapped controls and flashes real presses, per-mapping rows with live power dots, and
last-outcome text. OLED-minimal on pure black, auto-dims to 3% after 18 s, brightens on touch or
press, and drifts its layout within safe bounds so nothing burns in — the same approach as the
LastDose AOD. Reachable from Controllers & Remotes or the "TapRelay Remote" Quick Settings tile.

## Android background-controller limitation (unchanged, by design)

Controller input is delivered only to the focused window. A foreground service has no
`InputChannel`; `MediaSession` only ever receives media keys; a non-focusable overlay receives
nothing and a focusable one steals input from every other app. The only ways round it are an
`AccessibilityService` (a Play policy violation for input remapping) or an overlay that breaks the
phone. **TapRelay does neither.** Remote Mode is the compliant answer: lock-screen-capable,
always-awake, self-dimming, one tap away from any screen via the QS tile.

## Regression sweep

NFC (foreground dispatch, reader mode, LastDose passthrough), Govee, Tuya/Smart Life, Sensibo,
light groups, scenes, colour and brightness combining, tap history, Pro entitlement and the master
admin PIN, credential storage, and the existing 6 tags all verified intact after the in-place
upgrade. `MIGRATION_7_8` adds two nullable columns; every tag and mapping written before 0.1.2
keeps executing exactly as before.

## Open item for Aaron

The Quick Controls surface for a tag tapped **while TapRelay is closed** (`QuickControlsActivity`,
started by the NFC trampoline) is verified by construction — it shares `QuickControlsContent` with
the in-app sheet, which is hardware-verified, and reuses the translucent activity pattern
`NfcTrampolineActivity` has shipped since 0.1.0. It was not driven end-to-end because a
non-exported activity cannot be launched from `adb`. One physical tag tap on a tag set to Quick
Controls would close that gap.
