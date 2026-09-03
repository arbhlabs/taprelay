# TapRelay v0.0.2 — site publish (owner action)

This build is ready to host if you decide the Govee-terms risk is acceptable for a **free,
clearly-disclaimed alpha**. I did not deploy it: there are no Cloudflare/R2 credentials in this
environment, and the Govee developer API is personal / non-profit only, so publishing is your call.

## Artifacts in this folder
- `TapRelay-v0.0.2.apk` — signed release, SHA-256 `f8fb132fe3aebf869ac223642e0a0f37a0b09d366eb1cd39e3448c2c7b2ecf68`
- `TapRelay-v0.0.2.apk.sha256`
- `release-metadata.json`
- screenshots: `screenshot-home.png`, `screenshot-addtag.png`, `screenshot-about.png`

## If you publish (mirrors the LastDose/PulseTrace flow)
1. Copy `TapRelay-v0.0.2.apk` into `ARBH-Labs-Website/releases/`.
2. Upload to R2 bucket `arbh-releases` (binding `RELEASE_BUCKET`) at key
   `taprelay/TapRelay-v0.0.2.apk` via `wrangler r2 object put` (needs your CF auth).
3. Add a TapRelay entry to `public/data/releases.json` and a card on `public/releases/index.html`.
4. Deploy Pages, then verify the public URL downloads the APK and the SHA-256 matches.

## Required disclaimer copy (put on the download page)

> **TapRelay v0.0.2 — Alpha / Internal Test.** Early software; features may change or break.
> TapRelay lets you control your own Govee devices using a personal key you generate in the
> Govee Home app. Govee's developer API is licensed for personal, non-commercial use. TapRelay
> is free, is **not affiliated with, sponsored by, or endorsed by Govee or Google**, and does not
> resell or proxy any provider service. Your key is stored encrypted on your device only.
> Google Home is not supported in this build.

## Do NOT
- charge for it, gate it behind a paywall, or bundle it with a paid product (breaks Govee's terms)
- claim Govee/Google partnership or certification
- host it without the disclaimer above
