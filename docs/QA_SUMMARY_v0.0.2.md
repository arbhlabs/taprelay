# TapRelay v0.0.2 — QA Summary

Date: 2026-09-03
Build: release, R8-minified, signed with the `taprelay` release key
APK SHA-256: `f8fb132fe3aebf869ac223642e0a0f37a0b09d366eb1cd39e3448c2c7b2ecf68`
Device: Google Pixel 7 (`panther`), serial 29151FDH2008P1

## Automated tests — PASS (17/17)
NfcPayloadParserTest 8 · ToggleLogicTest 4 · GoveeApiClientTest 4 · ErrorCopyTest 1

## Physical Pixel 7 acceptance — PASS
| Step | Result |
| --- | --- |
| Upgrade install over v0.0.1 (data preserved) | PASS |
| Add Tag → hold a real blank writable NFC sticker | PASS — Detected → "Setting up your sticker…" → advanced automatically |
| Select Govee "Desk Lamp", select Toggle, Save | PASS |
| Exit app, tap the sticker | PASS — lamp toggled, pill `Desk Lamp • On/Off` |
| Tap the sticker again | PASS — lamp toggled back |
| Edit action on the same tag without re-tapping the sticker | PASS |
| Rapid repeated taps | PASS — single fire (1.5 s debounce) |
| Foreground scan | PASS |
| Background scan (trampoline) | PASS |
| About disclaimer dialog | PASS |
| NFC-off banner | shown when NFC disabled (verified in code path; NFC left on for the lamp tests) |

Confirmed by device owner: "EVERYTHING WORKS ... one pass".

## NOT VERIFIED
- Cold-start-from-killed scan (functional; latency not measured)
- Read-only / locked tag path (`This NFC sticker can't be written.`)
- App Link `autoVerify` — needs `assetlinks.json` hosted at taprelay.app (cert SHA-256
  `7E:D0:35:78:92:8D:3B:8E:AE:C3:5C:F0:64:15:4B:EE:4D:48:F5:28:B0:02:1D:D3:DC:D9:F3:FD:E7:3B:A8:08`)
- Govee 429 rate-limit against the live API

## Not done (out of scope this pass)
Website / R2 deploy — no Cloudflare credentials in environment; see `dist/SITE_PUBLISH.md`.
