# TapRelay v0.0.1 — QA Summary

Date: 2026-09-03
Build: release, minified (R8), signed with `taprelay` release key
APK SHA-256: `005dacde338470c89fa08d917ca8d3bc9e30d88c3be617735796d54daea32e19`
Primary device: Google Pixel 7 (`panther`), Android 16 preview (SDK_INT 37), serial 29151FDH2008P1

## Automated tests — PASS (17/17)

`./gradlew :app:testDebugUnitTest`

| Suite | Tests | Result |
| --- | --- | --- |
| NfcPayloadParserTest | 8 | PASS — valid App Link parse; rejects wrong host, http scheme, non-v4 UUID, SQL-injection payload, path traversal, null/blank; generated id round-trips |
| ToggleLogicTest | 4 | PASS — Turn On / Turn Off / Toggle target computation + authoritative inverse |
| GoveeApiClientTest | 4 | PASS — device parse + power-capability filter; 401→reconnect, 429→rate-limit, 500→server, all as human errors (MockEngine) |
| ErrorCopyTest | 1 | PASS — every `TapError` message scanned for 18 banned jargon terms |

## Manual QA on Pixel 7

| Area | Result |
| --- | --- |
| Clean install (release APK) | PASS — installs, no crash |
| Cold launch → onboarding screen | PASS — renders, dark/OLED theme, edge-to-edge, back gesture OK |
| "Get started" → Connect Govee screen | PASS |
| Govee connect with invalid key (live network) | PASS — HTTP 401 surfaced as "Your Govee connection needs to be reconnected." |
| No secrets in logcat | PASS — app logs nothing; key never printed |
| `pm clear` resets to first-run | PASS |
| Release signature (`apksigner verify`) | PASS — v1+v2+v3, CN=ARBH Labs / OU=TapRelay |

## NOT VERIFIED (no hardware / credentials available this run)

- Physical NFC write to a blank NTAG213/215/216 sticker
- Scan of a programmed tag: foreground, background, cold-start, app-killed, reboot
- Read-only tag / malformed tag / tag-moved-away / NFC-disabled paths (code paths exist and are unit-guarded)
- Govee device discovery with a **valid** key
- Govee H6003 turn on / off / toggle round-trip and real-state reconciliation
- Rate-limit (429) behaviour against the live API
- App Link auto-verification (needs `.well-known/assetlinks.json` hosted at taprelay.app)
- Haptic differentiation on the physical vibrator

## Notes / follow-ups

- `taprelay.app` domain is not owned/hosted — App Link `autoVerify` will not succeed until
  `assetlinks.json` (SHA-256 `7E:D0:35:78:92:8D:3B:8E:AE:C3:5C:F0:64:15:4B:EE:4D:48:F5:28:B0:02:1D:D3:DC:D9:F3:FD:E7:3B:A8:08`)
  is published. Until then the NDEF_DISCOVERED intent filter still routes taps to TapRelay when
  the app is installed.
