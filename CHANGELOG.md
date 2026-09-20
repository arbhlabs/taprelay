# Changelog

All published releases of TapRelay. Binaries are attached to each
[GitHub release](../../releases) and mirrored at
[arbhlabs.com/taprelay/](https://arbhlabs.com/taprelay/).

Published artifacts are immutable: a version already released is never rebuilt or replaced.

## 0.6.32 — 2026-09-20

TapRelay 0.6.32 (build 68), Xiaomi Smart Band 9 app 1.16.10 (build 84), and Windows PC Relay 1.0.2.

- Control your paired Windows PC from a phone touchpad or mouse mode on the band: move, left/right click, and scroll.
- Update the existing Windows helper to 1.0.2. Your saved pairing stays in place.
- Mouse input stops when you leave its screen or the connection expires. Phone and band take turns controlling the PC.
- Band 1.16.10 keeps touch input responsive and checks the current PC pairing through your phone, preventing a stale pairing warning on the band.

On the phone, open **Windows PC → Mouse control**. Install the band app through Notify for Xiaomi. All TapRelay features remain free.

Downloads are the exact verified artifacts; compare them with SHA256SUMS.txt. The APK and RPK retain the existing ARBH Labs signing certificate. The Windows helper is a self-contained x64 executable.

This repository and release tag contain distribution metadata, not application source. `release-manifest.json` records the exact Android and Band source commits and the Windows source baseline plus mouse patch. Windows 1.0.2 retains the previously installed helper's UI and pairing implementation.

## 0.3.0 — 2026-09-05

Build 18 · Android 11+ (API 30) · SHA-256 `64439c2db905d0e6aaa14a4c2056d3ff4c07470b9fdbe16171dcfe614771c8ff`

See the release notes on the [releases page](../../releases).
