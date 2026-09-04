# TapRelay v0.0.9 - QA summary

`TapRelay-v0.0.9.apk`, versionName `0.0.9`, versionCode `9`, 6,822,794 bytes,
SHA-256 `1acb95d8db2c324c5c856b56e7e7aac05912de4ed840fdd1a5a879a7fefcdf66`, signed with the
ARBH Labs TapRelay key (cert SHA-256 `7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`,
APK Signature Scheme v2). Date 2026-09-04.

## What was wrong

Two things quietly did the wrong thing. Both were found by reading the code, not by a crash.

1. **Stale Smart Life count on the home screen.** `TapRelayViewModel.ui` combined only five flows
   and read `tuyaDeviceCount` / `tuyaSceneCount` with `.value` inside the transform. `refreshConnections()`
   sets `tuyaConnected = true` *before* the discovery network calls return, so the combine emitted
   with the counts still at 0 and never re-emitted when they landed. The card stuck at
   "Connected - 0 devices - 0 scenes". Govee's count was already wired through the combine, so it
   was correct - the two providers behaved differently.

2. **"Disconnect Smart Life" did not stick.** `disconnectTuya()` cleared the encrypted credential
   store but `TuyaProvider.inMemoryCreds` kept a decrypted copy. `isConnected()` returns
   `inMemoryCreds != null || store...`, so it still reported connected, and the next
   `refreshConnections()` flipped the UI back to connected. The provider stayed usable with stale
   credentials until the process was killed.

## Fixes

- `ui` now folds all five connection signals (`goveeConnected`, `goveeDeviceCount`,
  `tuyaConnected`, `tuyaDeviceCount`, `tuyaSceneCount`) through a nested `combine`, so any count
  that arrives after the connected flag still refreshes the screen. `refreshConnections()` also
  sets the counts before the connected flag for both providers.
- New `TuyaProvider.clearCredentials()` nulls the in-memory cache; `disconnectTuya()` calls it
  alongside clearing the store. `isConnected()` then returns false immediately.

No schema change, no manifest change, no dependency change. Power/colour/brightness routing,
NFC read/write, and the group logic are untouched from 0.0.8.

## Verification

| Check | Result | Grade |
|---|---|---|
| `./gradlew testReleaseUnitTest` | 64 tests, 0 failures | VERIFIED |
| `./gradlew assembleRelease` (R8 + resource shrink, signed) | BUILD SUCCESSFUL | VERIFIED |
| APK signature | v2, cert SHA-256 matches every prior release | VERIFIED |
| APK identity | `package com.arbhlabs.taprelay versionCode 9 versionName 0.0.9` (aapt2 badging) | VERIFIED |
| `TuyaProvider.clearCredentials()` drops the connection | asserted in `TuyaProviderTest` | CODE-PATH VERIFIED |
| Home card refreshes when the Smart Life count lands | reasoned from the new `combine` wiring | CODE-PATH VERIFIED |
| In-place upgrade from 0.0.8 keeps tags/creds | Room schema and entity unchanged, vc 9 > 8 | CODE-PATH VERIFIED |
| The two fixes on real hardware | not run this cycle | NOT PHYSICALLY VERIFIED |
| Rendered hue/level at the bulb; Tuya scenes; cold-start latency; App Link autoVerify | unchanged from 0.0.8 | NOT PHYSICALLY VERIFIED |

## Security review of this change

No new network calls, no new permissions, no new logging. `clearCredentials()` only nulls a
field. No credential, token or `localKey` is written to the log by any changed line.
