# TapRelay

**An NFC sticker, or a spare controller button, as a physical smart-home switch.**

TapRelay turns an ordinary NFC sticker — or a button on a game controller, a home-screen widget, or an always-on face — into a physical switch for Govee, Smart Life (Tuya), Sensibo and your own Home Assistant. No Tasker profile, no webhook plumbing, no server.

## Download

Signed APKs are attached to every [release](../../releases). You can also get the current build
straight from the site: **[arbhlabs.com/taprelay/](https://arbhlabs.com/taprelay/)**

| | |
|---|---|
| Current version | **0.3.0** (build 18) |
| Requires | Android 11+ (API 30) |
| Package | `com.arbhlabs.taprelay` |
| Size | 7,982,103 bytes |
| SHA-256 | `64439c2db905d0e6aaa14a4c2056d3ff4c07470b9fdbe16171dcfe614771c8ff` |

## What it does

- **Map a game controller button** to your lights — press it mid-game, no phone, no menus.
- **Magic Actions** run several steps from one press: lights off, air down, phone quiet.
- **One tag, several brands** — mix Govee, Smart Life and Sensibo devices on a single tap.
- **Widgets, a quick-settings tile and an always-on face** for the same actions.
- **The tag holds only an anonymous code.** Device IDs and API keys stay in the phone's KeyStore.

## About this repository

**TapRelay is closed source.** This repository is not the source code — it hosts the signed release
binaries, the changelog and the issue tracker, so that the app has a verifiable home on a domain
people already trust and so tools like [Obtainium](https://github.com/ImranR98/Obtainium) can track
updates.

If that is a dealbreaker for you, that is a completely reasonable position and there are good
open-source alternatives in this space. This is stated plainly here rather than left for you to
work out.

## Verifying what you downloaded

Every release is signed with the same key. Check the APK before installing:

```bash
# the file matches what was published
sha256sum TapRelay-0.3.0.apk

# and it was signed by us
apksigner verify --print-certs TapRelay-0.3.0.apk
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

**Govee, Smart Life and Sensibo control is free, permanently, in every tier.** Their developer APIs are licensed for personal, non-commercial use, so nothing that reaches a vendor cloud sits behind the paywall.

**TapRelay Pro (€7.99 once)** unlocks the two things that do not: Home Assistant targets and raw web actions. A 7-day trial runs on device with no card and no account.

Not affiliated with Govee, Tuya, Sensibo or Google.

---

Built by [ARBH Labs](https://arbhlabs.com/).
