# TapRelay - site publish record

TapRelay is published as a **private alpha** on arbhlabs.com through the standard ARBH Labs
release pipeline. The disclaimer copy below is required on the download page and is live there.

## Current release - v0.2.1 (2026-09-05)

- `TapRelay-v0.2.1.apk` - signed release, SHA-256
  `8ec11701c80201a0ffe38a2b995c05d8335799ee18d605564c3d62c872d079c4`, 7,982,103 bytes,
  versionCode 17, Room schema **11**
- `TapRelay-v0.2.1.apk.sha256`, `../docs/QA-v0.2.1.md`, `../docs/RESEARCH_0.2.1.md`
- Magic Actions (one trigger runs a sequence of the owner's own actions, with pauses and partial-
  failure reporting); Home Assistant as a first-class `SmartHomeProvider` over the documented REST
  API; home-screen widgets (Action Key + Remote); a rebuilt always-on face driven by favourites;
  and three new target types (open app/link, Do Not Disturb, web request).
- Signed with the ARBH Labs TapRelay release key; certificate SHA-256
  `7ed03578928d3b8eaec35cf064154bee4d48f528b0021dd3dcd9f3fde73ba808`, which is the digest
  LastDose's `ExternalActionProvider` pins - so cross-app logging works with this build.
- **Still free and still paywall-free.** The entitlement boundary exists in code and
  `canAccess()` returns `true` for everything. See `../docs/RESEARCH_0.2.1.md` section 5.

Live URLs (all serve the exact bytes above, verified by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.2.1.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

0.1.5, 0.1.4, 0.1.2 and 0.1.0 all still resolve unchanged (HTTP 200, verified after this deploy).
The download page's release history gained a 0.1.5 card, which had been published to R2 but never
added to the page.

R2 objects in `arbh-releases`:
- `taprelay/releases/0.2.1/TapRelay-0.2.1.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

## Previous release - v0.1.5 (2026-09-04)

- `TapRelay-v0.1.5.apk` - signed release, SHA-256
  `fb72d9f5b6700e9604ece65badfd04451d1802b80bafff6668a0059f930bf4f3`, 7,747,955 bytes,
  versionCode 16, Room schema 10
- Fixes the edge swipe and system Back leaving TapRelay from a child screen.

## Previous release — v0.1.4 (2026-09-04)

- `TapRelay-v0.1.4.apk` — signed release, SHA-256
  `53c3ff7f58f07467f82a4df8b629441e08b29dafbe4f733a14274b1b10754ba1`, 7,747,951 bytes,
  versionCode 15, DB schema 10
- `TapRelay-v0.1.4.apk.sha256`, `../docs/QA-v0.1.4.md`
- Controller / NFC / place triggers can log straight into LastDose without LastDose opening, via
  LastDose 7.1.10's signature-pinned `ExternalActionProvider`. Analog trigger handling gains
  hysteresis, so one Xbox trigger pull is exactly one action. Rolls up the unreleased 0.1.3
  (paywall removed entirely).
- **Needs LastDose 7.1.10 or later for the logging link.** LastDose 7.1.10 is built and signed but
  NOT yet published (7.1.9 is still awaiting device validation and would ship with it).

Live URLs (all serve the exact bytes above, verified by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.1.4.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

R2 objects in `arbh-releases`:
- `taprelay/releases/0.1.4/TapRelay-0.1.4.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

> 0.1.3 (vc14) was built and tested but never published, so nothing was overwritten: 0.1.4 is the
> next published version and 0.1.2's bytes are untouched.

## Previous release — v0.1.2 (2026-09-04)

- `TapRelay-v0.1.2.apk` — signed release, SHA-256
  `4c3ac382f5ac4d55f8230f6ae24f3d6372a20cf59be994f2bbc58b9bc7a00021`, 7,715,111 bytes,
  versionCode 13, DB schema 9
- `TapRelay-v0.1.2.apk.sha256`, `../docs/QA-v0.1.2.md`
- Activation modes (Execute / Open item / Quick Controls) on every item and every trigger;
  capability-driven Quick Controls; Remote Mode always-on face with a Quick Settings tile;
  Places (geofenced routines); controller rumble and preferences. Fixes controller-button
  assignment (dialog window stole gamepad focus) and Sensibo fan levels that the unit does not have.

Live URLs (all serve the exact bytes above, verified by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.1.2.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

R2 objects in `arbh-releases`:
- `taprelay/releases/0.1.2/TapRelay-0.1.2.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

> 0.1.1 (vc12) was published before this work landed, so these changes shipped as 0.1.2 rather
> than overwriting bytes someone may already have downloaded.

## Previous release — v0.1.0 (2026-09-04)

- `TapRelay-v0.1.0.apk` — signed release, SHA-256
  `812e5ef5a421eee92bd282503f2aeb5f9772ef8830fc0b9cdbdbae0006c92837`, 6,839,178 bytes,
  versionCode 11
- `TapRelay-v0.1.0.apk.sha256`, `release-metadata.json`, `../docs/QA-v0.1.0.md`
- Colour polish: About screen now shows the real build version (was hard-coded to 0.0.8); rolls up 0.0.10 (brightness
  keeps the bulb colour on Smart Life, 22 colours + white-temperature slider). Gradients
  deferred (needs segmented hardware). No schema change.

Live URLs (all serve the exact bytes above, verify by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.1.0.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

R2 objects in `arbh-releases`:
- `taprelay/releases/0.1.0/TapRelay-0.1.0.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

### Prior releases
0.0.10 (vc10), 0.0.9 (vc9), 0.0.8 (vc8), 0.0.7 (vc7), 0.0.6 (vc6), 0.0.5 (vc5), 0.0.4 (vc4), 0.0.2 (vc2) — see git tags and `../docs/`.

## Publish steps (from the ARBH-Labs-Website repo root)

1. `node tools/publish-release.mjs taprelay <version> <apk> --channel alpha --recommended --approve
   --version-code <code> --min-sdk 30 --target-sdk 35 --feature ... --fix ... --issue ... --guidance ...`
   (uploads R2 objects and rewrites `public/data/releases.json` + `functions/utils/apk-releases.js`)
2. Update the version, size and SHA-256 shown on `public/taprelay/index.html`.
3. `npx wrangler pages deploy public --project-name arbh-labs-website --branch main --commit-dirty=true`
4. Download the live versioned and latest URLs and compare full SHA-256 against the local APK.

## Required disclaimer copy (kept on the download page)

> **TapRelay — Alpha / Internal Test.** Early software; features may change or break. TapRelay
> lets you control your own Govee and Smart Life devices using personal credentials you generate
> yourself — a key from the Govee Home app, and an Access ID and Secret from your own Tuya IoT
> cloud project. Both developer APIs are licensed for personal, non-commercial use. TapRelay is
> free, is **not affiliated with, sponsored by, or endorsed by Govee, Tuya, Smart Life or Google**,
> and does not resell or proxy any provider service. Your credentials are stored encrypted on your
> device only. Google Home and Sensibo are not supported.

## Do NOT
- charge for it, gate it behind a paywall, or bundle it with a paid product (breaks the personal /
  non-commercial terms of both the Govee and Tuya developer APIs)
- claim a Govee, Tuya, Smart Life or Google partnership or certification
- host it without the disclaimer above
