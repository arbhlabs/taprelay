# TapRelay v0.0.7 — QA summary

- `TapRelay-v0.0.7.apk`, versionName `0.0.7`, versionCode `7`, 6,822,794 bytes,
  SHA-256 `1514ca186f1f1cb8c9557a676625849da0b59155946db2ced4c52364ff6cfba1`,
  signed with the ARBH Labs TapRelay key. Pixel 7, 2026-09-04.

## What this fixes

In 0.0.6 a six-light group reported "5 of 6 of your lights can do this" for colour actions. The
owner correctly said all of his Govee lights support colour and brightness. A capability dump from
the live Govee cloud settled it:

```
sku=H6003        caps=on_off/powerSwitch, range/brightness, color_setting/colorRgb, color_setting/colorTemperatureK, dynamic_scene/diyScene
sku=H6003        caps=on_off/powerSwitch, range/brightness, color_setting/colorRgb, color_setting/colorTemperatureK, dynamic_scene/diyScene
sku=SameModeGroup caps=on_off/powerSwitch
```

Both real bulbs do have colour and brightness. The third entry is not a bulb: `SameModeGroup` is
Govee's own Home room grouping, which their cloud exposes with `powerSwitch` and nothing else.
The capability detection was right; the interface simply never explained what that row was.

The picker now says so, verified on the device:

```
Govee • Online
Govee • Online
Govee group • Online • on/off only
Smart Life • Online   (x3)
```

Any device reporting neither colour nor brightness gets the "on/off only" marker, not just Govee
groups. The diagnostic capability logging added to find this was removed before the release build.

## Not verified

Unchanged from v0.0.6: the exact rendered hue and level at the bulb, Tuya scenes (no scene
subscription), cold-start scan latency, and App Link autoVerify.
