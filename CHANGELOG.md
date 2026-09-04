# TapRelay Changelog

## v0.0.10 - 2026-09-04 (Alpha / Internal Test)

**Colour polish.** A brightness tap no longer steals your colour, and the colour picker grew up.

### Fixed
- **A brightness-only tag keeps the colour the light is already showing.** On Smart Life lamps,
  setting a brightness used to force the bulb into plain white mode — so a tag meant only to dim
  a light would wipe out a colour you'd set from Google Home or the Smart Life app. TapRelay now
  reads what mode the light is in: if it's showing a colour, only the brightness of that colour
  changes; hue and saturation are left exactly where they were. This also fixes brightness taps
  that "didn't take" — the mode flip was what was fighting them.

### Added
- **A much bigger colour palette.** 22 named colours around the full wheel (red through amber,
  green, teal, cyan, the blues, violet, magenta, pink), plus a dedicated **Whites** tab with six
  named white points and a warm-to-cool temperature slider from 1800 K to 6500 K. The white
  slider is stored as an ordinary colour, so it works on every Govee and Smart Life bulb without
  any extra setup.
- The colour step remembers whether a tag's colour is a named colour or a white, and reopens on
  the right tab when you edit it.

### Not in this build
- **Gradients / multi-colour effects.** These need a light with addressable segments (a strip or
  a panel). None of the single-colour bulbs this alpha has been tested against can render one, so
  gradient support is deferred until it can be built and verified on real segmented hardware.

### Unchanged
- The sticker still stores only the opaque App Link. Tags saved by 0.0.5 to 0.0.9 keep working;
  no database change.
- Private alpha. Not affiliated with Govee, Tuya, Smart Life or Google.

## v0.0.9 - 2026-09-04 (Alpha / Internal Test)

**Reliability polish.** No new features — two fixes to things that quietly did the wrong thing.

### Fixed
- **The home screen now shows the real Smart Life device and scene counts.** After connecting
  Smart Life, the card could stay stuck on "Connected • 0 devices • 0 scenes" because those
  numbers were read once and never allowed to update when discovery finished a moment later.
  The Govee count already updated correctly; now Smart Life matches.
- **Disconnecting Smart Life now sticks.** "Disconnect" cleared the stored credentials but left
  a copy cached in memory, so the app still believed it was connected — the next connection
  check would silently turn it back on until the app was fully closed. The cache is now cleared
  too.

### Unchanged
- The sticker still stores only the opaque App Link. Tags saved by 0.0.5 to 0.0.8 keep working.
- Private alpha. Not affiliated with Govee, Tuya, Smart Life or Google.

## v0.0.8 - 2026-09-04 (Alpha / Internal Test)

**Actions now combine.** Power, colour and brightness were three choices where you could only
pick one. They are independent things, and a tag can now carry any combination of them.

### Added
- Choose what the power does (Toggle, Turn On, Turn Off) and then, separately, whether the tag
  also sets a colour and whether it also sets a brightness. A sticker can toggle your TV lamp
  and bring it up blue at 10%, which was impossible before.
- Turning a light off stays off: there is no point colouring a dark bulb, so a tag that toggles
  a light off skips its look and applies it again next time the light comes on.
- Every combination works across a group of any size, mixing Govee and Smart Life.
- The tag list spells the whole thing out, for example "Toggle - Blue - 10% - 3 lights".

### Fixed
- The "also set a colour / brightness" rows toggle from anywhere on the row, not only from the
  small switch.
- The database no longer falls back to a destructive migration, so a missing migration fails
  loudly instead of silently deleting every tag mapping.

### Unchanged
- The sticker still stores only the opaque App Link. Tags saved by 0.0.5 to 0.0.7 keep working.
- Private alpha. Not affiliated with Govee, Tuya, Smart Life or Google.

## v0.0.7 — 2026-09-04 (Alpha / Internal Test)

### Fixed
- **A Govee room grouping is now labelled for what it is.** Govee's cloud reports a Home room
  group as a device of its own, and only ever lets it be switched on and off. TapRelay listed it
  next to real bulbs with no explanation, so a group containing it would say "5 of 6 of your
  lights can do this" with no clue why. It now reads "Govee group • Online • on/off only", and
  any light that reports neither colour nor brightness is marked "on/off only" too. Your bulbs
  were never the problem.

## v0.0.6 — 2026-09-04 (Alpha / Internal Test)

Scene-style tags. One sticker can now put a whole group of lights into an exact look —
colour and brightness together — in a single tap.

### Added
- **Set Colour + Brightness.** A tag can carry both at once, so a tap sets a room to, say,
  violet at 36% rather than making you choose one or the other. On Tuya this is a single
  request: in colour mode the brightness rides on the colour's own value channel, so a
  separate brightness command cannot fight the colour it was just given.
- Brightness, Colour and Colour + Brightness are now offered when **any** light in the group
  supports them, not only when all do. A real group is usually a mix, and the colour bulbs in
  it should still be settable. Each action says up front how many of your lights can do it
  ("5 of 6 of your lights can do this"), and the confirmation after the tap says how many
  actually changed.
- The tag editor now names the lights a tag drives, so you can see a group without reopening
  the picker.

### Unchanged / still true
- The physical sticker still stores only the opaque `https://taprelay.app/t/<uuid>` App Link.
  Changing a group, a colour or a brightness never rewrites the sticker.
- Private alpha. TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya,
  Smart Life or Google.

## v0.0.5 — 2026-09-04 (Alpha / Internal Test)

One sticker can now drive a whole group of lights, and a tap can set a brightness or a
colour instead of only flipping power. Verified on a Pixel 7 against the live Govee and
Tuya clouds with six real lights.

### Added
- **One tag, many lights.** Pick as many lights as you want in Add Tag and they all respond
  to the same tap. Groups can mix Govee and Smart Life. For Toggle, TapRelay reads the live
  state of the first light and sends that same result to every light in the group, so they
  move together instead of drifting apart.
- **Set Brightness.** Choose an exact brightness on a slider; every tap of that sticker puts
  the light at that level. Govee gets its 1-100 range value, Tuya gets its 10..1000
  `bright_value` data point and is switched to white mode so the change is visible.
- **Set Colour.** Twelve presets from warm white to violet. Govee gets a packed RGB value,
  Tuya gets hue/saturation/value and is switched to colour mode.
- Brightness and Colour are only offered when every light in the group supports them, and a
  light that cannot do what is asked now says so in plain language.
- The tag list shows what a tag really does — "Brightness 25% • 2 lights", not just "Toggle".
- If some lights in a group answer and others do not, the confirmation says so
  ("On (5 of 6)") rather than silently reporting success.

### Changed
- **New app icon.** The old one was a dark badge on a dark background, so on a dark home
  screen it read as a hole. The mark is now white on brand teal, centred in the adaptive
  safe zone, with one stroke weight and depth carried by opacity instead of three competing
  tints.

### Unchanged / still true
- The physical sticker still stores only the opaque `https://taprelay.app/t/<uuid>` App Link.
  Adding lights to a tag, or changing what it does, never rewrites the sticker.
- Private alpha. TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya,
  Smart Life or Google.

## v0.0.4 — 2026-09-04 (Alpha / Internal Test)

Real Smart Life / Tuya support. Verified end to end against a live Tuya Central Europe
cloud project on a Pixel 7 — no mock or demo devices exist anywhere in the app.

### Added
- **Smart Life (Tuya) provider.** Connect with your own Tuya IoT Core Access ID and Access
  Secret; the credentials are encrypted on device exactly like the Govee key.
- Devices are discovered through the current *Query Devices in Project* endpoint
  (`GET /v2.0/cloud/thing/device`) and appear under the friendly names you gave them in the
  Smart Life app, with their live online state.
- Tags can now target either a device or a scene, and the picker mixes Govee and Smart Life
  devices in one list.

### Fixed
- **Smart Life discovery reported 0 devices.** The project response was being decoded with the
  legacy device schema, which has none of the fields the project endpoint actually returns.
  The response shape is now detected before decoding.
- **Devices showed their generic product name.** The account label lives in `customName`, not
  `name`; `name` is the manufacturer's product string ("Candle Lamp W505Z2 3").
- **Online state was always reported as online.** The project endpoint spells it `isOnline`.
- `page_size` above 20 is rejected by Tuya with `40000904 param size too much`; discovery now
  requests a page the API accepts.
- Tuya request signing includes the uppercase method, the SHA-256 body hash, the empty
  signed-header section and the full path with query string.
- **Toggle now reads authoritative state before acting.** It fetches the device's live data
  points, picks the real boolean power point (`switch_led` on these lamps rather than a guessed
  `switch_1`), sends the inverse, and only writes the local last-known state after the command
  succeeds.
- Diagnostics no longer log Tuya response bodies, which contain per-device `localKey` secrets.

### Unchanged / still true
- The physical sticker stores only the opaque `https://taprelay.app/t/<uuid>` App Link. The
  mapping stays in the local database and stays editable without re-tapping the sticker.
- Govee is untouched and was regression-tested on real hardware in this release.
- Private alpha. TapRelay is not affiliated with, sponsored by, or endorsed by Govee, Tuya,
  Smart Life or Google.

## v0.0.2 — 2026-09-03 (Alpha / Internal Test)

Real-hardware completion pass. The full loop now works end-to-end on a Pixel 7:
provision a blank sticker → pick a Govee device + Toggle → tap sticker → light toggles → tap again → toggles back.

### Fixed
- **Add Tag could not provision a normal blank sticker** — it showed "This tag isn't a
  TapRelay tag." The reader was started with `FLAG_READER_SKIP_NDEF_CHECK`, which left the
  `Ndef` / `NdefFormatable` techs off the tag so the writer had nothing to write to. The flag
  is removed; blank and unformatted stickers now provision on the first tap.
- Provisioning is now a real state machine that separates *scan mode* from *provisioning mode*:
  a non-TapRelay tag is only an error in normal scanning; in Add Tag it is a candidate.

### Added
- **Add Tag scan states**: Ready (pulsing NFC target) → Detected → Setting up your sticker… →
  advances automatically. Errors show a plain message + a Try again button (no more stuck error).
- **Already-has-data stickers**: "This sticker already contains data. TapRelay can replace it."
  with **Use this tag / Cancel** — nothing is overwritten without confirmation.
- **Already-a-TapRelay-tag**: jumps straight to its configuration (pre-filled if a mapping exists).
- **Read-only / unsupported stickers**: "This NFC sticker can't be written."
- Write is **verified by read-back** where the tag allows it.
- **NFC-off banner** on the home screen and in Add Tag when NFC is disabled.
- **About TapRelay** dialog (overflow menu) with the alpha + Govee non-affiliation disclaimer.
- **Disconnect Govee** in the overflow menu.

### Unchanged / still true
- Physical tag stores only the opaque `https://taprelay.app/t/<uuid>` App Link. Mapping is local
  and editable without re-tapping the sticker.
- Govee: personal / non-profit API terms, Bring-Your-Own-Key, private alpha, no partnership claimed.
- Google Home: provider boundary only, not enabled.
- Govee LAN / Matter: not implemented.

## v0.0.1 — 2026-09-03 (Alpha / Internal Test)

First build. Onboarding, Govee connect + discovery, Add Tag wizard, stateless tags, scan-to-run
with optimistic toggle + rollback, tag management, Tink-encrypted key storage, human error copy,
reader-mode NFC, translucent trampoline scan path. 17 unit tests. Signed release.
