# TapRelay v0.0.4 — QA summary

- **Build**: `TapRelay-v0.0.4.apk`, versionName `0.0.4`, versionCode `4`, applicationId
  `com.arbhlabs.taprelay`, R8 minified + resource shrinking, signed with the dedicated ARBH Labs
  TapRelay release key.
- **SHA-256**: `026e57e485da904214f7ecd1c5568916ad27f186a8801cf518add8915ef98772`
- **Size**: 6,757,226 bytes
- **Signing certificate SHA-256**: `7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`
  (identical to v0.0.2, so in-place upgrade is valid). Verified using APK Signature Scheme v2.
- **Device**: Pixel 7 (`panther`), Android 16, ADB over Wi-Fi.
- **Date**: 2026-09-04.

All results below are from the real hardware and the real Tuya and Govee clouds. Nothing in this
document was produced by a mock.

## Environment

Tuya IoT Core cloud project "TapRelay", Central Europe data centre, linked to the Smart Life app
account by Automatic Link. Three lamps in the account. Govee account with three lights.

## Discovery

| Check | Result |
|---|---|
| Tuya authentication | Connected. `/v1.0/token?grant_type=1` succeeds against `openapi.tuyaeu.com`. |
| Devices discovered | 3 — **Corner Lamp** (online), **Accent Lamp Bottom** (offline), **Accent Light Top** (offline). |
| Endpoint used | `GET /v2.0/cloud/thing/device?page_size=20` — confirmed in the device log. |
| Names shown | The account's own labels, not the manufacturer product string. |
| Govee discovery | 3 — Desk Lamp, TV Lamp, Aaron's Room, all online. |
| Mock / demo data | None. No mock, demo, fake or sample device or scene exists in `app/src/main`. |

Screenshot evidence of the combined picker: `dist/screenshot-devices-v0.0.4.png`.

## Control — Corner Lamp (Tuya, `switch_led`)

| Step | Observed | Result |
|---|---|---|
| Initial state | `switch_led = false` (off) | — |
| Turn On | `ok provider=tuya_cloud action=TURN_ON state=1` | Lamp on |
| Turn Off | `ok provider=tuya_cloud action=TURN_OFF state=0` | Lamp off |
| Toggle #1 | `toggle read=0 sending=1` then `ok ... state=1` | Lamp on |
| Toggle #2 | `toggle read=1 sending=0` then `ok ... state=0` | Lamp off |

Toggle reads `/v1.0/devices/{id}/status`, selects the real boolean power data point, sends the
inverse, and only writes the local last-known state after the command is acknowledged.

## NFC — two consecutive physical scans

Sticker mapped to **Corner Lamp → Toggle**. The sticker was *not* rewritten; only the local
mapping was edited.

| Scan | Log | Lamp |
|---|---|---|
| 1 | `toggle provider=tuya_cloud read=0 sending=1` → `ok ... state=1` | On |
| 2 | `toggle provider=tuya_cloud read=1 sending=0` → `ok ... state=0` | Off |

Confirmed visually by the owner. Both scans ran against the exact APK published as v0.0.4
(the on-device `base.apk` SHA-256 matches the released artifact byte for byte).

## Govee regression

| Step | Log | Result |
|---|---|---|
| Toggle on | `toggle provider=govee_cloud read=0 sending=1` → `ok ... state=1` | Pass |
| Toggle off | `toggle provider=govee_cloud read=1 sending=0` → `ok ... state=0` | Pass |

The Govee credential and all existing mappings were preserved throughout; the light was returned
to the state it started in.

## Upgrade

`adb install -r` over the installed build. Tag mappings, provider credentials and onboarding state
all survived. Package reports versionName `0.0.4` / versionCode `4`.

## Automated tests

`./gradlew testReleaseUnitTest` — **37 tests, 0 failures**, including a new
`TuyaProjectDiscoveryTest` built from the response body the live Tuya cloud actually returned:
friendly-name selection, online-state mapping, power data-point resolution, and graceful handling
of the `40000904 param size too much` page-size rejection.

## Not verified

- Control of **Accent Lamp Bottom** and **Accent Light Top** — both were offline in the Tuya
  account for the whole session, so no command could be confirmed at the bulb.
- Tuya **scenes** — the project has no scene subscription, so the scene list is empty.
- Cold-start-from-killed scan latency (functional, not timed).
- App Link `autoVerify` (needs `assetlinks.json` on taprelay.app).

## Security review of this change

- No credential, token, signature or Tuya `localKey` is written to the log. An earlier diagnostic
  build did log raw response bodies; that was removed before the release build because those
  bodies carry per-device `localKey` secrets.
- Diagnostic logging that remains records only endpoint paths, API error codes, device counts, and
  toggle state transitions.
