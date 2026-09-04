# TapRelay v0.0.10 - QA summary

`TapRelay-v0.0.10.apk`, versionName `0.0.10`, versionCode `10`, 6,839,182 bytes,
SHA-256 `bf8fe48b8a671fc74aa5303ddd171d227f3357f80c7dffdc08208b2b1f93488a`, signed with the
ARBH Labs TapRelay key (cert SHA-256 `7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`,
APK Signature Scheme v2). Date 2026-09-04. No Room schema change.

## What the owner reported

1. "When I set only a brightness toggle for a light it doesn't always stick and will change the
   colour I set in Google Home."
2. "I need way more colour options."
3. "Gradients."

## What changed

### 1. Brightness no longer steals the colour (Smart Life / Tuya)

`TuyaProvider.setBrightness` used to send `work_mode = white` plus `bright_value`. On a bulb that
was showing a colour, that flipped it out of colour mode entirely — the colour set from Google
Home or the Smart Life app was gone, and the "brightness didn't stick" symptom was the mode flip
fighting the brightness write.

Now it reads the live `work_mode` first:
- **colour mode** -> re-send `colour_data*` with the bulb's current `h` and `s` untouched and only
  a new `v` (the value channel is what brightness means in colour mode). No `work_mode`, no
  `bright_value`.
- **white mode / no colour mode** -> set `bright_value*` only. No `work_mode`.

Govee was already correct here: its `range/brightness` capability is independent of
`color_setting`, so a Govee brightness command never touched colour. No change needed there;
left as-is.

### 2. Bigger colour palette + white temperature

- `LightPresets.COLORS`: 22 named colours around the full wheel.
- `LightPresets.WHITES`: six white points (Candlelight 2000 K -> Daylight 6500 K), generated on
  the black-body curve.
- A **Whites** tab in the colour step with a 1800-6500 K slider. `ColorMath.kelvinToRgb` maps the
  slider to a plain RGB, so it is stored and sent exactly like any other colour and needs no new
  provider capability - works on every Govee and Smart Life bulb.
- The colour step reopens on the correct tab (Colours vs Whites) when editing an existing tag,
  via `ColorMath.nearestKelvin`.

### 3. Gradients - deferred, deliberately

A real gradient needs a light with addressable segments (a strip or panel). Every bulb this
alpha has been tested against is single-colour, so a gradient could be neither rendered nor
verified. Building an unverifiable feature was judged worse than shipping without it. Recorded in
the changelog and release notes; revisit with real segmented hardware.

## Verification

| Check | Result | Grade |
|---|---|---|
| `./gradlew testReleaseUnitTest` | 71 tests, 0 failures (up from 64) | VERIFIED |
| `./gradlew assembleRelease` (R8 + resource shrink, signed) | BUILD SUCCESSFUL | VERIFIED |
| APK signature | v2, cert SHA-256 unchanged from every prior release | VERIFIED |
| APK identity | `versionCode 10 versionName 0.0.10` (aapt2 badging) | VERIFIED |
| Tuya brightness in colour mode keeps h/s, moves only v, sends no work_mode/bright_value | `LightCommandPayloadTest` | CODE-PATH VERIFIED |
| Tuya brightness in white mode sets bright_value, sends no work_mode | `LightCommandPayloadTest` | CODE-PATH VERIFIED |
| kelvin <-> RGB round-trip; saturated hue not misread as white | `ColorPaletteTest` (6 new) | CODE-PATH VERIFIED |
| Tags from 0.0.5-0.0.9 unaffected | no schema change; `colorRgb` already holds any RGB | CODE-PATH VERIFIED |
| The brightness fix on a real Smart Life lamp | not run this cycle | NOT PHYSICALLY VERIFIED |
| The new palette + kelvin slider on a real bulb | not run this cycle | NOT PHYSICALLY VERIFIED |
| Gradients | out of scope | N/A |

## New tests

- `ColorPaletteTest` - palette distinctness and size, warm->cool kelvin ordering, white
  round-trip, saturated-hue rejection, `nameFor` for preset / white / custom, warm default.
- `LightCommandPayloadTest` - the two Tuya brightness-mode cases above (one existing test
  rewritten, one added).

## Security review of this change

No new network calls, permissions, or logging. `setBrightness` issues the same
`/v1.0/devices/{id}/commands` request it always did, with a different data point. No credential,
token or `localKey` is written to the log by any changed line.
