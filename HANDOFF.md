# TapRelay — handoff, 2026-09-04 (after 0.1.4)

## TL;DR

**0.1.4 is shipped, live and hash-verified.** The working tree is clean. Nothing is half-finished.

The one thing left for a human: **on-device validation on the Pixel 7 with the Xbox pad** — no
device was attached to ADB during the 0.1.4 run. The exact script is section 3 of
`docs/QA-v0.1.4.md`. And **LastDose 7.1.10 is built and signed but not published** (see §3).

---

## 1. What is live

TapRelay **0.1.4 / versionCode 15**, SHA-256
`53c3ff7f58f07467f82a4df8b629441e08b29dafbe4f733a14274b1b10754ba1`, 7,747,951 bytes,
Room schema **10**.

- `https://arbhlabs.com/downloads/TapRelay-0.1.4.apk` and `/TapRelay.apk`, both hash-verified by
  download; 0.1.2, 0.1.1, 0.1.0 and 0.0.10 still resolve unchanged
- Tag `taprelay-0.1.4`; full record in `docs/QA-v0.1.4.md` and `dist/SITE_PUBLISH.md`

**0.1.3 is closed.** It was code-complete but never published; everything in it (the paywall
removal) ships inside 0.1.4. Do not resurrect it. The AGP/Gradle toolchain drift the previous
handoff warned about is no longer in the tree — 0.1.4 was built on the same Microsoft JDK 21
toolchain that produced 0.1.2.

## 2. The architecture that matters now

```
NFC tap ───┐
Controller ├──> TriggerRouter.fire(tagId) ──> activation mode ──> ActionExecutor
Place ─────┤                                                        ├─ smart-home provider
In-app ────┘                                                        └─ LastDose (tag.isLastDose)
```

**A LastDose log is an item, not a feature.** It is a `TagEntity` with
`targetType = LASTDOSE_LOG` and four nullable `lastDose*` columns. That is the whole reason
`Right Trigger → Bowl +1` works: the controller path, the NFC path and Places already routed
through `TriggerRouter`, so none of them needed touching. **Adding a trigger type still means
calling `fire()`, and adding an action target still means one branch in `ActionExecutor.run()` —
never a new execution path per trigger.**

`ActionExecutor.run()` branches on `tag.isLastDose` before any device-shaped work and rejoins the
shared path at the same tap-history row, pill and haptics.

### The LastDose contract

`LastDoseClient.kt` → LastDose `ExternalActionProvider` → LastDose `canonicalLog` (the same method
the LastDose on-screen button and its NFC tags use — there is no second logging implementation).
Full spec in `LASTDOSE/ARBH_RELEASE_7.1.10.md`. Points that will bite:

- **Needs LastDose 7.1.10+.** Older LastDose has no provider; the client reports "too old".
- **Signature-pinned.** LastDose pins TapRelay's release cert
  (`7ed03578928d3b8eaec35cf064154bee4d48f528b0021dd3dcd9f3fde73ba808`). A *debug* TapRelay is
  signed by the debug key and is refused — correct, not a bug. Test with the release APK.
- **Package visibility.** `<queries><package android:name="com.lastdose.app"/></queries>` in
  TapRelay's manifest. Without it the provider is invisible on Android 11+ and everything reads as
  "not installed".
- **Success is never assumed.** Only `LOGGED` (after LastDose has read the row back) is reported as
  success. `requestId` makes a duplicated delivery of one press a no-op.
- `LastDoseClient` calls block. Always off the main thread.

### Analog triggers

`ControllerInputProcessor.processTriggerAxes` now has hysteresis: activate at **0.65**, release at
**0.30**. Once armed the trigger produces nothing until the axis falls below the release threshold.
The old single 0.6 threshold re-fired on every wobble across it. `AnalogTriggerTest` pins the
behaviour — do not "simplify" it back to one constant.

## 3. Open items

1. **Pixel 7 + Xbox pad validation of 0.1.4.** Script in `docs/QA-v0.1.4.md` §3. Install with
   `adb install -r`, **never** `connectedAndroidTest`.
2. **LastDose 7.1.10 is not published.** It is built, signed and staged at
   `LASTDOSE/dist/LastDose-v7.1.10.apk` (vc109), committed and tagged `lastdose-7.1.10`. It was held
   back because 7.1.9 has also never been published or device-validated and would ship alongside it.
   Validate both, then publish through `tools/publish-release.mjs lastdose 7.1.10 …`.
   **Until that happens, TapRelay 0.1.4's LastDose logging does nothing on Aaron's phone** — it will
   say "Update LastDose to log from TapRelay."
3. **Quick Controls from a physical NFC tap while TapRelay is closed** — still verified by
   construction only. `adb` cannot start a non-exported activity, so this needs a human with a tag.

## 4. Things learned the hard way (do not re-derive)

- **Gamepad input reaches only the focused window.** A Compose `Dialog` gets its own window, so a
  learning dialog never sees the pad. Services, `MediaSession` and non-focusable overlays receive
  nothing at all. **There is no compliant background controller listener on Android** — Remote Mode
  works because it is a real, focused activity. Never promise logging with TapRelay fully closed;
  the delivered promise is "TapRelay's remote face is up, LastDose is closed", which is the actual
  use case.
- Pixel 7 exposes only **60 Hz and 90 Hz** as app-selectable display modes; the 20–45 Hz values in
  `mSupportedRefreshRates` are system render rates, reached with Android 15's
  `View.setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_LOW)`.
- `WindowManager.LayoutParams.screenBrightness = 0f` is the panel's dimmest, not off.
- Sensibo: `remoteCapabilities.modes[mode].fanLevels` is authoritative. Aaron's Pure air purifier
  reports **only `low` and `high`** (plus `auto`) and a single `fan` mode. Never hardcode speeds.
- Geofences are dropped on reboot and on app replace — `BootReceiver` rebuilds them from the DB.
- Background location is a separate grant and on Android 11+ usually only obtainable from Settings.
- `adb` cannot start a non-exported activity.
- **The main item list filters LastDose items out**; they have their own screen. The Controllers
  target picker deliberately does not filter — that is where a button gets bound to one.
- On Windows, a stale Gradle daemon (the foojay-downloaded JDK 25 ones especially) holds
  `app/build/intermediates/lint-cache` open and makes `clean` fail with "Unable to delete
  directory". `Stop-Process` the daemon; `rm -rf` will not win.
