# TapRelay — handoff, 2026-09-04

## TL;DR

**0.1.2 is shipped, live and verified.** **0.1.3 is built and tested but NOT released** — it sits
uncommitted in the working tree. There is also a **toolchain upgrade in the tree that nobody on
this session made**, which must be decided on before 0.1.3 goes out.

---

## 1. What is already done and live (nothing to do here)

TapRelay **0.1.2 / versionCode 13** is published and verified end to end.

- SHA-256 `4c3ac382f5ac4d55f8230f6ae24f3d6372a20cf59be994f2bbc58b9bc7a00021`, 7,715,111 bytes
- Live at `https://arbhlabs.com/downloads/TapRelay-0.1.2.apk` and `/TapRelay.apk`, both
  hash-verified by download; 0.1.1, 0.1.0 and 0.0.10 still resolve
- App repo commits `b968f03` + `c2f39db`, tag `taprelay-0.1.2`; site commit `dc17240`
- Installed on Aaron's Pixel 7 and smoke-tested

It contains: activation modes (Execute / Open item / Quick Controls) per item and per trigger,
capability-driven Quick Controls, Remote Mode (always-on face + Quick Settings tile), Places
(geofencing), controller rumble, and a preferences card. Full detail in `docs/QA-v0.1.2.md`.

---

## 2. What is half-finished: 0.1.3 "everything is free"

**Status: code complete, builds clean, 119 tests + lint pass, NOT staged / installed / published /
committed.**

Aaron's instruction: remove the paywall, because the Govee and Tuya developer APIs are licensed
for personal, non-commercial use and `dist/SITE_PUBLISH.md` already forbade paywalling.

### Changes made (all uncommitted)

App repo (`.gemini/antigravity/scratch/taprelay`):

| File | Change |
|---|---|
| `app/build.gradle.kts` | versionName `0.1.3`, versionCode `14` |
| `monetization/EntitlementRepository.kt` | `canAccess()` now returns `true` for every feature. Licence plumbing left intact but inert. |
| `ui/TapRelayViewModel.kt` | Removed the 5-tag free-tier limit from `saveTag()` |
| `ui/TapRelayApp.kt` | Removed the PRO badge, the "x / 5 Free Tags" quota, the Upgrade button, the FAB paywall gate, the "(Pro Feature)" multi-device card and the Pro gate on time-of-day conditions |
| `app/src/test/.../EntitlementRepositoryTest.kt` | `default_tier_is_free` replaced by `every_feature_is_free_without_a_licence`, asserting every feature is accessible with no licence |
| `CHANGELOG.md` | 0.1.3 entry added |

Site repo (`ARBH-Labs-Website`):

| File | Change |
|---|---|
| `public/taprelay/index.html` | The €1.99/mo Pro pricing card replaced with an "Everything included / €0 / NO PAYWALL" card that states the personal-use API reason. "Upgrade to Pro or activate your 7-day free trial" line replaced. Release history left untouched (it is accurate for the versions it describes). |

### ⚠️ Decide this before releasing 0.1.3

The tree also contains a **toolchain upgrade that was not made by this session** — almost
certainly Android Studio's AGP upgrade assistant:

- `gradle/libs.versions.toml`: AGP `8.6.0` → `8.13.2`
- `gradle/wrapper/gradle-wrapper.properties`: Gradle `8.7` → `8.13`
- `settings.gradle.kts`: adds the `foojay-resolver-convention` toolchain plugin
- `gradle/gradle-daemon-jvm.properties`: **new, untracked** — pins the daemon to a JetBrains JDK 21
  downloaded from foojay

The 0.1.3 build did pass on this toolchain (tests, lint, R8 release build), so it is not broken.
But it is a large unrelated change that would silently ride along in a release commit, and
`gradle-daemon-jvm.properties` conflicts with the project's rule of building on **Microsoft JDK
21** — it will pull a JetBrains JDK instead.

**Recommended:** revert the four toolchain files, ship 0.1.3 on the same toolchain that produced
0.1.2, and do the AGP upgrade as its own commit with its own verification.

```bash
cd /c/Users/Aaron/.gemini/antigravity/scratch/taprelay
git checkout -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties settings.gradle.kts
rm gradle/gradle-daemon-jvm.properties
```

### Steps to finish 0.1.3

```bash
cd /c/Users/Aaron/.gemini/antigravity/scratch/taprelay
JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.12.101-hotspot" ./gradlew.bat clean testReleaseUnitTest lintVitalRelease assembleRelease
cp app/build/outputs/apk/release/app-release.apk dist/TapRelay-v0.1.3.apk
cd dist && sha256sum TapRelay-v0.1.3.apk | tee TapRelay-v0.1.3.apk.sha256
```

1. Verify the signer is `CN=ARBH Labs, OU=TapRelay` (`apksigner verify --print-certs`).
2. `adb install -r dist/TapRelay-v0.1.3.apk`, confirm `versionName=0.1.3`, launch, no crash,
   tags/credentials intact. **No schema change in 0.1.3** (still 9), so the upgrade is trivial.
3. Publish (from `ARBH-Labs-Website`):
   `node tools/publish-release.mjs taprelay 0.1.3 <apk> --channel alpha --recommended --approve
   --version-code 14 --min-sdk 30 --target-sdk 35 --feature ... --guidance ...`
4. Add a 0.1.3 release card to `public/taprelay/index.html`, move the "Recommended Latest" badge
   off 0.1.2, update the two download links and the sideload step. Leave older cards alone.
5. `npx wrangler pages deploy public --project-name arbh-labs-website --branch main
   --commit-dirty=true`
6. Verify: download the live versioned and latest URLs and compare the full SHA-256.
7. Write `docs/QA-v0.1.3.md`, update `dist/SITE_PUBLISH.md`, commit both repos, tag
   `taprelay-0.1.3`.

---

## 3. Known gap, still open

The Quick Controls surface launched by tapping a **physical NFC tag while TapRelay is closed**
(`QuickControlsActivity`, started from `NfcTrampolineActivity`) has never been driven end to end.
It shares `QuickControlsContent` with the in-app panel, which is hardware-verified, and reuses the
translucent-activity pattern shipped since 0.1.0, so it is verified by construction only. `adb`
cannot launch a non-exported activity, so this needs a human: set a tag's activation mode to
Quick Controls, close the app, tap the tag.

---

## 4. Things learned the hard way (do not re-derive)

- **Gamepad input reaches only the focused window.** A Compose `Dialog` gets its own window, so a
  learning dialog never sees the pad — it just moves system focus. Capture UI must live in the
  activity's own window. This cost a whole debugging cycle.
- Services, `MediaSession` and non-focusable overlays receive no gamepad input at all. There is no
  compliant background controller listener on Android.
- Pixel 7 exposes only **60 Hz and 90 Hz** as app-selectable display modes; the 20–45 Hz values in
  `mSupportedRefreshRates` are system render rates, reached with Android 15's
  `View.setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_LOW)`.
- `WindowManager.LayoutParams.screenBrightness = 0f` is the panel's dimmest, not off.
- Sensibo: `remoteCapabilities.modes[mode].fanLevels` is authoritative. Aaron's Pure air purifier
  reports **only `low` and `high`** (plus `auto`) and a single `fan` mode. Never hardcode speeds.
- Geofences are dropped on reboot and on app replace — `BootReceiver` rebuilds them from the DB.
- Background location is a separate grant and on Android 11+ usually only obtainable from Settings.
- `adb` cannot start a non-exported activity.
