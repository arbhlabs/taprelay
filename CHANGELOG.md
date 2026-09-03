# TapRelay Changelog

## v0.0.4 — 2026-09-04 (Alpha / Internal Test)

Real Smart Life / Tuya support. Verified end to end against a live Tuya Central Europe
cloud project on a Pixel 7 — no mock or demo devices exist anywhere in the app.

### Added
- **Smart Life (Tuya) provider.** Connect with your own Tuya IoT Core Access ID and Access
  Secret; the credentials are encrypted on device exactly like the Govee key.
- Devices are discovered through the current *Query Devices in Project* endpoint
  (`GET /v2.0/cloud/thing/device`) and appear under the friendly names you gave them in the
  Smart Life app, with their live online state.
- Tags can now target either a device or a scene, and the picker mixes Govee and Smart Life
  devices in one list.

### Fixed
- **Smart Life discovery reported 0 devices.** The project response was being decoded with the
  legacy device schema, which has none of the fields the project endpoint actually returns.
  The response shape is now detected before decoding.
- **Devices showed their generic product name.** The account label lives in `customName`, not
  `name`; `name` is the manufacturer's product string ("Candle Lamp W505Z2 3").
- **Online state was always reported as online.** The project endpoint spells it `isOnline`.
- `page_size` above 20 is rejected by Tuya with `40000904 param size too much`; discovery now
  requests a page the API accepts.
- Tuya request signing includes the uppercase method, the SHA-256 body hash, the empty
  signed-header section and the full path with query string.
- **Toggle now reads authoritative state before acting.** It fetches the device's live data
  points, picks the real boolean power point (`switch_led` on these lamps rather than a guessed
  `switch_1`), sends the inverse, and only writes the local last-known state after the command
  succeeds.
- Diagnostics no longer log Tuya response bodies, which contain per-device `localKey` secrets.

### Unchanged / still true
- The physical sticker stores only the opaque `https://taprelay.app/t/<uuid>` App Link. The
  mapping stays in the local database and stays editable without re-tapping the sticker.
- Govee is untouched and was regression-tested on real hardware in this release.
- Private alpha. TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya,
  Smart Life or Google.

## v0.0.2 — 2026-09-03 (Alpha / Internal Test)

Real-hardware completion pass. The full loop now works end-to-end on a Pixel 7:
provision a blank sticker → pick a Govee device + Toggle → tap sticker → light toggles → tap again → toggles back.

### Fixed
- **Add Tag could not provision a normal blank sticker** — it showed "This tag isn't a
  TapRelay tag." The reader was started with `FLAG_READER_SKIP_NDEF_CHECK`, which left the
  `Ndef` / `NdefFormatable` techs off the tag so the writer had nothing to write to. The flag
  is removed; blank and unformatted stickers now provision on the first tap.
- Provisioning is now a real state machine that separates *scan mode* from *provisioning mode*:
  a non-TapRelay tag is only an error in normal scanning; in Add Tag it is a candidate.

### Added
- **Add Tag scan states**: Ready (pulsing NFC target) → Detected → Setting up your sticker… →
  advances automatically. Errors show a plain message + a Try again button (no more stuck error).
- **Already-has-data stickers**: "This sticker already contains data. TapRelay can replace it."
  with **Use this tag / Cancel** — nothing is overwritten without confirmation.
- **Already-a-TapRelay-tag**: jumps straight to its configuration (pre-filled if a mapping exists).
- **Read-only / unsupported stickers**: "This NFC sticker can't be written."
- Write is **verified by read-back** where the tag allows it.
- **NFC-off banner** on the home screen and in Add Tag when NFC is disabled.
- **About TapRelay** dialog (overflow menu) with the alpha + Govee non-affiliation disclaimer.
- **Disconnect Govee** in the overflow menu.

### Unchanged / still true
- Physical tag stores only the opaque `https://taprelay.app/t/<uuid>` App Link. Mapping is local
  and editable without re-tapping the sticker.
- Govee: personal / non-profit API terms, Bring-Your-Own-Key, private alpha, no partnership claimed.
- Google Home: provider boundary only, not enabled.
- Govee LAN / Matter: not implemented.

## v0.0.1 — 2026-09-03 (Alpha / Internal Test)

First build. Onboarding, Govee connect + discovery, Add Tag wizard, stateless tags, scan-to-run
with optimistic toggle + rollback, tag management, Tink-encrypted key storage, human error copy,
reader-mode NFC, translucent trampoline scan path. 17 unit tests. Signed release.
