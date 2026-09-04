# TapRelay v0.1.0 - QA summary

`TapRelay-v0.1.0.apk`, versionName `0.1.0`, versionCode `11`, 6,839,178 bytes,
SHA-256 `812e5ef5a421eee92bd282503f2aeb5f9772ef8830fc0b9cdbdbae0006c92837`,
signed with the ARBH Labs TapRelay key (cert SHA-256
`7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`, APK Signature Scheme v2).
Date 2026-09-04. No Room schema change.

## What changed vs 0.0.10

- **Version bumped to 0.1.0** (first 0.1 tag). Rolls up 0.0.10 unchanged (brightness keeps the
  bulb's colour on Smart Life; 22-colour palette + warm-to-cool white slider).
- **About TapRelay** showed a hard-coded "version 0.0.8". It now reads
  `BuildConfig.VERSION_NAME`, so it always matches the installed build and cannot drift again.
  `buildFeatures { buildConfig = true }` was enabled to expose it.

## Verification

| Check | Result | Grade |
|---|---|---|
| `./gradlew testReleaseUnitTest` | 71 tests, 0 failures | VERIFIED |
| `./gradlew assembleRelease` (R8 + shrink, signed) | BUILD SUCCESSFUL | VERIFIED |
| APK signature | v2, cert SHA-256 unchanged from every prior release | VERIFIED |
| APK identity | `versionCode 11 versionName 0.1.0` (aapt2 badging) | VERIFIED |
| Installed on Pixel 7 (`adb install -r` over 0.0.8) | Success; on-device base.apk SHA-256 == released artifact; launches to MainActivity, no crash | VERIFIED |
| About dialog now shows 0.1.0 | BuildConfig-sourced; confirmed by the badging match | CODE-PATH VERIFIED |
| In-place upgrade keeps tags/credentials | no schema change | CODE-PATH VERIFIED |
| Brightness-keeps-colour and palette on real hardware | unchanged from 0.0.10; not re-run | NOT PHYSICALLY VERIFIED |

## Security review

No new network calls, permissions, or logging. `BuildConfig` exposes only the version name and
code, both already public in the APK manifest.
