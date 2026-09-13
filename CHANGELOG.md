# TapRelay Changelog

## v0.6.7 - 2026-09-13 (Alpha / Internal Test)

### Added
- Xiaomi Smart Band 9 wrist remote (needs Notify for Xiaomi and the TapRelay band app 1.4.1): a
  2-column tile grid of your chosen items, Remote Mode and settings, with live heart rate on the band.
  Choose tiles, order and icons in AOD settings → watch icon. A quiet "Band link" notification keeps
  the band working while TapRelay is closed.

- Band look (band app 1.5.0): Grid / Compact / List layouts, 8 accent colours, heart rate large /
  small / off, names on tiles, keep-screen-on and buzz switches, with a live miniature of the band.
- Band tile icons are picked from each item's name (vent, corner, desk, ceiling, strip, TV, fan…) and
  never repeat within a family, so similar lamps are told apart at a glance.

### Changed
- Volume up/down and next/previous track (Windows and phone) repeat on every press, on every
  surface - no more 1.5 s wait between presses. Band taps no longer wait for the previous one.
- A Windows helper that only accepts the older sign-in now gets one request per press, not two.

### Fixed
- TapRelay no longer crashes on launch while Notify for Xiaomi is restarting; the band link
  reconnects by itself once Notify is back.

## v0.6.5 - 2026-09-11 (Alpha / Internal Test)

### Added
- AOD log cards: each favourite shows how long since it last ran, "Last logged Today · 21:42 ·
  ♥ 106 BPM" and how many times today, read from real history (LastDose items from LastDose,
  needs 7.2.12+ for the today count). Timers stay visible while the face is dimmed.
- Heart-rate options on the AOD: Large / Compact / Hidden, red / accent / white, and pulse on each log.
- "Favourite on the AOD" switch on every item; same list as AOD settings.
- Each run records your live pulse (when fresh) in history.

## v0.6.4 - 2026-09-11 (Alpha / Internal Test)

### Added
- The always-on face shows your live heart rate ("♥ 93 BPM") from your band, read from LastDose
  (needs LastDose 7.2.11+). It appears only while a fresh reading exists.

## v0.6.3 - 2026-09-11 (Alpha / Internal Test)

### Added
- **Windows PC** (⋮ → Windows PC): pair a PC running the new TapRelay PC Relay helper with a
  6-digit code, then add PC actions as ordinary items and map them to any controller button, NFC
  tag or place: Play/Pause, Next, Previous, Stop, Volume up/down, Mute, any keyboard shortcut
  (e.g. `ctrl+shift+m`, `alt+tab`, `f11`), open a link/app/file, lock, screen off, sleep.
- TapRelay PC Relay (`windows-relay/`): a small tray app for Windows. Media keys reach YouTube in
  the browser even when it is not focused. Only a phone that typed the code shown on the PC gets
  a token; tokens are stored hashed. Starts with Windows (can be turned off in its window).

- Home screen: every lamp/device item has a real on/off switch showing its state (on restores
  the item's own brightness/colour; the switch snaps back if no device answers). Scenes, LastDose
  logs, PC actions and web requests get a ▶ run button instead of "Test".

### Fixed
- The existing PC relay code was never connected: pairing was never loaded, item values were
  never sent, and plain-HTTP calls to the PC were blocked. Discovery now uses the address the
  PC actually replied from, so a VPN adapter on the PC cannot break pairing.

## v0.6.2 - 2026-09-11 (Alpha / Internal Test)

### Fixed
- D-pad brightness felt spotty: 10% steps were invisible near full brightness, and every window
  started from the item's saved value rather than where the lamp actually was. Up/Down now walk
  visible notches (1, 5, 10, 20, 35, 50, 70, 100%) from the brightness TapRelay last set on that
  lamp, or the item's own brightness right after it turns on.
- Pressing Up at 100% or Down at 1% gives one soft tick instead of silently doing nothing.
- The D-pad now reaches the light window wherever you are. Android never gives the Xbox D-pad to
  the "any app" service, so outside TapRelay it moved the phone's UI, and in LastDose's
  always-on screen only the mapped Left/Right reached TapRelay (colour worked, brightness moved
  the UI). While the 20-second window is open and TapRelay isn't on screen, a small pill at the
  bottom holds the D-pad; taps elsewhere still reach the app behind (and hand the D-pad back),
  and the pill closes itself when the window ends.
- Light adjustments now log each D-pad step and any light that fails to respond.

## v0.6.1 - 2026-09-11 (Alpha / Internal Test)

### Changed
- Light adjustment moved from the left stick (too sensitive) to the D-pad. Turning a light on
  with a controller opens a 20-second window: D-pad up/down steps brightness in 10% notches,
  left/right steps colour by 30° of hue. Unsupported directions keep their normal mapping.
- The window closes after exactly 20 s (or when that light goes off), the D-pad returns to its
  own mappings, and the pad gives one double-knock so you know. Turning the light on again
  restarts the window; switching to another item or disconnecting the pad closes it quietly.
- The left stick no longer changes lights at all.

### Fixed
- LastDose logging shows "LastDose isn't available" after LastDose was reinstalled — fixed on
  the LastDose side in 7.2.8 (install-order-dependent permission gate removed).

## v0.6.0 - 2026-09-09 (Alpha / Internal Test)

### Added
- A selected or controller-mapped smart light now gets automatic left-stick controls: vertical
  movement adjusts brightness and horizontal movement adjusts colour when the device supports it.
- Stick movement is analog, dead-zoned and coalesced to at most one cloud update every 400 ms.
  Capability checks prevent colour calls to brightness-only lights and skip non-light devices.
- Controllers & Remotes and Remote Mode show the active automatic controls and target.
- Existing explicit mappings remain authoritative; controller discovery continues to refresh
  automatically on connect, disconnect and device changes.
- PC relay actions from the integration branch are included in this release.

### Fixed
- Controller light adjustments no longer require a separate manual mapping for every direction.
- Cloud APIs receive only the newest coalesced desired state instead of every noisy stick event.

## v0.5.2 - 2026-09-06 (Alpha / Internal Test)

Polish for the controller-in-any-app hand-off.

### Changed
- **A button with no mapping is no longer swallowed.** When the global service is on, only a
  button you have actually mapped is consumed; anything else passes straight through to the app
  in front, so a game or another app keeps every button you did not assign. The set of mapped
  buttons is kept live from the database, so this stays correct as you add and remove mappings.
- Dropped the per-press diagnostic toast from 0.5.1. Feedback is now quiet: one toast when the
  service connects, the action's own result when a mapped button fires, and a single "no mapping"
  note when an unmapped button is pressed.

### Notes
- Pairs with LastDose 7.2.1, which fixes the always-on face not refreshing immediately after a
  controller-driven log.

## v0.5.1 - 2026-09-06 (Alpha / Internal Test)

Diagnosing "I enabled it and nothing happens".

### Fixed
- The accessibility service showed up in Android's list with a whole sentence as its name
  ("Use Fire a mapped controller button from any app"). It now has a proper short label,
  "TapRelay controller (any app)".
- Surfaces opened by a global button press (Open item / Quick Controls activation modes) now
  carry `FLAG_ACTIVITY_NEW_TASK`, so they actually launch from the service instead of being
  swallowed by a silent exception.

### Added
- **The service now tells you where a press stops.** While the alpha runs, each gamepad button
  the service receives shows a short toast: none at all means Android is not delivering gamepad
  keys to the service on this device; "sent to TapRelay" means it was received and decoded;
  "no mapping in TapRelay" means it arrived but nothing is mapped to that button. A one-time
  toast on connect confirms the service bound and found the app.
- **"Blocked by Android" state on the Controllers screen.** If the switch is on but Android never
  started the service — the usual outcome for an app installed from outside the Play Store — the
  card now says so and links straight to App info, with the exact steps ("Allow restricted
  settings", then toggle off/on).

## v0.5.0 - 2026-09-06 (Alpha / Internal Test)

**The controller keeps working when you leave TapRelay.** Map a button once, then use it from
another app, the home screen, or with the screen off — TapRelay and LastDose no longer have to
be the app in front.

### Added
- **Use controller in any app.** A new opt-in accessibility service (Controllers & Remotes →
  "Use controller in any app") lets a mapped button fire globally. You grant one accessibility
  permission; nothing runs until you do, and TapRelay only ever sees the buttons you mapped —
  no screen content, no activity tracking.
  - Works with the face buttons (A/B/X/Y), bumpers (LB/RB), stick clicks (L3/R3) and Menu/View.
  - The D-pad and analog triggers stay in-app only: Android never delivers those to an
    accessibility service.
  - A button mapped here is consumed, so it won't also act in the app you're using.

### Changed
- LastDose no longer needs its always-on screen in front to hand a controller press to TapRelay
  when the global service is on — the press is caught before any app sees it.
- New `list_mapped_inputs` method on the LastDose hand-off provider: LastDose 7.2.0 uses it to
  skip a cross-process call for a button nobody mapped and to fire a mapped one even on a cold
  start. Still caller-pinned to `com.lastdose.app`; exposes only the input names, nothing else.
- Controllers & Remotes screen: the old "TapRelay will not do that" note is replaced by the
  opt-in card above; Remote Mode stays the zero-setup option.

### Fixed
- The input-device listener is now reference-counted, so opening and closing the TapRelay app no
  longer blinds a running Remote Mode or the global service to controller connect/disconnect.

## v0.4.0 - 2026-09-06 (Alpha / Internal Test)

**Useful with just a phone.** You no longer need a smart bulb to get value from a controller
button or an NFC tag.

### Added
- **My phone actions.** A trigger can now control the phone itself, with no account and no
  permission:
  - **Music & media** — Play / pause, Next track, Previous track. Goes through Android's media
    controls, so one button pauses whatever is playing: Spotify, YouTube Music, a podcast.
  - **Volume** — Up, Down, Mute / unmute, with the system volume panel shown.
  - **Flashlight** — On, Off, or Toggle, using the rear torch.
  Do Not Disturb moved under the same "My phone" action and still asks for its one system switch.
- **Web request presets.** The Web request editor now starts from ntfy, Discord, or Home Assistant
  webhook, each with the address shape, method and an example message filled in, plus one line on
  where to get the URL. "Something else" keeps the blank form. Secrets still go through the
  encrypted Key field and are never logged.

### Notes
- Media and torch actions fail gracefully: "nothing is playing" is reported, not hidden, and a
  phone with no flashlight says so.
- No new permissions in the manifest. No database migration — existing tags, mappings, Magic
  Actions and stored config are untouched.
- Every new action is an ordinary TapRelay item: it works from a controller button, an NFC tag,
  a place, the always-on face, a widget and inside a Magic Action, and carries its own Haptic
  Signature.

## v0.3.1 - 2026-09-06 (Alpha / Internal Test)

- **ARBH Labs HapticSignatures.** Completed actions now use a stable tactile language: LastDose
  logs are crisp doubles, controls are shaped confirmations, macros are short sequences, and
  failures are unmistakable. Assignments are deterministic and kept perceptually separated as
  mappings are added. Android's actual controller vibrator IDs are discovered at runtime; trigger
  motors are never assumed or claimed without being exposed by Android.
- **Controller while LastDose AOD is open.** LastDose forwards supported Xbox/controller button
  presses from its focused always-on screen directly to TapRelay's authenticated hand-off. Existing
  TapRelay mappings can therefore control devices, log to LastDose, or open Quick Controls without
  leaving the AOD. Analog axis-only trigger positions remain subject to Android focus limitations.
- Controller and phone haptics can be turned off independently; Haptic Signatures are on by default.

## v0.2.1 - 2026-09-05 (Alpha / Internal Test)

**Turn anything into a programmable button.** One press now runs a whole sequence, Home Assistant
joins the providers, and TapRelay reaches the home screen.

### Added
- **Magic Actions.** One trigger runs several of your actions in order, with optional pauses.
  Lights off, air down, log to LastDose, phone quiet - from one tap on a sticker, one controller
  button, one press on the always-on face, or one home-screen tile. Start from a template
  (Bedtime, Gaming, Desk Mode, Movie Time, Leaving Home, Quick Log) and it fills itself in from
  the actions you already have. If a step cannot be reached the rest still run, and TapRelay says
  which one did not: `4 of 5 done - Air purifier unavailable`.
- **Home Assistant, as an ordinary connection.** Give TapRelay your instance's address and an
  access token you create yourself, and it discovers your lights, switches, fans, scenes and
  scripts. They behave like any other TapRelay device from that moment - on a tag, on a controller
  button, inside a Magic Action, on a widget. TapRelay talks to your instance directly; nothing is
  proxied and no ARBH Labs server is involved.
- **Home-screen widgets.** An Action Key for one action, and a resizable Remote for up to six.
  Both follow your wallpaper colours on Android 12 and later, and change layout as you resize them
  instead of clipping. A tile shows it is running the moment you touch it.
- **A redesigned always-on face**, built around the actions you use most: one large primary
  button, the rest underneath, a compact hardware line, and nothing else. Choose and reorder your
  favourites under **Always-on face**. One tap arms a button and the second runs it, so a sleeve on
  the glass cannot turn the bedroom lights off - switchable if you want it immediate.
- **Three new kinds of action:** open an app or a link, switch Do Not Disturb, and send a web
  request to an address you already have. A web request's key is encrypted on your phone and never
  written to your action history, which keeps only the method and the host.

### Fixed
- Selected chips and buttons were drawn in Android's default purple rather than TapRelay's own
  colour, because half the Material colour roles were never defined.
- New screens described every device as "Device" instead of Govee, Smart Life, Sensibo or Home
  Assistant.
- The feedback message covered the title of the screen it appeared over. It now sits below it, and
  shows how far through a Magic Action is.
- Reading your saved connections decrypted them on the main thread during launch, which showed as
  a stutter on the first screen.
- The connections chip listed every provider by name and stopped fitting at four.

### Notes
- Upgrades in place from 0.1.5. Existing tags, controller mappings, places and LastDose items are
  preserved (database schema 11).
- Everything is still free. No subscription, no trial, no tag limit.

## v0.1.4 - 2026-09-04 (Alpha / Internal Test)

**Your controller can log to LastDose.** Pull the right trigger and one entry lands in LastDose -
the app never opens, nothing is navigated, nothing is tapped for you.

### Added
- **LastDose logs as TapRelay items.** Pick a LastDose log, set the amount, save. It becomes an
  ordinary TapRelay item, so the same thing that binds a lamp to a controller button, an NFC tag
  or a place binds a log: `Right Trigger -> Bowl +1`. Under **LastDose Logs** in the menu.
- **Direct, background logging.** TapRelay asks LastDose to write the entry through LastDose's own
  canonical log path - the same one its on-screen LOG NOW button and its NFC tags use. There is no
  second logging implementation and no UI automation. TapRelay reports success only after LastDose
  has confirmed the entry exists.
- Needs LastDose 7.1.10 or later. The link is signature-pinned in both directions: no other app can
  use it, and TapRelay says plainly when LastDose is missing, too old, or the log has been deleted.

### Fixed
- **One trigger pull is one action.** Xbox triggers are analog axes, not buttons: the value climbs,
  wobbles and drifts. A single threshold could fire again on every wobble across it, so activation
  and release now use separate thresholds. Holding a trigger produces exactly one action; releasing
  re-arms it for a deliberate second pull.

## v0.1.3 - unreleased (folded into 0.1.4)

**Everything is free.** No subscription, no trial, no five-tag limit, nothing to unlock.

TapRelay reaches your lights and climate devices through Govee and Tuya developer keys you
generate yourself, and both of those APIs are licensed for personal, non-commercial use. A paid
tier gating access to them does not fit that licence, so the paywall is gone rather than quietly
left in place.

Unlimited tags, multi-device routines, time-of-day windows, diagnostics and replay, activation
modes, Quick Controls, Remote Mode, controller mappings and Places are all simply part of the app.
The licence plumbing is left intact but inert, so an existing stored licence still reads cleanly
and no capability depends on one.

This version was built and tested but never published; everything in it ships in 0.1.4.

## v0.1.2 - 2026-09-04 (Alpha / Internal Test)

**A tap doesn't have to mean "do it now".** Every trigger now has an activation mode, your
controller is a real remote, and TapRelay can act on where you are.

### Added
- **Activation modes.** Each item, and each individual trigger, chooses what firing means:
  **Execute** (run the action, exactly as before), **Open item** (jump straight to that item), or
  **Quick Controls** (a small panel so you decide in the moment). A trigger's own choice wins;
  otherwise the item decides. One controller button can execute a lamp while another opens its
  controls.
- **Quick Controls.** A fast, capability-driven panel: device name, icon, live state, and only the
  controls the real hardware can perform. A light gets power, brightness, white temperature and
  colour; an air purifier gets power and the fan speeds it actually reports — nothing else. A
  group only offers what every device in it shares.
- **Remote Mode.** An always-on remote face: clock and date, your controller and its battery, a
  drawn pad that rings the buttons you have mapped and flashes the one you just pressed, and a
  live list of what each button does with its current on/off state. It shows over the lock screen
  and holds the display awake, then dozes: black background, the panel's own dimmest backlight,
  the slowest frame rate the display will give, and one redraw a minute. It drifts within safe
  bounds so nothing burns in, and waking is a deliberate slide rather than a stray tap. Add the
  **TapRelay Remote** Quick Settings tile to get there from anywhere without opening the app.
- **Places.** Set a circle where you are standing and have an item fire when you arrive, when you
  leave, or both — with a different item for each direction. Places survive reboots and app
  updates, and need location set to "Allow all the time" because Android only lets a geofence fire
  in the background with it.
- **Controller rumble.** The pad in your hand buzzes when a button fires: a shaped rise-and-thud
  on success, three firm knocks on failure, using haptic primitives where the pad supports them.
- **Preferences**, in one card on Controllers & Remotes rather than a new settings screen:
  controller rumble, phone haptics, dim-when-idle, Remote Mode spacing (Compact / Normal / Large)
  and keep-screen-awake.

### Fixed
- **You couldn't assign a controller button.** The learning step was a dialog, and Android hands
  gamepad input only to the focused window — so the pad moved system focus around instead of being
  captured. The learning surface now lives in the app's own window and swallows the whole press.
- **Sensibo showed fan speeds your unit doesn't have.** The wizard offered High / Med / Low /
  Quiet / Auto to every climate device. TapRelay now asks the unit what it can actually do: a Pure
  air purifier that reports only Low and High is offered only Low and High, everywhere. The
  Low/High tap toggle uses the unit's real slowest and fastest speeds instead of assuming.
- Landscape is handled properly across the app: the Remote Mode face splits into two panes, the
  pad drawing is capped so it cannot dominate a wide screen, and Quick Controls and the item sheet
  scroll instead of clipping on a short screen.
- A tag driving several lights reads its capabilities in one request per provider rather than one
  per light.

### About your controller and the background
Android delivers game-controller buttons only to the app in front. No app can read your pad from
the background without an accessibility service, which is against Google Play policy for input
remapping — so TapRelay won't. Remote Mode is the honest answer instead.

## v0.1.0 - 2026-09-04 (Alpha / Internal Test)

First 0.1 tag. Rolls up everything in 0.0.10 (brightness keeps the light's colour; the 22-colour
palette and the warm-to-cool white slider) and fixes the version shown in **About TapRelay**,
which was still hard-coded to 0.0.8. It now reads the real build version, so it can't drift again.

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
