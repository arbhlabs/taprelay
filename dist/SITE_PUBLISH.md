# TapRelay — site publish record

TapRelay is published as a **private alpha** on arbhlabs.com through the standard ARBH Labs
release pipeline. The disclaimer copy below is required on the download page and is live there.

## Current release — v0.0.10 (2026-09-04)

- `TapRelay-v0.0.10.apk` — signed release, SHA-256
  `bf8fe48b8a671fc74aa5303ddd171d227f3357f80c7dffdc08208b2b1f93488a`, 6,839,182 bytes,
  versionCode 10
- `TapRelay-v0.0.10.apk.sha256`, `release-metadata.json`, `../docs/QA-v0.0.10.md`
- Colour polish: brightness-only tags no longer knock Smart Life bulbs to white / wipe the
  colour set elsewhere; 22 spectrum colours + a Whites tab with an 1800–6500 K slider. Gradients
  deferred (needs segmented hardware). No schema change.

Live URLs (all serve the exact bytes above, verify by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.0.10.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

R2 objects in `arbh-releases`:
- `taprelay/releases/0.0.10/TapRelay-0.0.10.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

### Prior releases
0.0.9 (vc9), 0.0.8 (vc8), 0.0.7 (vc7), 0.0.6 (vc6), 0.0.5 (vc5), 0.0.4 (vc4), 0.0.2 (vc2) — see git tags and `../docs/`.

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
