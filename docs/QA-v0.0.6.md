# TapRelay v0.0.6 — QA summary

- **Build**: `TapRelay-v0.0.6.apk`, versionName `0.0.6`, versionCode `6`, R8 minified, signed
  with the ARBH Labs TapRelay release key (cert SHA-256
  `7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`, APK Scheme v2).
- **SHA-256**: `059d8280b8ce7eedb704be4b0d6d259469da76889de4d68e80278dfe0cedf0a2`
- **Size**: 6,822,794 bytes. **Device**: Pixel 7. **Date**: 2026-09-04.

The APK installed on the phone during these runs hashes identically to the published artifact.

## Scene tags — the headline

| Case | Log | Result |
|---|---|---|
| One tag, six lights, Violet at 36%, one tap | `ok provider=govee_cloud action=SET_SCENE state=1 lights=5/6` | Pass |
| Action list before saving | "5 of 6 of your lights can do this" | Correct |
| Tag list label | `Govee • Violet 36% • 6 lights` | Correct |
| Set Brightness on a single Govee light | `ok action=SET_BRIGHTNESS state=1 lights=1/1` | Pass |

The sixth light is a Govee device that reports no colour capability, so it is honestly excluded
rather than silently counted as a success. That is the whole point of the change in this release:
brightness and colour are now offered when **any** light in the group can do them, because a real
group is a mix, and the app tells you the count both before you save and after you tap.

On Tuya a look is one request. In colour mode the brightness is the "v" channel of the colour data
point, so sending a separate `bright_value` command afterwards would fight the colour that was just
set. Govee treats them as two capabilities and gets two calls.

## Regressions re-checked

Group toggle, Set Colour and Set Brightness from 0.0.5 all still behave correctly, on both
providers. Discovery still returns three Govee and three Smart Life lights by account name.

## Automated tests

`./gradlew testReleaseUnitTest` — **57 tests, 0 failures**. New: the Tuya look is asserted to be a
single request carrying hue and the brightness-derived value channel, with no `bright_value`
command alongside it; and the colour value channel is asserted to use the same 10..1000 scale as
the brightness data point.

## Not verified

- The exact rendered hue and perceived level at the bulb. Both clouds accepted every command and
  reported success; judging the shade is the owner's call.
- Tuya scenes — no scene subscription on the project, so the scene list is empty.
- Cold-start-from-killed scan latency (functional, not timed).
- App Link `autoVerify` (needs `assetlinks.json` on taprelay.app).
