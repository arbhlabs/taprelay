# TapRelay

**An NFC sticker, or a spare controller button, as a physical smart-home switch.**

TapRelay turns an ordinary NFC sticker — or a button on a game controller, a home-screen widget, or an always-on face — into a physical switch for Govee, Smart Life (Tuya), Sensibo and your own Home Assistant. No Tasker profile, no webhook plumbing, no server.

## Download

Signed APKs are attached to every [release](../../releases). You can also get the current build
straight from the site: **[arbhlabs.com/taprelay/](https://arbhlabs.com/taprelay/)**

| | |
|---|---|
| Current version | **0.6.32** (build 68) |
| Band app | **[1.16.12](../../releases/tag/band-v1.16.12)** for Xiaomi Smart Band 9 |
| Windows helper | **PC Relay 1.0.2**, Windows x64 |
| Requires | Android 11+ (API 30) |
| Package | `com.arbhlabs.taprelay` |
| Size | 9,150,873 bytes |
| SHA-256 | `c3c0318c1edcfb11842b52676364741aa09c1a1ebe818ebd1119572416978574` |

## What it does

- **Map a game controller button** to your lights — press it mid-game, no phone, no menus.
- **Magic Actions** run several steps from one press: lights off, air down, phone quiet.
- **One tag, several brands** — mix Govee, Smart Life and Sensibo devices on a single tap.
- **Widgets, a quick-settings tile and an always-on face** for the same actions.
- **The tag holds only an anonymous code.** Device IDs and API keys stay in the phone's KeyStore.

- **Mouse mode** turns your phone or Xiaomi Smart Band 9 into a touchpad for your paired Windows PC: move, click and scroll. Update PC Relay to 1.0.2, then open **Windows PC → Mouse control** on your phone.

Band mouse mode: swipe slowly for precision or quickly to move farther. Tap to click, or use Left, Right and Scroll. The [Band follow-up manifest](releases/band-v1.16.12.json) records the current RPK; the original phone release manifest stays unchanged.

## About this repository

**TapRelay is closed source.** This repository is not the source code — it hosts the signed release
binaries, the changelog and the issue tracker, so that the app has a verifiable home on a domain
people already trust and so tools like [Obtainium](https://github.com/ImranR98/Obtainium) can track
updates. Release tags identify distribution metadata; [the release manifest](releases/v0.6.32.json) records the exact separate application source checkpoints and artifact hashes.

If that is a dealbreaker for you, that is a completely reasonable position and there are good
open-source alternatives in this space. This is stated plainly here rather than left for you to
work out.

## Verifying what you downloaded

The Android APK and Band RPK use the same existing signing key. Check the APK before installing:

```bash
# the file matches what was published
sha256sum TapRelay-0.6.32.apk

# and it was signed by us
apksigner verify --print-certs TapRelay-0.6.32.apk
```

Expected signer:

```
CN=ARBH Labs, OU=TapRelay, O=ARBH Labs
SHA-256 digest: 7ed03578928d3b8eaec35cf064154bee4d48f528b0021dd3dcd9f3fde73ba808
```

If the signing certificate ever differs from the fingerprint above, **do not install it** and please
open an issue.

## Installing

Android will ask you to allow installation from this source the first time. Newer Android versions
add a short wait before permitting an install from an unverified developer — that is a platform
behaviour, not something the app controls.

## Issues and feedback

Bug reports and feature requests are welcome in [Issues](../../issues). Please include your Android
version, device model, and the app version from the About screen.

## Notes

**Every TapRelay feature is free**, including Home Assistant targets and web actions. No subscription, licence key or trial is needed.

Not affiliated with Govee, Tuya, Sensibo or Google.

---

Built by [ARBH Labs](https://arbhlabs.com/).
