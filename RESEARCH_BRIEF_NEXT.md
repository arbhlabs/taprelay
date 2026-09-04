# TapRelay — research brief for the next version

**Headline: the Devices dashboard — the thing that finally removes any reason to open Google Home.**

Written 2026-09-04, after shipping 0.1.2. Ships as 0.1.4 if 0.1.3 (the free-for-everyone build
described in `HANDOFF.md`) goes out first; renumber freely, the content is what matters.

---

## 1. The goal, stated honestly

Aaron uses Govee and Tuya only. He wants to delete Google Home. Today TapRelay can already do
almost everything Home does for him:

| Capability | TapRelay today |
|---|---|
| Control a device | Only through a **saved item** (a tag) |
| Groups | Yes — one item, several devices |
| Scenes | Yes (Tuya) |
| Automations by time | Yes — time-of-day windows on an item |
| Automations by place | Yes — Places, 0.1.2 |
| Physical triggers | NFC + game controllers |
| **Browse and control any device directly** | **No** |
| Voice | No, and it will not have it |

**The single missing piece is the last row.** Right now, to turn on a lamp you never made a tag
for, you still open Google Home. That is the whole gap.

Be honest in the marketing: TapRelay replaces Google Home *for direct control and automation of
Govee and Tuya devices*. It does not replace Assistant voice control, Matter/Thread commissioning,
Nest devices, or cameras. Claiming otherwise will burn trust the first time someone tries.

---

## 2. What to build

### 2.1 Devices tab (the core)

A second top-level destination beside the existing item list. Lists **every device discovered
across every connected provider**, controllable immediately, with no tag required.

- Grouped by provider, or by Tuya/Govee room where the API exposes one
- Each row: icon, friendly name, live power state, and a tap target that opens **Quick Controls**
- **Reuse `QuickControlsSession` and `QuickControlsContent` unchanged.** They already do exactly
  this job: capability-driven, provider-agnostic, tested on hardware. Do not write a second
  control surface.
- Long-press (or an overflow) → "Make a tag for this", pre-filling the existing wizard

**The one piece of new plumbing needed:** `QuickControlsSession.open()` currently takes a `tagId`
and loads a `TagEntity`. Generalise it to accept a target that is either a saved item **or** a
bare `(providerId, deviceId, sku, name)`. Suggest introducing a small `ControlTarget` sealed type
and having the session accept that; the tag path becomes one of its cases. Everything downstream
(capabilities, snapshot, apply-to-all-targets) already works per-`TagTarget`.

### 2.2 Device state, and why it is the hard part

The item list currently shows `lastKnownState`, which is only ever what TapRelay last *sent*. A
dashboard that lies about state is worse than no dashboard.

- Govee and Tuya both offer a state read; `SmartHomeProvider.getPowerState()` already exists and
  `getSnapshot()` was added in 0.1.2.
- **Do not poll per device.** Govee's cloud API has hard rate limits (and TapRelay is a personal
  developer key). Fetch state in one batched discovery call per provider on screen open, then
  refresh on pull-to-refresh and after any action.
- Research needed: does the Govee Developer API expose a bulk state endpoint, or only per-device?
  If per-device only, cap concurrency and stagger. Tuya's `/v2.0/cloud/thing/{id}/shadow/properties`
  can return several properties at once — check whether a multi-device variant exists.
- Consider a short in-memory cache (30–60 s) shared with the wizard's discovery, which currently
  re-discovers on every entry.

### 2.3 Home-screen widgets

The second-biggest "why I still open Home" reason. `androidx.glance:glance-appwidget`.

- A small toggle widget bound to one item or one device
- A larger grid widget for a handful
- Widget taps go through `TriggerRouter.fire()` like any other trigger — that is what it is for.
  A widget is just another trigger source; add it to `TriggerSource`.

### 2.4 Schedules (only if the above lands cleanly)

Today time conditions gate an item that something else fires. A real schedule fires by itself.
`AlarmManager.setExactAndAllowWhileIdle` or WorkManager, plus the same reboot-rebuild pattern
`BootReceiver` already uses for geofences. Same rule as Places: it must **execute**, because
Android forbids background activity starts, so no Quick Controls from a schedule.

---

## 3. Constraints that will bite you

1. **No paywall, ever.** See `AGENTS.md`. The Govee and Tuya developer APIs are personal,
   non-commercial use. A Devices dashboard makes TapRelay look much more like a product; that is
   exactly when someone will be tempted to charge for it. Do not.
2. **Rate limits are the real ceiling** on a dashboard, not UI work. Design the state story first.
3. **Room migrations are additive and non-destructive.** Current schema version is **9**.
4. **Never `connectedAndroidTest`** against Aaron's phone — it has wiped app data before. Use
   `-PLASTDOSE_SIGNED_ANDROID_TEST` + `adb install -r` + `am instrument` if you truly need it.
5. Build with **Microsoft JDK 21**, not the Studio JBR (Java 25 breaks the Kotlin compiler).
6. Published R2 artifacts are immutable — cut a new version rather than replacing bytes.

## 4. Reuse map — do not rewrite these

| Need | Already exists |
|---|---|
| Firing anything | `trigger/TriggerRouter.kt` |
| Running an action on N devices | `execution/ActionExecutor.kt` |
| What a device can do | `SmartHomeProvider.getCapabilities()`, `DeviceCapabilities` |
| Live device state | `SmartHomeProvider.getSnapshot()`, `DeviceSnapshot` |
| A control panel | `ui/quick/QuickControlsContent.kt` + `QuickControlsSession` |
| Provider calls | `GoveeCloudProvider`, `TuyaProvider`, `SensiboProvider` |
| Discovery | `TapRelayViewModel.discoverDevicesAndScenes()` |

## 5. Suggested order

1. `ControlTarget` refactor so Quick Controls can drive a bare device (small, unblocks everything)
2. Devices tab with batched state + pull-to-refresh
3. "Make a tag for this" from a device row
4. Glance widgets on top of `TriggerRouter`
5. Schedules, if there is room

## 6. Open questions worth answering before coding

- Does the Govee Developer API have a bulk device-state endpoint, or is it one call per device?
- Does Tuya expose room/home grouping through the cloud project API the app already uses, or only
  through the Smart Life app's own account API?
- Do Govee scenes exist in the developer API? Tuya scenes are already supported; Govee scenes are
  not, and a dashboard makes their absence obvious.
- Should the Devices tab or the Items tab be the app's start screen once Devices exists?
