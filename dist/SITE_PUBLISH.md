# TapRelay — site publish record

TapRelay is published as a **private alpha** on arbhlabs.com through the standard ARBH Labs
release pipeline. The disclaimer copy below is required on the download page and is live there.

## Current release — v0.0.4 (2026-09-04)

- `TapRelay-v0.0.4.apk` — signed release, SHA-256
  `026e57e485da904214f7ecd1c5568916ad27f186a8801cf518add8915ef98772`, 6,757,226 bytes
- `TapRelay-v0.0.4.apk.sha256`, `release-metadata.json`, `../docs/QA-v0.0.4.md`
- Screenshots: `screenshot-home.png`, `screenshot-addtag.png`, `screenshot-about.png`,
  `screenshot-devices-v0.0.4.png`

Live URLs (all serve the exact bytes above, verified by download):
- https://arbhlabs.com/taprelay/
- https://arbhlabs.com/downloads/TapRelay-0.0.4.apk
- https://arbhlabs.com/downloads/TapRelay.apk
- https://arbhlabs.com/downloads/TapRelay-latest.apk

R2 objects in `arbh-releases`:
- `taprelay/releases/0.0.4/TapRelay-0.0.4.apk` (immutable)
- `taprelay/latest/TapRelay.apk`, `taprelay/latest/TapRelay-latest.apk` (aliases)

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
