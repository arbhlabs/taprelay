# TapRelay v0.0.5 — QA summary

- **Build**: `TapRelay-v0.0.5.apk`, versionName `0.0.5`, versionCode `5`, applicationId
  `com.arbhlabs.taprelay`, R8 minified + resource shrinking, signed with the dedicated ARBH Labs
  TapRelay release key.
- **SHA-256**: `0e0f63312ce8d7c88f5375053a84c7993e4661076ada297e003782115621ed69`
- **Size**: 6,822,794 bytes
- **Signing certificate SHA-256**: `7ED03578928D3B8EAEC35CF064154BEE4D48F528B0021DD3DCD9F3FDE73BA808`
  (same key as 0.0.2 and 0.0.4). Verified using APK Signature Scheme v2.
- **Device**: Pixel 7 (`panther`), ADB over Wi-Fi. **Date**: 2026-09-04.

Everything below ran against the real Govee and Tuya clouds and real bulbs. The APK installed on
the phone during these runs hashes identically to the published artifact.

## Lights available during the session

Six, all online: Govee — Desk Lamp, TV Lamp, Aaron's Room. Smart Life (Tuya) — Corner Lamp,
Vent Lamp Left, Vent Lamp Right. The two Tuya lamps that were unreachable during 0.0.4 QA were
unpowered at the wall switch; with the switch on they discovered, reported online, and responded.

## One tag, many lights

| Case | Log | Result |
|---|---|---|
| Mixed pair (Tuya + Govee), toggle on | `toggle read=0 sending=1` → `ok ... state=1 lights=2/2` | Pass |
| Mixed pair, toggle back off | `toggle read=1 sending=0` → `ok ... state=0 lights=2/2` | Pass |
| All six lights, toggle off | `toggle read=1 sending=0` → `ok ... state=0 lights=6/6` | Pass |
| All six lights, toggle on | `toggle read=0 sending=1` → `ok ... state=1 lights=6/6` | Pass |

Toggle reads the authoritative live state of the **primary** light only, then sends that one
result to every light in the group, so a group cannot split in half. A group may mix providers;
each target carries its own provider id and is dispatched to the right cloud.

Partial failure is reported, not hidden: if some lights answer and others do not, the
confirmation reads "On (5 of 6)". The tap is only treated as failed when every light fails.

## Brightness and colour

| Case | Log | Result |
|---|---|---|
| Set Colour "Violet" on the mixed pair | `ok ... action=SET_COLOR state=1 lights=2/2` | Both clouds accepted |
| Set Brightness 25% on the mixed pair | `ok ... action=SET_BRIGHTNESS state=1 lights=2/2` | Both clouds accepted |
| Tag list label | `Smart Life • Brightness 25% • 2 lights` | Correct |

Brightness and colour are only offered when *every* light in the group supports them, checked
against the capabilities each cloud reports. Govee brightness uses the `range`/`brightness`
capability as a plain percentage and colour uses `color_setting`/`colorRgb` as a packed integer.
Tuya reads the device's own data points first (`bright_value_v2` vs `bright_value`,
`colour_data_v2` vs `colour_data`) rather than assuming, switches `work_mode` to white or colour
so the change is actually visible, and powers the light on in the same request.

## Regressions re-checked

Turn On, Turn Off and Toggle on a single light still behave exactly as in 0.0.4, on both
providers. Govee discovery returns three devices; Tuya discovery returns three by account name.

## Upgrade

A migration bug was found and fixed during this work: an already-applied migration was edited in
place, so Room's schema hash stopped matching and the app crashed on launch with "Room cannot
verify the data integrity". The brightness/colour columns and the group column are now two
separate migrations (2→3 and 3→4), so a phone coming from 0.0.4 (schema 2) and a phone that
already took the intermediate schema 3 both upgrade cleanly. Verified by installing over the
broken state: the app launched, and every existing tag, group and credential survived.

## App icon

The previous launcher icon was a dark badge on a dark gradient, so on a dark home screen it read
as an empty hole; the mark also sat off-centre and used three tints at two stroke weights. The new
icon is a white tap-point with three waves on a brand-teal gradient, balanced on the icon centre
and inside the 66dp adaptive safe zone, with one stroke weight and depth from opacity alone.
Checked at real launcher size on the device.

## Automated tests

`./gradlew testReleaseUnitTest` — **55 tests, 0 failures** (up from 37). New coverage:

- `LightSettingsTest` — brightness clamping, the Govee pass-through, the Tuya 10..1000 mapping,
  RGB→HSV for red/green/blue, white having no saturation and black no value, every preset landing
  in range, and brightness/colour actions targeting the on state.
- `LightCommandPayloadTest` — the actual request bodies: Tuya powers on, switches `work_mode`, and
  scales the data point; the colour data point is sent as an object, not the string form used in
  status reads; a plug with no colour data point is refused; Govee sends the right capability
  type, instance and value.
- `MultiTargetTagTest` — target list construction with the primary first, mixed providers,
  JSON round-trip through the Room converter, and corrupt or missing column data reading as
  "no extra lights".

## Not verified

- The exact rendered hue and perceived brightness at the bulb. Both clouds accepted every command
  and reported success, and the group state transitions are confirmed in the logs, but judging the
  actual shade is the owner's call.
- Tuya scenes — the project has no scene subscription, so the scene list is empty.
- Cold-start-from-killed scan latency (functional, not timed).
- App Link `autoVerify` (needs `assetlinks.json` on taprelay.app).
