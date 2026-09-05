# TapRelay — Comprehensive Product Strategy, Revenue Architecture & Implementation Handoff

**Target Release**: TapRelay `0.2.0` (Code `17`)  
**Target Platform**: Android 15, 16 & 17 / Pixel 7 (compileSdk 35, targetSdk 35, minSdk 30)  
**Primary Architect**: Senior Android Architect & Head of Product Strategy, ARBH Labs  
**Date**: September 5, 2026  
**Document Status**: AUTHORITATIVE SOURCE OF TRUTH FOR CLAUDE AUTONOMOUS TAKEOVER  

---

## 1. Executive Summary

### The Core Business Objective
ARBH Labs needs TapRelay to generate its **first real revenue** as quickly as reasonably possible while building durable long-term software and hardware value.

### The Product Promise
TapRelay is NOT a complex programming IDE like Tasker, nor is it a server-heavy hobbyist platform like Home Assistant. Its fundamental, sacred product promise is:

> **PHYSICAL INPUT → ACTION → DONE.**  
> *Under 50 milliseconds. Zero friction. No programming required.*

### The Core Revenue Thesis
1. **The Trap to Avoid**: Subscriptions in utility apps get severely punished on Android. Users will not pay €24/year just to toggle a light bulb.
2. **The High-Converting Model**:
   - **Free Tier**: Generous and genuinely useful (up to 3 active tags, single device toggles, LastDose 1-tap logging, QR code triggers). Enough to get raving 5-star reviews on Reddit and Google Play.
   - **TapRelay Pro (€4.99 Launch / €5.99 Regular One-Time Lifetime)**: Unlocks power automation — **Generic Webhooks**, **Home Assistant Integration**, **Action Chains (Multi-Action Sequences)**, **Smart Remote Home-Screen Widgets**, and **Tap History Diagnostics Replay**.
   - **Physical NFC Hardware Packs (€14.99–€19.99)**: Official ARBH Labs 5-pack of matte black waterproof NTAG215 stickers/pucks, bundling a free TapRelay Pro license. Margin is ~€12–14 per pack.
3. **The Architectural Force-Multiplier**:
   Instead of building 10 disjointed features, we implement **ONE unified enhancement to `ActionExecutor`** that simultaneously unlocks:
   - Generic Webhooks (HTTP GET/POST)
   - Home Assistant Service Calls
   - Action Chains (multi-device sequences)
   - Home Screen Widgets
   - QR Code Fallbacks
   - Controller Chords
   - Full Execution Diagnostics

---

## 2. Current TapRelay State

### Repository
- Location: `C:\Users\Aaron\.gemini\antigravity\scratch\taprelay`
- Current Branch: `master`
- Current Version: `0.1.5` (versionCode `16`)
- Toolchain: Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Room 2.6.1, KSP, Ktor 2.3.12, Tink 1.14.1, JDK 21.

### Verified Existing Capabilities
1. **NFC Architecture**:
   - Read & Write via `NfcManager.kt` and `NfcPayloadParser.kt`.
   - Tags store an opaque URL `https://taprelay.app/t/<tagId>`.
   - `NfcTrampolineActivity` captures `NDEF_DISCOVERED` and `VIEW` intents for `taprelay.app/t/*`.
2. **Smart Home Integrations**:
   - **Govee**: Cloud REST API (`GoveeCloudProvider.kt`), API key authentication, power/color/brightness.
   - **Tuya**: Cloud OpenAPI (`TuyaProvider.kt`), sign calculation (`TuyaSigner.kt`), token caching, device discovery.
   - **Sensibo**: Cloud API (`SensiboProvider.kt`), AC power, target temperature, fan modes.
3. **LastDose Cross-App Logging**:
   - `LastDoseClient.kt` connects to `com.lastdose.app.external.ExternalActionProvider` via signature-pinned Content Provider.
   - Allows tapping an NFC tag or pressing a gamepad button to log into LastDose in the background.
4. **Bluetooth Gamepad / Controller Architecture**:
   - `ControllerManager.kt` captures button inputs (`KeyEvent`, `MotionEvent`).
   - `RemoteModeActivity.kt` wakes the screen and displays over lock screen (`showWhenLocked="true"`) for docked controller use.
   - `RemoteModeTileService.kt` exposes a Quick Settings tile to toggle Remote Mode.
5. **Data Persistence**:
   - Room Database (`AppDatabase.kt`): `tags`, `controllers`, `place_triggers`, `tap_logs`.
   - Secure Key Storage (`SecureKeyStorage.kt`) backed by Google Tink AEAD.
6. **Execution Engine**:
   - `ActionExecutor.kt`: Thread-safe debounce (1500ms), optimistic feedback callback, haptics (`HapticsManager.kt`), time-of-day condition evaluation, and diagnostics logging into `TapLogDao`.
7. **Monetization Plumbing**:
   - `EntitlementRepository.kt`: License activation client, master PIN bypass (`96275562199744435376961556288749` grants lifetime Pro), trial support.

---

## 3. Existing Research Findings & Learnings

1. **Vendor API Policy Reality**:
   - Govee and Tuya developer APIs have personal-use restrictions. Charging directly for "Tuya access" creates unnecessary policy risk.
   - Charging for **local automation primitives** (Webhooks, Home Assistant, Action Chains, Widgets, Controller Mapping, Diagnostics) carries **ZERO vendor risk** and 100% intellectual property ownership.
2. **Physical Tag Economics**:
   - Blank NTAG215 stickers cost ~€0.15–0.25 in bulk.
   - Retail sales on Amazon/Etsy command €3–8 per tag, or €15–20 for a 5-pack.
   - Users strongly prefer buying pre-tested, high-quality tags from the app creator if it guarantees instant setup.
3. **Android 17 / Pixel 7 Behavior**:
   - Background controller interception is blocked by Android OS security when no activity has window focus. `RemoteModeActivity`'s `showWhenLocked` architecture is the only battery-efficient, policy-compliant approach.
   - Home-screen widgets must use native `RemoteViews` with unexported `BroadcastReceiver`s and `goAsync()` to avoid intrusive activity launches.

---

## 4. Competitive Findings

| Competitor | Strengths | Fatal Flaws / User Complaints | TapRelay Exploitation |
|---|---|---|---|
| **Tasker** | Infinite power, deep plugins. | "Need a computer science degree." Steep learning curve. UI looks like Android 4.0. Constant background permission issues. | Instant 15-second setup. Material 3. Tactile physical focus. |
| **MacroDroid** | Friendlier than Tasker. | Ad-supported free tier is irritating. Complex logic builders still intimidate non-technical users. | Single-purpose simplicity: Physical trigger → Done. |
| **NFC Tools / Tasks** | Great raw tag inspector. | Writes tasks onto physical tag bytes (fails on NTAG213 size limits). Requires re-scanning to edit. Shifted to hated iOS subscription. | Opaque ID on sticker; all mappings live in phone Room DB. Change action anytime without re-scanning. |
| **Home Assistant Companion** | Vast ecosystem. | NFC scanning opens the bulky HA app, takes 2–3 seconds to load. Requires running an HA server. | Executes in <50ms. Works standalone with cloud APIs OR connects to HA via 1-tap webhooks. |
| **Govee / Tuya Native Apps** | Official manufacturer apps. | 5-second splash screens, bloated shopping tabs, buried submenus, slow cold launch. | Sub-50ms bypass. Never open the bloated OEM app again. |

---

## 5. Market Gap: Where TapRelay Wins

```
                         High Complexity / High Power
                                     │
                                     │      Tasker
                                     │      Home Assistant
                                     │
      Slow / Bloated                 │                 Fast / Tactile
     ────────────────────────────────┼────────────────────────────────
      Govee / Smart Life             │                 TAPRELAY (THE SWEET SPOT)
      Google Home                    │                 - Instant Physical Input
                                     │                 - Under 50ms execution
                                     │                 - Zero-code setup
                                     │                 - Standalone + HA Bridge
                                     │
                         Low Complexity / Low Power
```

TapRelay owns the **"Fast, Tactile, Physical Smart Switch"** quadrant.

---

## 6. Ranked Candidate Feature Matrix

Each feature scored 1–10 across positive value drivers and negatively scored for risk/maintenance:

| Candidate Feature | User Value (1-10) | Willingness to Pay (1-10) | Differentiation (1-10) | Ease of Build (1-10) | Ecosystem Leverage (1-10) | Maintenance Cost (-1 to -10) | Regression Risk (-1 to -10) | Net Score |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1. Generic Webhooks (HTTP GET/POST)** | 10 | 9 | 8 | 9 | 10 | -2 | -1 | **43** |
| **2. Home Assistant Integration** | 10 | 10 | 9 | 8 | 10 | -2 | -1 | **44** |
| **3. Action Chains (Multi-Action Scenes)** | 9 | 9 | 8 | 8 | 9 | -2 | -1 | **40** |
| **4. Home Screen Widgets (Action Key & Remote)**| 9 | 8 | 8 | 8 | 8 | -2 | -1 | **38** |
| **5. Tap History & Diagnostics Replay** | 8 | 7 | 8 | 9 | 7 | -1 | -1 | **37** |
| **6. QR Code Fallback Triggers** | 8 | 6 | 8 | 9 | 7 | -1 | -1 | **36** |
| **7. Smart State Toggle (`lastKnownState`)** | 8 | 6 | 7 | 9 | 6 | -1 | -1 | **34** |
| **8. Controller Chords & Long-Press** | 8 | 7 | 9 | 6 | 6 | -3 | -2 | **31** |
| **9. JSON Backup & Restore** | 7 | 6 | 5 | 9 | 6 | -1 | -1 | **31** |
| **10. Official NFC Hardware Store Link** | 7 | 9 | 8 | 9 | 6 | -1 | -1 | **37** |

---

## 7. Top 10 Opportunities

1. **Generic Webhook & HTTP Action Engine**: One simple HTTP client unlocks thousands of services (ESPHome, Shelly, IFTTT, Zapier, Webhook.site, custom servers).
2. **Home Assistant Native Bridge**: 1-tap connection to Home Assistant via webhook or REST API, instantly supporting Zigbee, Z-Wave, Matter, and 10,000+ devices.
3. **Action Chains (Multi-Action Sequences)**: Execute multiple steps sequentially (e.g. Turn off lights → Set AC to 21°C → Log LastDose → Call Webhook).
4. **Adaptive Home-Screen Widgets**: Action Key (1x1) and Smart Remote (4x2) widgets bringing physical smart controls to the Android launcher.
5. **Tap History & Live Diagnostics Drawer**: Visual log showing last 20 executions, millisecond timing, success/failure badges, and a 1-tap **[Replay]** button.
6. **QR Code Fallback Triggers**: Instant printable/screen-scannable QR codes for any action, allowing users without NFC tags to use TapRelay on Day 1.
7. **Smart State Toggle**: Intelligent tracking of device state to seamlessly toggle power without asking the user to manually track on/off state.
8. **Controller Power Chords**: Map gamepad button combinations (`LT + A`, `LB + B`) and long-presses to expand controller utility.
9. **Zero-Cloud JSON Backup & Restore**: Clean export/import of all tag configurations and place triggers without exposing credentials.
10. **ARBH Labs NFC Hardware Starter Kit**: Direct in-app commerce for premium NTAG215 stickers, bundling lifetime Pro access.

---

## 8. Top 5 Easy Wins

These 5 features have the highest ratio of impact to implementation effort:

1. **Tap History & Diagnostics Drawer** (Effort: ~3 hours): `TapLogEntity` already records every tap! Just add a Compose bottom sheet to view and replay them.
2. **QR Code Action Generation** (Effort: ~2 hours): `NfcTrampolineActivity` already handles `https://taprelay.app/t/*`. Just add a QR generator dialog to display/share the link.
3. **Generic Webhooks** (Effort: ~4 hours): Add `TargetType.WEBHOOK` and execute standard HTTP requests using the existing Ktor client.
4. **Smart State Toggle** (Effort: ~2 hours): Use `TagEntity.lastKnownState` to invert power on toggle actions automatically.
5. **JSON Configuration Backup/Restore** (Effort: ~3 hours): Serialize Room database rows to a clean JSON document.

---

## 9. FINAL Features To Build (The 0.2.0 Release Scope)

Claude Code will implement this cohesive, high-leverage feature package:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TAPRELAY 0.2.0 RELEASE SCOPE                    │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Generic Webhook & Home Assistant Action Engine                      │
│    - Execute HTTP GET, POST, PUT with custom headers & JSON payload.  │
│    - Dedicated Home Assistant preset (Webhook or Long-Lived Token).    │
│                                                                        │
│ 2. Action Chains (Multi-Action Sequences / Scenes)                     │
│    - Chain multiple actions onto a single trigger with optional delay. │
│    - Supports mixed targets (Tuya + Webhook + LastDose + Sensibo).     │
│                                                                        │
│ 3. Tap History & Diagnostics Drawer with 1-Tap Replay                 │
│    - View last 30 executions with millisecond duration and error info. │
│    - 1-tap Replay button to test or re-fire without physical tag.      │
│                                                                        │
│ 4. QR Code Fallback Action Triggers                                    │
│    - Generate and display scannable QR code for any mapped action.     │
│    - Scanned via system camera / QR scanner; triggers instantly.       │
│                                                                        │
│ 5. Home Screen Widgets (Action Key & Smart Remote)                    │
│    - Implement the widgets specified in WIDGET_SYSTEM_CLAUDE_HANDOFF.md│
│                                                                        │
│ 6. TapRelay Pro Paywall & Licensing Activation                         │
│    - Enable Pro gating for Webhooks, Chains, and Multi-Tile Widgets.   │
│    - Support €4.99 one-time unlock and Master PIN bypass.              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Why These Won (The Unifying Architecture)

Notice the architectural genius of this combination:

1. **Shared Execution Pipeline**:
   - `ActionExecutor.kt` remains the single point of execution.
   - When Webhooks and Chains are added, they immediately work across **NFC tags**, **gamepad controller buttons**, **home screen widgets**, **QR code scans**, and **in-app test taps**.
2. **Zero Maintenance Burden**:
   - No servers to host or pay for.
   - Webhooks and Home Assistant calls run directly from the phone's network stack.
3. **Overwhelming "OOO THAT'S USEFUL" Value**:
   - A user can stick an NFC tag on their desk, tap it, and simultaneously dim their Govee lights, call Home Assistant to mute their PC, and log water in LastDose. That is true magic.
4. **Immediate Monetization Justification**:
   - Free users get 3 single-device tags (Tuya/Govee/LastDose).
   - Pro unlocks unlimited tags, Webhooks, Home Assistant, multi-action Chains, and Smart Remote widgets. The value proposition is instantly understood.

---

## 11. Free vs Pro Recommendation

| Feature | Free Tier | TapRelay Pro (€4.99 One-Time) |
|---|:---:|:---:|
| **Active Mapped Tags** | Up to 3 tags | **Unlimited** |
| **Smart Home Integrations (Tuya, Govee, Sensibo)**| Included | **Included** |
| **LastDose Cross-App Logging** | Included | **Included** |
| **QR Code Action Generation** | Included | **Included** |
| **Generic Webhooks (HTTP GET/POST)** | 1 basic webhook | **Unlimited, custom headers, body** |
| **Home Assistant Integration** | 1 basic call | **Unlimited entities & services** |
| **Action Chains (Multi-Action Scenes)** | Locked | **Unlimited chains (up to 8 steps)** |
| **Home Screen Widgets** | 1x1 Action Key | **1x1 Action Key + 4x2 Smart Remote** |
| **Tap History & Diagnostics** | Last 3 events | **Full history + 1-tap Replay** |
| **Controller Mapping** | 1 profile (4 buttons) | **Unlimited profiles & chords** |
| **Backup & Restore** | Locked | **Full JSON Export & Import** |

---

## 12. Pricing Recommendation

1. **Launch Pricing**: **€4.99 one-time** (stamped with a "Founder Lifetime Unlock" badge in the UI).
2. **Standard Pricing**: **€5.99 one-time**.
3. **No Consumer Subscription**: Reject the €1.99/month model. It causes high churn and negative reviews for utility apps. A high-converting one-time purchase yields immediate cash flow and stellar app store ratings.
4. **Master Admin Pin Bypass**:
   Entering `96275562199744435376961556288749` into the license activation input MUST always immediately activate unrestricted lifetime Pro (`tier = "admin"`, `expires_at = 0`).

---

## 13. NFC Commerce Opportunities

1. **In-App "Get Official Tags" Card**:
   - Place a sleek Material 3 card inside the Tag Management screen:
     *"Need high-performance NFC stickers? Get the ARBH Labs 5-Pack (Waterproof NTAG215, pre-tested, includes free TapRelay Pro unlock) — €14.99"*.
   - Links to `https://arbhlabs.com/taprelay/tags` (or Shopify/Etsy checkout).
2. **Pre-Configured Room Packs**:
   - "Bedside Pack" (Sleep scene, Alarm, LastDose log).
   - "Desk Pack" (Focus lighting, PC webhook, Spotify launch).

---

## 14. Architecture Plan

```
                                  [ TRIGGER SOURCE ]
           ┌─────────────────┬──────────────────┬─────────────────┬───────────────┐
           │     NFC Tap     │    Controller    │   Home Widget   │  QR Code Scan │
           └────────┬────────┴────────┬─────────┴────────┬────────┴───────┬───────┘
                    │                 │                  │                │
                    ▼                 ▼                  ▼                ▼
     ┌────────────────────────────────────────────────────────────────────────────┐
     │                      ActionExecutor.executeByTagId()                       │
     │                      - Debounce check (1500ms)                             │
     │                      - Haptics: Vibrate Click                              │
     │                      - Check Entitlement (Pro gate)                        │
     └─────────────────────────────────────┬──────────────────────────────────────┘
                                           │
                                           ▼
                    ┌──────────────────────────────────────────────┐
                    │ Resolve TargetType from TagEntity            │
                    └──────┬──────────────┬──────────────┬─────────┘
                           │              │              │
         ┌─────────────────┘              │              └──────────────────┐
         ▼                                ▼                                 ▼
┌──────────────────┐            ┌──────────────────┐              ┌──────────────────┐
│ DEVICE / SCENE   │            │ WEBHOOK / HA     │              │ ACTION_CHAIN     │
│ - Tuya Provider  │            │ - Ktor HTTP      │              │ - Sequential     │
│ - Govee Provider │            │ - POST/GET/PUT   │              │   sub-actions    │
│ - Sensibo Prov.  │            │ - Custom Headers │              │ - Optional delays│
│ - LastDose Client│            │ - JSON Body      │              │ - Rollback safe  │
└────────┬─────────┘            └────────┬─────────┘              └────────┬─────────┘
         │                               │                                 │
         └───────────────────────────────┼─────────────────────────────────┘
                                         │
                                         ▼
                    ┌──────────────────────────────────────────────┐
                    │ Outcomes & Logging                           │
                    │ - Insert TapLogEntity in Room DB             │
                    │ - Update TagEntity.lastKnownState            │
                    │ - Haptics: Vibrate Success / Error           │
                    │ - Notify Widgets: TapRelayWidgetUpdater      │
                    │ - Render Toast / In-App Notification         │
                    └──────────────────────────────────────────────┘
```

---

## 15. Data Model Changes

### Room Database (`AppDatabase.kt`) Migration from version 4 to 5:

1. **`TargetType.kt` Additions**:
   ```kotlin
   enum class TargetType {
       DEVICE,
       SCENE,
       LASTDOSE_LOG,
       WEBHOOK,         // [NEW]
       HOME_ASSISTANT,  // [NEW]
       ACTION_CHAIN     // [NEW]
   }
   ```

2. **`TagEntity.kt` Column Extensions**:
   ```kotlin
   // Webhook & Home Assistant parameters
   val webhookUrl: String? = null,
   val webhookMethod: String? = null, // "GET", "POST", "PUT"
   val webhookHeadersJson: String? = null, // JSON map of headers
   val webhookBody: String? = null,

   // Home Assistant specific parameters
   val haEntityId: String? = null,
   val haDomain: String? = null, // "light", "switch", "scene", etc.
   val haService: String? = null, // "toggle", "turn_on", "turn_off"

   // Action Chain configuration (serialized JSON array of ActionStep)
   val actionChainJson: String? = null
   ```

3. **New Model: `ActionStep.kt`**:
   ```kotlin
   @Serializable
   data class ActionStep(
       val stepId: String = UUID.randomUUID().toString(),
       val label: String,
       val targetType: TargetType,
       val providerId: String? = null,
       val deviceId: String? = null,
       val actionType: ActionType? = null,
       val delayMs: Long = 0L,
       val webhookUrl: String? = null,
       val lastDoseItemId: Long? = null,
       val lastDoseAmount: String? = null
   )
   ```

4. **Room Migration Definition**:
   ```kotlin
   val MIGRATION_4_5 = object : Migration(4, 5) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE tags ADD COLUMN webhookUrl TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN webhookMethod TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN webhookHeadersJson TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN webhookBody TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN haEntityId TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN haDomain TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN haService TEXT")
           db.execSQL("ALTER TABLE tags ADD COLUMN actionChainJson TEXT")
       }
   }
   ```

---

## 16. Security & Privacy Model

1. **Webhook & Token Protection**:
   - Webhook authentication headers (e.g. `Bearer <ha_token>`) and sensitive endpoints are encrypted using Google Tink AEAD via `SecureKeyStorage.kt` before persisting to disk.
   - Plaintext tokens are never logged to `TapLogEntity` or logcat. `TapLogEntity.actionDescription` records only the host domain (e.g. `POST https://homeassistant.local:8123/...`) with sensitive query parameters and auth headers stripped.
2. **Local Execution & No Third-Party Telemetry**:
   - Webhooks and Home Assistant requests originate directly from the user's phone. No ARBH Labs relay server sits in the middle.
3. **Replay & Intent Forgery Protection**:
   - `NfcTrampolineActivity` validates that scanned tag IDs match known registered tags or routes to the registration flow. It never executes arbitrary unverified URLs passed via external intents.

---

## 17. Android Background Strategy (Android 15–17 Compliance)

1. **Zero Foreground Service Abuse**:
   - TapRelay does NOT keep a permanent battery-draining Foreground Service running in the background.
   - Tag taps and widget clicks run via short-lived `BroadcastReceiver.goAsync()` or transient Activity dispatch, completing all IO network work in < 150ms.
2. **Controller Input Architecture**:
   - Bluetooth controller input is handled legitimately through `RemoteModeActivity.kt` (`showWhenLocked="true"`, `turnScreenOn="true"`), complying 100% with Android 14+ background activity start restrictions.
3. **App Links & Deep Linking**:
   - Uses verified domain `https://taprelay.app/t/*` registered in `AndroidManifest.xml` with `android:autoVerify="true"`.

---

## 18. UX Specifications

### 1. Webhook & Home Assistant Creator Sheet
- **Entry**: Tapping "Add Action" in Tag Editor offers:
  - *Smart Home Device* (Tuya, Govee, Sensibo)
  - *LastDose Quick Log*
  - *Home Assistant Service* [Pro]
  - *Webhook / HTTP Request* [Pro]
  - *Multi-Action Chain* [Pro]
- **Home Assistant Preset**:
  - Fields: HA Server URL (e.g., `http://192.168.1.100:8123` or Nabu Casa URL), Long-Lived Access Token, Entity ID (`light.desk`), Service (`Toggle`).
  - Tactile "Test Connection" button with real-time feedback.

### 2. Action Chain Builder
- Visual vertical step builder with drag-and-drop or reorder buttons.
- Step summary pills with icons (e.g., `[Light] Desk Lamp OFF` → `[Clock] Wait 200ms` → `[Pill] Log Melatonin`).
- "Test Chain" button that runs all steps with progress indicators.

### 3. Tap History & Diagnostics Drawer
- Accessible via a clean clock icon on the top app bar.
- Lists last 30 events with:
  - Timestamp (e.g. "10:42:15 PM")
  - Friendly tag name & icon
  - Outcome badge (`SUCCESS · 0.24s` in emerald or `FAILED · Timeout` in amber)
  - Detailed error expandable on tap (e.g. `HTTP 502 Bad Gateway: Tuya Cloud unreachable`)
  - Prominent **[Replay Action]** button.

### 4. QR Code Dialog
- Accessible from any tag's options menu: "Show QR Code".
- Renders high-contrast QR code pointing to `https://taprelay.app/t/<tagId>`.
- Includes "Print / Share QR Card" button to save or send an image of the action card.

### 5. Pro Upgrade Modal
- Triggered when configuring a 4th tag, adding a Webhook, building an Action Chain, or placing a Smart Remote widget.
- Header: *"Unlock TapRelay Pro"*.
- Highlights:
  - *Unlimited physical tags & widgets*
  - *Home Assistant & Custom Webhooks*
  - *Multi-device Action Chains*
  - *Diagnostics & 1-tap Replay*
- CTA: **"Unlock Lifetime Pro — €4.99"** (or "Activate License / Admin PIN").

---

## 19. Migration & Compatibility Requirements

1. **Backward Compatibility**:
   - All existing tags written with version `0.1.5` or earlier must continue to execute without modification.
   - Any tag with `targetType == DEVICE` or `SCENE` or `LASTDOSE_LOG` behaves identically.
2. **Room Database**:
   - Increment `AppDatabase` version from `4` to `5`.
   - Add `MIGRATION_4_5` in `AppDatabase.kt` and register it in `ServiceLocator.kt`.
3. **Signing & Permissions**:
   - Preserve `com.lastdose.app.permission.EXTERNAL_ACTION` for LastDose integration.
   - No new dangerous Android permissions are required!

---

## 20. Detailed Implementation Order for Claude Code

Claude Code should execute the implementation in this strictly ordered sequence:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: Database Migration & Model Extensions                         │
│ 1. Update TargetType.kt to include WEBHOOK, HOME_ASSISTANT, ACTION_CHAIN│
│ 2. Extend TagEntity.kt with webhook and action chain columns.          │
│ 3. Create ActionStep.kt model.                                         │
│ 4. Write MIGRATION_4_5 in AppDatabase.kt and test migration.           │
│ 5. Verify compilation: .\gradlew.bat compileDebugKotlin                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 2: Webhook & Home Assistant Action Engine                        │
│ 1. Add runWebhook() and runHomeAssistant() methods in ActionExecutor.kt│
│ 2. Leverage existing Ktor HttpClient for HTTP GET/POST/PUT.            │
│ 3. Log timing and outcome to TapLogEntity.                             │
│ 4. Add unit tests for Webhook & HA execution.                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 3: Action Chains Engine                                          │
│ 1. Add runActionChain() in ActionExecutor.kt.                          │
│ 2. Execute steps sequentially with optional delayMs.                   │
│ 3. Gracefully handle individual step errors without crashing.          │
│ 4. Add unit test verifying multi-step chain execution.                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 4: UI Components (History Drawer, QR Generator, Creator Sheets) │
│ 1. Build TapHistorySheet.kt Compose modal observing TapLogDao.         │
│ 2. Build QrCodeDialog.kt using Android's native Bitmap/Canvas QR draw. │
│ 3. Build WebhookConfigSheet.kt and ActionChainEditor.kt.               │
│ 4. Update TagEditor to support selecting new target types.             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5: Home Screen Widgets Integration                               │
│ 1. Implement ActionButtonWidget and SmartRemoteWidget as designed in   │
│    WIDGET_SYSTEM_CLAUDE_HANDOFF.md.                                    │
│ 2. Wire widgets to ActionExecutor.executeByTagId().                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 6: Entitlement & Pro Paywall Polish                              │
│ 1. Update EntitlementRepository.kt:                                    │
│    Add TapRelayProFeature.WEBHOOKS, ACTION_CHAINS, UNLIMITED_TAGS.     │
│ 2. Gate features cleanly; verify Master PIN bypass                     │
│    (96275562199744435376961556288749).                                 │
│ 3. Build PaywallDialog.kt Compose screen.                              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 7: Verification, Tests & Release Notes                           │
│ 1. Run all unit tests: .\gradlew.bat testDebugUnitTest                 │
│ 2. Create docs/RELEASE_0.2.0.md.                                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Tests Required

1. **`ActionExecutorTest.kt`**:
   - `testExecuteWebhook_success`: Mocks HTTP 200 response; verifies `TapLogEntity` records `success = true` with duration.
   - `testExecuteWebhook_timeout`: Mocks HTTP timeout; verifies graceful error handling, haptic error vibration, and logged failure.
   - `testExecuteHomeAssistant_toggle`: Verifies correct URL formatting (`/api/services/light/toggle`) and Authorization header.
   - `testExecuteActionChain_sequential`: Verifies multiple steps execute in exact order with specified delays.
2. **`DatabaseMigrationTest.kt`**:
   - Verifies migration from DB version 4 to 5 preserves all existing tag records.
3. **`EntitlementTest.kt`**:
   - Verifies master PIN `96275562199744435376961556288749` immediately unlocks Pro.
   - Verifies free tier restricts to 3 active tags.

---

## 22. Real-Device Acceptance Tests (Pixel 7 / Android 17)

| Test ID | Test Flow | Expected Real-Device Result |
|---|---|---|
| **RD-01** | Tap Webhook Tag | Executes HTTP POST in background in <100ms; produces crisp success haptic buzz; Tap History records green success pill. |
| **RD-02** | Tap Home Assistant Tag | Toggles target entity on local HA instance; no browser or external app opens. |
| **RD-03** | Tap Action Chain Tag | Dims desk lamp, waits 200ms, logs water in LastDose; LastDose widget updates immediately. |
| **RD-04** | Scan QR Code via Camera | Point Pixel camera at generated QR; tap pop-up link; action fires immediately via `NfcTrampolineActivity`. |
| **RD-05** | Tap Widget Action Key | Tap 1x1 home-screen widget; executes mapped action in <50ms with haptic buzz. |
| **RD-06** | Master PIN Activation | Enter `96275562199744435376961556288749`; app displays "Founder Pro Active" badge; all Pro gates lift instantly. |

---

## 23. Regression Checklist

- [ ] NFC reading, writing, and tag locking must remain 100% operational.
- [ ] Tuya, Govee, and Sensibo cloud integrations must continue executing reliably.
- [ ] LastDose cross-app logging via `ExternalActionProvider` must not break.
- [ ] Controller input listening in `RemoteModeActivity` must continue to function.
- [ ] Edge-to-edge system Back navigation (fixed in 0.1.5) must not be regressed.
- [ ] All sensitive tokens must remain encrypted under Google Tink AEAD.

---

## 24. Release Acceptance Criteria

- [ ] Clean compilation with zero warnings: `.\gradlew.bat compileDebugKotlin`.
- [ ] 100% passing unit test suite: `.\gradlew.bat testDebugUnitTest`.
- [ ] Database migration from v4 to v5 passes cleanly without data loss.
- [ ] Pro paywall renders with clear value propositions and Master PIN bypass.
- [ ] Documentation updated in `docs/RELEASE_0.2.0.md`.

---

## 25. Deferred Backlog (Post-0.2.0 Candidates)

1. **Matter Local SDK Integration**: Defer until Android Matter client libraries mature and reduce dependency footprint.
2. **Cloud Household Sync**: Defer until ARBH Labs cloud backend infrastructure is deployed.
3. **Tasker Plugin Broadcasts**: Defer; generic Webhooks already satisfy the power-user audience.

---

## READY FOR CLAUDE AUTONOMOUS TAKEOVER
