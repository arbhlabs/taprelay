# TapRelay v0.0.8 - QA summary

`TapRelay-v0.0.8.apk`, versionName `0.0.8`, versionCode `8`, 6,822,794 bytes,
SHA-256 `f697654367b756cdf6bea47e328cafaec0a5914340b8c307ef30aa57b501f244`, signed with the
ARBH Labs TapRelay key. Pixel 7, 2026-09-04. The APK installed during these runs hashes
identically to the published artifact.

## What was wrong

Up to 0.0.7 a tag had exactly one action: Toggle, Turn On, Turn Off, Set Brightness, Set Colour,
or Set Colour + Brightness. Those are not alternatives. Power is one axis and the look is
another, so it was impossible to say "toggle my TV lamp, and when it comes on make it blue at
10%". The owner found that, and was right that the model was wrong rather than incomplete.

## What it does now

Power is chosen on its own. Colour and brightness are independent switches on top of it. One
routing rule covers every combination, so both providers behave identically:

| Target state | Colour | Brightness | Result |
|---|---|---|---|
| off | anything | anything | power off only |
| on | no | no | power on |
| on | yes | no | colour |
| on | no | yes | brightness |
| on | yes | yes | one combined look |

Turning a light off deliberately ignores the look and applies it again next time it comes on.

## Hardware verification

The owner's own example, on a real Smart Life lamp:

```
toggle provider=tuya_cloud read=0 sending=1
ok     provider=tuya_cloud action=TOGGLE state=1 lights=1/1     -> on, Blue at 10%
toggle provider=tuya_cloud read=1 sending=0
ok     provider=tuya_cloud action=TOGGLE state=0 lights=1/1     -> off, look skipped
```

Tag list reads `Smart Life - Toggle - Blue - 10%`. A fresh sticker was also provisioned and
configured end to end through the new flow.

## Automated tests

**64 tests, 0 failures.** New `ActionCombinationTest` records what every combination sends to a
provider, and that tags saved by 0.0.5 to 0.0.7 map onto the right power intent so nothing saved
earlier changes behaviour.

## Also in this build

- The colour and brightness rows toggle from anywhere on the row.
- `fallbackToDestructiveMigration()` removed. It was never observed to fire, but it meant a
  missing migration would silently delete every tag mapping instead of failing loudly. A
  precaution, not a fix for a data loss that happened.

## Not verified

The exact rendered hue and level at the bulb; Tuya scenes (no scene subscription); cold-start
scan latency; App Link autoVerify.
