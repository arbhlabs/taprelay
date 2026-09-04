# TapRelay — start here

Android app (`com.arbhlabs.taprelay`) that turns NFC tags, game-controller buttons and places
into smart-home actions across Govee, Tuya/Smart Life and Sensibo — and, since 0.1.4, into
LastDose log entries written without LastDose ever opening.

## Read these first

| File | What it is |
|---|---|
| `HANDOFF.md` | **Current state, what is half-finished, and the exact steps to finish it.** Read before touching anything. |
| `RESEARCH_BRIEF_NEXT.md` | The research + design brief for the next version (Devices dashboard). |
| `docs/QA-v0.1.4.md` | The current release's QA record, incl. what is still unverified on device. |
| `docs/QA-v0.1.2.md` | The 0.1.2 QA record; still the reference for Remote Mode and Quick Controls. |
| `CHANGELOG.md` | User-facing history. |
| `dist/SITE_PUBLISH.md` | Release/publish pipeline and the rules that constrain it. |

## Build

Use **Microsoft JDK 21**. The Android Studio JBR is Java 25 and breaks the Kotlin compiler.

```bash
JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.101-hotspot" ./gradlew.bat testReleaseUnitTest lintVitalRelease assembleRelease
```

Signing comes from `signing.properties` at the repo root. Never print or commit its contents.

## Non-negotiables

1. **No paywall.** The Govee and Tuya developer APIs are licensed for personal, non-commercial
   use. `EntitlementRepository.canAccess()` returns `true` for everything and must stay that way.
   The licence plumbing is intentionally left intact but inert.
2. **Published versions are immutable.** An APK already uploaded to R2 at a given version must
   never be overwritten with different bytes. Cut a new version instead.
3. **Room migrations are never destructive.** No `fallbackToDestructiveMigration`. Every schema
   change ships a migration; adding nullable columns or new tables keeps old rows behaving as
   they did. A missing migration must fail loudly rather than delete a user's tags.
4. **No AccessibilityService and no focus-stealing overlay** for controller input. Both are
   policy violations or user-hostile. See the limitation note in `docs/QA-v0.1.2.md`.
5. **The LastDose link is signature-pinned.** LastDose's `ExternalActionProvider` only accepts
   TapRelay's release certificate, so a debug build cannot log. Never widen that pin to make
   testing easier, and never report a log as successful unless LastDose returned `LOGGED`.
6. **Never `connectedAndroidTest` against Aaron's device** — it has wiped app data before.

## Architecture in one line

`trigger -> item -> activation mode -> execute / open item / Quick Controls`, all through
`trigger/TriggerRouter.kt`. Adding a trigger type means calling `fire()`, not adding a new
execution path; adding an action target means one branch in `ActionExecutor.run()`, not a path per
trigger. A LastDose log is an *item* (`targetType = LASTDOSE_LOG`), which is why every trigger type
could already fire one the day it was added. Quick Controls renders only what
`SmartHomeProvider.getCapabilities()` reports.
