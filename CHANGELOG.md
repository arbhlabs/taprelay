# TapRelay Changelog

## v0.0.1 — 2026-09-03 (Alpha / Internal Test)

First build. TapRelay turns ordinary NFC stickers into physical smart-home buttons.

### Added
- **First-run onboarding** — one screen, plain language, no slideshow.
- **Connect Govee** — paste a personal Govee key from the Govee Home app; TapRelay validates it
  and discovers your controllable lights automatically. No device IDs, MAC addresses or JSON.
- **Add Tag wizard** — hold a blank NFC sticker to the phone, pick a device by friendly name,
  pick Toggle / Turn On / Turn Off, name it, choose an icon, save.
- **Stateless tags** — the sticker only ever stores an opaque `https://taprelay.app/t/<uuid>`
  App Link. The device, action and name live on the phone and can be changed later without
  re-tapping the sticker.
- **Scan to run** — tapping a programmed sticker fires an instant haptic and a top-of-screen
  pill (`Desk Lamp • On`). Works from the home screen or any other app via a translucent
  trampoline activity.
- **Optimistic Toggle** — perceived response under ~150 ms; for Toggle the real device state is
  queried in the background and reconciled, with automatic rollback on failure.
- **Tag management** — rename, enable/disable, change device or action, test action, delete.
- **Human error copy** — every failure is plain language (offline, needs reconnecting, device
  not responding, tag not set up, tag moved too quickly, …). No error codes, no jargon.
- **Encrypted key storage** — Govee key held in a Jetpack DataStore encrypted with Google Tink
  (AES-256-GCM), master key in the Android Keystore. Excluded from cloud backup and device transfer.
- **Reader-mode NFC** — `NfcAdapter.enableReaderMode` with platform sounds suppressed and a
  presence-check delay; duplicate scans within 1.5 s are debounced.

### Known limitations
- **Govee**: personal / non-profit API terms only — shipped as a private alpha under
  Bring-Your-Own-Key. No Govee partnership or approval is claimed.
- **Google Home**: not enabled. The provider boundary and UX state exist; the integration
  requires provider access from Google that is not available for this alpha.
- **Govee LAN / Matter**: not implemented in v0.0.1 (cloud OpenAPI only).
- The scan pill in the trampoline path is a lightweight Compose overlay, not the full
  "dynamic island" treatment.
