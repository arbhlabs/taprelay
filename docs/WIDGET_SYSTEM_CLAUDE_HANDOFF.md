# ARBH Labs — Android Home Screen Widget System Architecture & Implementation Handoff

**Target Applications**: LastDose (`com.lastdose.app`) & TapRelay (`com.arbhlabs.taprelay`)  
**Target Platform**: Android 16 & 17 / Pixel 7 (Minimum SDK 26 for LastDose, 30 for TapRelay)  
**Design Standard**: Google Material You / Material 3 Adaptive Widget Specification  
**Document Status**: AUTHORITATIVE R&D SPECIFICATION FOR CLAUDE CODE EXECUTION  
**Primary Architect**: R&D / Product Architecture Lead, ARBH Labs  
**Date**: September 5, 2026  

---

## Executive Summary & Target Emotional Reaction

> **Target User Reaction**: *"OOO — THAT’S NICE AND ACTUALLY USEFUL."*

Widgets on Android are all too often passive, static informational billboards or generic AI-generated rectangles that merely mirror the app launcher icon with a number.

This specification changes that paradigm for ARBH Labs. For **LastDose**, the home screen becomes a high-frequency, friction-free tracking terminal where logging happens in 100ms with zero app cold-starts and live zero-battery chronometers. For **TapRelay**, the home screen becomes a physical/digital tactile remote control surface that turns the Pixel into a programmable smart command center.

This handoff provides the complete, unambiguous architectural and implementation specification for Claude Code to execute end-to-end without needing to pause for routine product or architectural decisions.

---

## 1. Current Architecture Findings

### LastDose (`C:\Users\Aaron\AndroidStudioProjects\LASTDOSE`)
- **Language / Runtime**: Pure Java 17, Android compileSdk 37, targetSdk 37, minSdk 26. No Jetpack Compose.
- **Data Persistence**: SQLite database via `Store.java` (`items`, `events`, `metric_snapshots`, `connected_devices`, `safety`).
- **Existing Widget Codebase**:
  - `BaseLastDoseWidget.java`: Abstract `AppWidgetProvider` subclass executing background rendering via single-thread executor daemon (`LastDoseWidgetRender`). Handles `APPWIDGET_UPDATE`, `MY_PACKAGE_REPLACED`, `BOOT_COMPLETED`, and `onAppWidgetOptionsChanged`.
  - `LastDoseWidget.java`: "Compact" timer widget (`@xml/last_dose_widget`).
  - `LastDoseQuickWidget.java`: "Quick" single-log widget with a large "LOG NOW" button (`@xml/last_dose_quick_widget`).
  - `LastDoseHistoryWidget.java`: "Dashboard" widget with up to 5 static vertical `LinearLayout` rows (`@xml/last_dose_history_widget`).
  - `LastDoseWidgetSystem.java`: Projection layer reading SQLite `Store`. Maps items, loads events, filters out hidden logs (`tracker_hidden_<id>`), evaluates privacy mode (`timerOnly` or `isPrivate`), and formats accessibility strings.
  - `WidgetUpdater.java`: Broadcast fan-out triggered on canonical logs, updates, and package replacement.
  - `WidgetConfigActivity.java`: Activity allowing user to bind a widget instance to a specific tracker or mode.
- **Chronometer Presentation**: Already leverages Android's native `RemoteViews.setChronometer(viewId, baseTime, null, true)` with `SystemClock.elapsedRealtime() - elapsedMs`. This is **crucial**: it runs inside the launcher process (`com.google.android.apps.nexuslauncher` / `SystemUI`), rendering live ticking seconds with zero wakeups or battery drain on the app.
- **Safety / Interval Checks**: `BaseLastDoseWidget.logNow()` checks `item.intervalMinutes`. If a mandatory interval spacing is required, it routes to `MainActivity.intervalLogIntent` for safety confirmation instead of blind double-dosing.
- **Fasting System**: Fully built, deterministic, evidence-grounded `FastingEngine.java` and `FastingProtocol.java` (16:8, 18:6, OMAD, 20:4, etc.) in `com.lastdose.app.fasting`.
- **Insights & Cadence**: Local-first `InsightEngine.java` and `SmartReminderPredictor.java` calculating typical cadence, expected windows, peak time-of-day, and overdue alerts.
- **Cross-App Entry Point**: `com.lastdose.app.external.ExternalActionProvider` exposes a signature-pinned `call()` endpoint for `log`, `items`, and `contract` guarded by `com.lastdose.app.permission.EXTERNAL_ACTION`.

### TapRelay (`C:\Users\Aaron\.gemini\antigravity\scratch\taprelay`)
- **Language / Runtime**: 100% Kotlin 2.0, Jetpack Compose, Material 3, compileSdk 35, targetSdk 35, minSdk 30.
- **Data Persistence**: Android Room database (`AppDatabase.kt`) containing `TagEntity`, `ControllerEntity`, `PlaceTriggerEntity`, `TapLogEntity`.
- **Current Widget Infrastructure**: None. TapRelay currently has Quick Settings Tiles (`RemoteModeTileService`), Quick Controls bottom sheet (`QuickControls.kt`), and NFC Trampoline (`NfcTrampolineActivity`), but zero AppWidget providers.
- **Action Pipeline**: Unified `ActionExecutor.kt` handling Smart Home providers (Tuya, Sensibo, Govee), LastDose logging via `LastDoseClient.kt`, scenes, and device toggles with debouncing, haptic feedback (`HapticsManager.kt`), and tap history logging (`TapLogDao`).
- **Entitlements**: `EntitlementManager.kt` gates advanced capabilities (unlimited tags, time-of-day conditions). Master Admin PIN `96275562199744435376961556288749` is honored across all checks.

---

## 2. External Research & Platform Findings (Android 16/17 & Pixel 7 Target)

### Jetpack Glance vs Traditional AppWidget Architecture
| Criterion | Jetpack Glance (`androidx.glance`) | Traditional `RemoteViews` + XML Layouts |
|---|---|---|
| **LastDose Suitability** | **POOR**. Would require pulling Compose runtime & compiler into a pure Java codebase, inflating APK size by ~4MB. Cannot do zero-battery `Chronometer` count-up natively. | **OPTIMAL**. Native Java compatibility, instant cold start, zero overhead, native `setChronometer()` zero-battery ticking. |
| **TapRelay Suitability** | **VIABLE BUT RISKY**. Glance has slow cold-start IPC latency on launcher bind, higher memory overhead on low RAM, and complex state synchronization when reacting to rapid external hardware clicks. | **OPTIMAL**. Deterministic XML layouts with Kotlin view-builders. Instantaneous render, perfect sub-50ms tap-to-haptic-to-network pipeline. |
| **Decision** | Use traditional, highly-optimized `RemoteViews` with Android 12–17 modern features across **BOTH** apps for rock-solid reliability, shared architectural concepts, and zero battery vampire tendencies. |

### Modern Android 12–17 Widget Capabilities Leveraged
1. **Dynamic Color / Material You**:
   - Widgets must reference system color resources: `@android:color/system_neutral1_900` (dark surface), `@android:color/system_neutral1_50` (light surface), `@android:color/system_accent1_100` (accent container), `@android:color/system_accent1_900` (on accent), etc.
   - On Pixel running Android 12+, these tokens dynamically sample the user's active Material You wallpaper palette.
2. **System Corner Radii**:
   - Outer container corners MUST use `@android:dimen/system_app_widget_background_radius` (API 31+).
   - Inner interactive cards/buttons MUST use `@android:dimen/system_app_widget_inner_radius`.
   - On older devices (< API 31), resource qualification falls back to `24dp` outer and `12dp` inner.
3. **Multi-Size Responsive Layouts (`RemoteViews(Map<SizeF, RemoteViews>)`)**:
   - Rather than one static XML that clips or stretches, we provide discrete layout variants keyed by breakpoint sizes:
     - 2x1 (Compact bar: min width 130dp, min height 60dp)
     - 2x2 (Square tile: min width 130dp, min height 130dp)
     - 4x2 (Wide dashboard: min width 270dp, min height 130dp)
     - 4x3+ (Extended command center: min width 270dp, min height 200dp)
   - The launcher switches layouts smoothly in real-time during resizing without app process IPC.
4. **Target Cell Sizing (`res/xml/*.xml`)**:
   - Utilize modern attributes: `android:targetCellWidth="2"`, `android:targetCellHeight="2"`, `android:minResizeWidth="130dp"`, `android:minResizeHeight="60dp"`.
5. **Direct Actions (Zero Trampoline)**:
   - Tapping an action button fires a `PendingIntent.getBroadcast()` to an explicit internal `BroadcastReceiver`.
   - The receiver calls `goAsync()`, offloads execution to an IO background thread, runs the action, updates the widget projection, and finishes. No disruptive Activity launches over the home screen unless explicit review or PIN entry is required!

---

## 3. Exact Widget Set Selected for LastDose

We reject bloat. We select four tightly focused, deeply valuable widgets:

### 1. LastDose "Quick Dose" (Single Tracker)
- **Default Grid**: 2x1 (Expandable to 2x2, 3x2)
- **Purpose**: Ultra-fast monitoring and 1-tap logging for the user's primary medication, supplement, or daily habit.
- **Key Information**:
  - Tracker Name & Category Glyph
  - Live ticking elapsed Chronometer ("3h 42m")
  - Contextual status line ("Last: 500 mg • 4:15 PM" or "Today: 1,500 mg")
  - Prominent, high-contrast **"LOG NOW"** action pill
- **Smart Safety**: If interval spacing is violated (e.g. taken 20 min ago with a 4h minimum interval), the button text changes to "WAIT 3h 40m" in amber, and tapping it opens the safety review dialog in `MainActivity` instead of blind double-logging.

### 2. LastDose "Dose Commander" (Multi-Log Dashboard)
- **Default Grid**: 4x2 (Expandable to 4x3, 4x4)
- **Purpose**: Complete overview of active regimen with per-item direct logging.
- **Key Information**:
  - Header: Active regimen status, total logged today count, quick [+] launch button.
  - 3 to 5 Tracker Rows (responsive to vertical height):
    - Tracker glyph and name
    - Live ticking Chronometer per tracker
    - Contextual last dosage
    - Dedicated mini **[+ Log]** button per row for immediate background logging
  - Tapping the row text opens that specific tracker's history/stats in `MainActivity`.

### 3. LastDose "Smart Cadence / Up Next"
- **Default Grid**: 3x2 (Expandable to 4x2)
- **Purpose**: Contextual intelligence powered by `InsightEngine` and `SmartReminderPredictor`.
- **Key Information**:
  - Dynamically surfaces the log that needs attention *right now*:
    - If in an expected window: "Window Open: Evening Magnesium (8:00 PM – 9:30 PM)"
    - If overdue: "Overdue by 35m: Blood Pressure" (Urgent accent)
    - If all scheduled tasks done: Surfaces the longest-running active timer or highest streak habit.
  - Direct Action: **[Log Expected Dose]** or **[Snooze 30m]**.
  - Transparency badge: "Based on your 14-day median cadence (92% confidence)". Zero medical claims.

### 4. LastDose "Fasting Companion"
- **Default Grid**: 3x2 (Expandable to 4x2, 2x2)
- **Purpose**: Real-time fasting tracker powered by `FastingEngine` and `FastingProtocol`.
- **Key Information**:
  - Active Protocol ("16:8 LeanGains")
  - Live Fasting Chronometer ("14h 22m elapsed")
  - Physiological Phase Badge ("Metabolic switch", "Fat mobilisation & ketone rise")
  - Visual Progress Bar (Target hours vs elapsed)
  - Action Controls:
    - While fasting: **[Break Fast]** (logs meal/end fast)
    - While eating window: **[Start Fast]** (initiates new fasting cycle)

---

## 4. Exact Widget Set Selected for TapRelay

### 1. TapRelay "Action Key" (Single Quick Remote)
- **Default Grid**: 1x1 or 2x1
- **Purpose**: Direct physical/digital trigger mapped to any configured TapRelay action.
- **Key Information**:
  - Action / Device Friendly Name ("Desk Lamp", "Goodnight Scene", "Sensibo AC 21°C", "Log Espresso")
  - Icon & Current State Indicator (ON / OFF / Running / Last Run)
  - Full tactile touch surface: Tapping fires the action via `ActionExecutor.executeByTagId()` in < 50ms with system haptic feedback.

### 2. TapRelay "Smart Remote" (Command Surface)
- **Default Grid**: 4x2 (Expandable to 4x3, 3x2)
- **Purpose**: A programmable, clean physical command center for smart home devices, scenes, and LastDose cross-app triggers.
- **Key Information**:
  - 4 to 8 tactile modular control tiles (arranged in a 2x2, 4x1, or 4x2 grid).
  - Each tile supports:
    - Smart Home Toggle (Light ON/OFF with state accent)
    - Scene Execution (One-tap trigger with momentary active animation)
    - LastDose Log (e.g. "Water +250ml", "Creatine 5g")
  - Header: Active Place indicator ("At Home") and Quick Replay button.

### 3. TapRelay "Controller & NFC Companion"
- **Default Grid**: 2x2
- **Purpose**: Status and fast switching for TapRelay's unique hardware controller and NFC triggers.
- **Key Information**:
  - Hardware Connection status ("Xbox Controller: Connected", or "NFC Ready")
  - 1-tap toggle into "Remote Mode" (lock-screen overlay mode for docked gamepad use)
  - Recent trigger activity pill with 1-tap **[Replay]** button.

---

## 5. UX Specifications & Design Tokens

### Visual Hierarchy & Typography
- **Primary Timers**: Large, bold tabular numerals (`sans-serif`, 32sp–36sp on 2x2 / 4x2).
- **Titles / Tracker Names**: High-legibility medium weight (`sans-serif-medium`, 15sp–16sp), ellipsized with single line constraint.
- **Status / Details**: Secondary legibility (`sans-serif`, 12sp), dimmed contrast.
- **Action Buttons / Pills**: High-contrast, tactile capsules with bold uppercase text (`12sp–13sp`) or clear Material icons. Minimum touch target is **48dp × 48dp** per Android accessibility standards.

### Color Tokens (Material You / Dynamic Theme)
All widgets MUST use Android 12+ dynamic color attributes in XML layouts (`res/values-v31/widget_colors.xml`):
```xml
<!-- Background surface matching launcher palette -->
<color name="widget_bg_surface">@android:color/system_neutral1_900</color>
<color name="widget_inner_surface">@android:color/system_neutral2_800</color>
<color name="widget_accent_primary">@android:color/system_accent1_200</color>
<color name="widget_accent_on_primary">@android:color/system_accent1_900</color>
<color name="widget_accent_secondary">@android:color/system_accent2_100</color>
<color name="widget_text_primary">@android:color/system_neutral1_50</color>
<color name="widget_text_secondary">@android:color/system_neutral2_200</color>
<color name="widget_warning">#FFB74D</color>
<color name="widget_error">#E57373</color>
```
*Day/Night qualification* provides matching dark/light mode rendering automatically.

### Shape & Corner Radius
- Outer widget container: `android:background="@drawable/widget_surface"` where `widget_surface.xml` uses `<corners android:radius="@android:dimen/system_app_widget_background_radius" />`.
- Inner buttons/cards: `<corners android:radius="@android:dimen/system_app_widget_inner_radius" />`.

---

## 6. Responsive Size & State Behavior

Android launchers (especially Pixel Launcher) place widgets on variable grid densities (4x5, 5x5, 6x5) depending on display size and scaling. Widgets MUST NOT clip or produce ellipsis cascades.

### Breakpoint Matrix
| Breakpoint Key | Min Width (dp) | Min Height (dp) | Layout Used | Render Strategy |
|---|---|---|---|---|
| **Compact (2x1)** | 130 | 50 | `widget_quick_compact.xml` | Single row: Icon, Name, Timer, small Log button. Details hidden. |
| **Normal (2x2)** | 130 | 130 | `widget_quick_normal.xml` | Stacked: Header, Name, large Chronometer, Last dosage detail, full-width Action Pill. |
| **Wide (4x1)** | 270 | 50 | `widget_dashboard_row.xml` | Horizontal strip: 3 compact trackers side-by-side. |
| **Dashboard (4x2)** | 270 | 130 | `widget_dashboard_normal.xml` | Header with total count + 3 complete tracker rows with direct log buttons. |
| **Expanded (4x3+)** | 270 | 220 | `widget_dashboard_expanded.xml` | Header with search/add + 5 complete tracker rows + insights pill. |

### Implementation via `RemoteViews(Map<SizeF, RemoteViews>)`
Claude Code must implement the responsive sizing using the modern multi-size RemoteViews mapping:
```java
Map<SizeF, RemoteViews> viewMapping = new ArrayMap<>();
viewMapping.put(new SizeF(130f, 50f), renderCompact(context, snapshot));
viewMapping.put(new SizeF(130f, 130f), renderNormal(context, snapshot));
viewMapping.put(new SizeF(270f, 130f), renderWide(context, snapshot));
RemoteViews responsiveViews = new RemoteViews(viewMapping);
manager.updateAppWidget(widgetId, responsiveViews);
```

---

## 7. Configuration UX

### LastDose Configuration Flow
1. **Trigger**: Added from launcher picker, or tapped via the widget "•••" settings gear.
2. **Component**: `WidgetConfigActivity.java` (already present, to be enhanced).
3. **Configuration Options**:
   - **Target Selection**:
     - *Primary Tracker* (automatically follows app's active primary tracker)
     - *Specific Tracker* (picker list showing all active, unhidden trackers with their icons)
     - *Fasting Engine* (for Fasting Companion widget)
     - *Smart Cadence* (auto-predicted)
   - **Display Options**:
     - *Show Today's Total* (Toggle: On/Off)
     - *Privacy Mode* (Toggle: Hide item name and dosage; show timer only)
     - *Require Confirmation* (Toggle: If On, opens app review; if Off, logs immediately in background)
4. **Finish Contract**: Sets `RESULT_OK` with `EXTRA_APPWIDGET_ID`, saves config to `lastdose_widget_system` prefs, and immediately triggers `WidgetUpdater.refresh(context)`.

### TapRelay Configuration Flow
1. **Component**: `TapRelayWidgetConfigActivity.kt` (New).
2. **Configuration Options**:
   - **For Action Key (1x1)**:
     - Searchable list of all registered tags/devices/scenes in TapRelay Room DB.
     - Also lists available LastDose quick actions.
     - Custom label and color accent override.
   - **For Smart Remote (4x2)**:
     - 4 or 6 slot picker grid.
     - Tap each slot to assign an action or leave blank.
3. **Finish Contract**: Saves mapping to `taprelay_widget_prefs` and updates widget immediately.

---

## 8. Data & State Architecture

```
                                  [ User Touch on Home Screen ]
                                                │
                                                ▼
                         ┌──────────────────────────────────────────────┐
                         │   PendingIntent.getBroadcast()               │
                         │   (WidgetActionReceiver - unexported)        │
                         └──────────────────────┬───────────────────────┘
                                                │
                                                ▼
                               ┌─────────────────────────────────┐
                               │  BroadcastReceiver.goAsync()     │
                               │  Offloads to Background IO Pool │
                               └────────────────┬────────────────┘
                                                │
                     ┌──────────────────────────┴──────────────────────────┐
                     ▼                                                     ▼
           [ In LastDose ]                                       [ In TapRelay ]
┌───────────────────────────────────────┐             ┌───────────────────────────────────────┐
│ Store.canonicalLog(...)               │             │ ActionExecutor.executeByTagId(...)    │
│  - Appends to events SQLite table     │             │  - Executes Tuya/Sensibo/Govee API    │
│  - Updates item last_timestamp        │             │  - Or calls LastDoseClient            │
│  - Checks interval violations         │             │  - Triggers HapticsManager.vibrate()  │
└──────────────────┬────────────────────┘             └──────────────────┬────────────────────┘
                   │                                                     │
                   ▼                                                     ▼
┌───────────────────────────────────────┐             ┌───────────────────────────────────────┐
│ WidgetUpdater.refresh(context)        │             │ TapRelayWidgetUpdater.refresh(context)│
│  - Builds fresh Snapshot from Store   │             │  - Queries Room TagDao                │
│  - Computes Chronometer bases         │             │  - Generates RemoteViews with new     │
│  - Calls updateAppWidget(id, views)   │             │    optimistic state                   │
└───────────────────────────────────────┘             └───────────────────────────────────────┘
                   │                                                     │
                   └──────────────────────────┬──────────────────────────┘
                                              │
                                              ▼
                         ┌──────────────────────────────────────────────┐
                         │   AppWidgetManager updates Launcher Surface  │
                         │   (Pixel Launcher / SystemUI renders frame)  │
                         └──────────────────────────────────────────────┘
```

---

## 9. Cross-App Architecture (TapRelay ↔ LastDose)

Both apps are ARBH Labs products and share a cryptographically secured integration:
1. **Content Provider Interface**:
   - Authority: `com.lastdose.app.external`
   - Class: `com.lastdose.app.external.ExternalActionProvider`
   - Gate Permission: `com.lastdose.app.permission.EXTERNAL_ACTION`
   - Signature Pinning: Verified against SHA-256 cert `7ed03578928d3b8eaec35cf064154bee4d48f528b0021dd3dcd9f3fde73ba808`.
2. **Widget Cross-Trigger**:
   - A TapRelay Smart Remote tile configured to log an item calls `LastDoseClient.log(itemId, amount, unit, intervalOverride)`.
   - `ExternalActionProvider` executes `Store.canonicalLog()` inside LastDose.
   - LastDose immediately fires its own `WidgetUpdater.refresh()`.
   - Both the TapRelay widget (showing "Logged • Just now") and any LastDose widgets on the home screen update concurrently.
   - Neither app needs to launch an activity or compromise security boundaries.

---

## 10. Privacy & Security Behavior

### Concealment of Sensitive Logs
- LastDose allows users to mark trackers as hidden (`tracker_hidden_<id>`).
- **Rule**: `LastDoseWidgetSystem.load()` strictly filters out all hidden items (`SELECT ... WHERE hidden=0` and checks `tracker_hidden_<id>`). A hidden tracker **NEVER** appears in widget configuration lists, auto-selection, or multi-log dashboards.

### Privacy Mode (Lock-Screen & Public View Defense)
- When "Privacy Mode" is enabled (or if device is locked and launcher hides secure data):
  - Tracker names are obscured to generic "Active Tracker" or "LastDose".
  - Dosages and amounts are hidden.
  - The Chronometer remains visible (e.g. "Elapsed: 4h 12m").
  - Logging from lock screen requires device unlock if safety confirmation is active.

### Master Admin Pin Bypass
- In any monetization/premium gating (e.g. custom widget themes, unlimited Smart Remote tiles):
  - Input `96275562199744435376961556288749` (stripping whitespace/dashes) MUST always be recognized as Aaron's master founder credential, instantly unlocking unrestricted lifetime Pro access (`tier = "admin"`, `expires_at = 0`).

---

## 11. Background & Update Strategy

1. **Zero-Polling Principle**:
   - **NEVER** run a continuous background thread, Handler, or `ValueAnimator` to update seconds on a widget.
   - Rely strictly on native `views.setChronometer()`. Android's Launcher framework renders the incrementing time natively without waking the app.
2. **Event-Driven Push Updates**:
   - Widgets are refreshed ONLY when a state-changing event occurs:
     - Log created / updated / deleted in LastDose.
     - Fasting window started / ended.
     - Smart Home device toggled in TapRelay.
     - Midnight day-boundary rollover (`ACTION_DATE_CHANGED`, `ACTION_TIME_SET`).
     - System reboot (`BOOT_COMPLETED`) or app update (`MY_PACKAGE_REPLACED`).
3. **Scheduled Day Refresh**:
   - `LiveTimerNotification.scheduleDayRefresh()` schedules an inexact alarm at 00:01 daily via `AlarmManager` to refresh daily totals ("Today Total").

---

## 12. Battery Strategy (Zero-Vampire Architecture)

| Component | Battery Drain Risk | Architectural Mitigation |
|---|---|---|
| **Timer Display** | HIGH if using background threads or WorkManager polling. | **ZERO IMPACT**. Handled by `RemoteViews.setChronometer()`. App process remains completely asleep (`CPU 0%`). |
| **Smart Home State** | MEDIUM if polling device APIs every minute. | **ZERO POLLING**. State is updated optimistically on tap, and synchronized only when the app opens or a push/geofence trigger occurs. |
| **Widget Layout Sizing** | LOW to MEDIUM if re-rendering on every resize event via IPC. | **ZERO IPC ON RESIZE**. Responsive layouts use `RemoteViews(Map<SizeF, RemoteViews>)`. The launcher swaps pre-computed layouts internally. |
| **Action Execution** | LOW. Broadcast with `goAsync()` runs on IO thread and terminates in < 150ms. | BroadcastReceiver lifecycle completes immediately; no persistent Foreground Services are kept alive for widgets. |

---

## 13. Compatibility & Fallback Behavior

- **Android 14 to 17 (Target)**: Full dynamic color (`system_accent`), system corner radii (`@android:dimen/system_app_widget_background_radius`), multi-size responsive `SizeF` mapping, and seamless unexported broadcast actions.
- **Android 12 to 13**: Dynamic color supported; standard system radii applied.
- **Android 8.0 to 11 (API 26–30)**:
  - System dynamic color falls back to custom ARBH Labs dark slate palette (`#1E1E24`, `#2A2A35`, emerald accent `#00E676`).
  - System radii fall back to explicit 24dp outer / 12dp inner.
  - Multi-size mapping gracefully falls back to single-layout selection based on `AppWidgetManager.getAppWidgetOptions()`.

---

## 14. Files & Components Requiring Changes

### In LastDose (`C:\Users\Aaron\AndroidStudioProjects\LASTDOSE`):
1. **`app/src/main/res/values-v31/widget_colors.xml`**: Update with complete Material 3 dynamic color tokens.
2. **`app/src/main/res/drawable/widget_surface.xml`**: Update with `@android:dimen/system_app_widget_background_radius`.
3. **`app/src/main/res/drawable/widget_action.xml` & `widget_inner_surface.xml`**: Style with dynamic pill/capsule shapes and ripple selectors.
4. **`app/src/main/res/layout/`**:
   - `widget_last_dose_quick.xml`: Modernize responsive single-log layout.
   - `widget_last_dose_history.xml`: Modernize dashboard layout with per-row mini log buttons.
   - `widget_last_dose_fasting.xml` **[NEW]**: Fasting companion layout.
   - `widget_last_dose_cadence.xml` **[NEW]**: Smart cadence / up next layout.
5. **`app/src/main/res/xml/`**:
   - `last_dose_quick_widget.xml`: Add `targetCellWidth="2"`, `targetCellHeight="1"`, `minResizeWidth`, etc.
   - `last_dose_history_widget.xml`: Add `targetCellWidth="4"`, `targetCellHeight="2"`, etc.
   - `last_dose_fasting_widget.xml` **[NEW]**: Fasting widget provider info.
   - `last_dose_cadence_widget.xml` **[NEW]**: Cadence widget provider info.
6. **`app/src/main/java/com/lastdose/app/`**:
   - `BaseLastDoseWidget.java`: Implement `RemoteViews(Map<SizeF, RemoteViews>)` builder; handle per-row action intents.
   - `LastDoseQuickWidget.java`: Enhance with safety interval countdown and state flashes.
   - `LastDoseHistoryWidget.java`: Wire per-row log actions.
   - `LastDoseFastingWidget.java` **[NEW]**: Render fasting phase and toggle action.
   - `LastDoseCadenceWidget.java` **[NEW]**: Connect `InsightEngine` projection.
   - `LastDoseWidgetSystem.java`: Add fasting projection and cadence prediction loader.
   - `WidgetConfigActivity.java`: Polish UI for tracker selection and preview.
7. **`app/src/main/AndroidManifest.xml`**: Register new widget receivers with intent-filters.

### In TapRelay (`C:\Users\Aaron\.gemini\antigravity\scratch\taprelay`):
1. **`app/src/main/res/layout/`**:
   - `widget_taprelay_action_button.xml` **[NEW]**: Tactile 1x1 / 2x1 action tile.
   - `widget_taprelay_remote_4x2.xml` **[NEW]**: Multi-button command surface.
   - `widget_taprelay_controller_companion.xml` **[NEW]**: Hardware companion widget.
2. **`app/src/main/res/xml/`**:
   - `widget_action_button_info.xml` **[NEW]**
   - `widget_smart_remote_info.xml` **[NEW]**
   - `widget_controller_companion_info.xml` **[NEW]**
3. **`app/src/main/java/com/arbhlabs/taprelay/widget/`** **[NEW PACKAGE]**:
   - `TapRelayWidgetActionReceiver.kt`: Unexported broadcast receiver that calls `ActionExecutor.executeByTagId()`.
   - `ActionButtonWidget.kt`: Provider for the single action key.
   - `SmartRemoteWidget.kt`: Provider for the multi-tile command grid.
   - `ControllerCompanionWidget.kt`: Provider for hardware status and quick replay.
   - `TapRelayWidgetConfigActivity.kt`: Compose-based or View-based action picker.
4. **`app/src/main/AndroidManifest.xml`**: Register widget providers and configuration activity.

---

## 15. Implementation Sequence for Claude Code

Execute the work in this strictly phased order:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1: LastDose Quick & Dashboard Modernization                      │
│ - Implement Material You dynamic colors in values-v31 and drawables.   │
│ - Refactor BaseLastDoseWidget to support multi-size SizeF layouts.     │
│ - Upgrade Quick Widget with safety interval countdown styling.         │
│ - Upgrade Dashboard Widget with per-row independent [+ Log] actions.   │
│ - Verify on Pixel 7: .\gradlew.bat testDebugUnitTest & compile.       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 2: LastDose Fasting & Smart Cadence Widgets                      │
│ - Create widget_last_dose_fasting.xml & FastingWidget provider.        │
│ - Hook into FastingEngine to display phase, progress, and meal toggle. │
│ - Create widget_last_dose_cadence.xml & CadenceWidget provider.        │
│ - Hook into InsightEngine to surface upcoming window / overdue item.   │
│ - Register providers in AndroidManifest.xml and verify compilation.    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 3: TapRelay Widget System Foundation                             │
│ - Create widget drawables & layout XMLs matching Material 3 standard.  │
│ - Create TapRelayWidgetActionReceiver hooking into ActionExecutor.     │
│ - Implement ActionButtonWidget (1x1 / 2x1) for single actions.         │
│ - Implement SmartRemoteWidget (4x2) for multi-tile command surface.    │
│ - Implement ControllerCompanionWidget (2x2) with Replay functionality. │
│ - Register receivers in TapRelay AndroidManifest.xml.                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 4: Cross-App Synergy & Polish                                    │
│ - Verify TapRelay widget executing LastDose log via LastDoseClient.    │
│ - Verify LastDose WidgetUpdater refreshes immediately upon cross-log.  │
│ - Verify HapticsManager feedback on widget tap.                        │
│ - Polish WidgetConfigActivity in both apps for fast setup.             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5: Verification & Regression Gates                               │
│ - Run all automated tests: LastDose & TapRelay unit test suites.       │
│ - Verify Pro entitlement bypass with master pin 96275562199744435376961556288749. │
│ - Perform layout verification across screen densities.                 │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Migration & Backward Compatibility Considerations

1. **Existing Widget ID Preservation**:
   - Existing users may already have `LastDoseWidget` or `LastDoseQuickWidget` pinned to their home screen.
   - Claude Code MUST NOT rename or remove the existing provider component names (`com.lastdose.app.LastDoseWidget`, `LastDoseQuickWidget`, `LastDoseHistoryWidget`). They must retain their existing class names and update in-place so user home screens do not break or show dead widget placeholders upon update.
2. **Preference Key Continuity**:
   - Keep `lastdose_widget_system` SharedPreferences structure (`mode_<id>`, `item_<id>`, `timer_only_<id>`).
3. **Database Schema**:
   - Zero SQLite schema alterations are required in LastDose.
   - Zero Room migration alterations are required in TapRelay (widgets read existing `TagEntity` and `TapLogEntity`).

---

## 17. Comprehensive Test Matrix

| Test ID | Area | Scenario | Expected Behavior |
|---|---|---|---|
| **T-LD-01** | LastDose Quick | Tap "LOG NOW" on 2x1 widget | Database event recorded, Chronometer resets to 00:00, "Today Total" increments, zero activity launch. |
| **T-LD-02** | LastDose Quick | Tap "LOG NOW" within mandatory interval window | Does NOT double-log; opens review activity showing remaining wait time. |
| **T-LD-03** | LastDose Dashboard | Tap mini [+ Log] on Row 2 | Logs specifically Row 2's item; only Row 2 chronometer resets. |
| **T-LD-04** | LastDose Fasting | Fast in progress; tap "End Fast" | Fasting record ends, transition to "Eating Window", widget updates phase. |
| **T-LD-05** | LastDose Privacy | Enable Privacy Mode in Settings | Widget obscures drug/tracker names and quantities; chronometers remain functional. |
| **T-LD-06** | LastDose Hidden | Tracker marked as hidden in app | Tracker immediately disappears from all dashboard and picker lists. |
| **T-TR-01** | TapRelay Action | Tap "Desk Lamp" 1x1 widget | Sends Tuya command, device toggles, widget flips accent state, haptic buzz. |
| **T-TR-02** | TapRelay Remote | Tap "Log Water" on Smart Remote | Calls `LastDoseClient`, LastDose records 250ml, LastDose widget updates concurrently. |
| **T-TR-03** | TapRelay Replay | Tap "Replay" on Controller widget | Re-executes the most recent action without opening the app. |
| **T-SYS-01** | System Lifecycle | Device reboots (`BOOT_COMPLETED`) | All widgets restore accurate chronometers and state without user intervention. |
| **T-SYS-02** | System Lifecycle | Midnight boundary crossed | Daily totals reset to 0; widgets refresh without process wakeups. |

---

## 18. Regression Gates (Must Not Break)

1. **LastDose Normal Logging**: The standard in-app logging dialogs, camera photo attachments, and history list must function identically.
2. **NFC Dispatch**: LastDose's `NfcDispatchActivity` and TapRelay's `NfcTrampolineActivity` must not have their intent-filters or dispatch priority modified.
3. **Always-On-Display (AOD)**: LastDose AOD dashboard and low-power rendering must not be affected by widget broadcasts.
4. **Smart Reminders**: Notification scheduling via `SmartReminderManager` must continue unimpeded.
5. **TapRelay Controller Mode**: `RemoteModeActivity` and gamepad button listener must retain priority over background widget execution.
6. **Smart Home Integrations**: API credentials stored in Tink / EncryptedSharedPreferences must remain completely isolated.

---

## 19. Acceptance Criteria for Complete Sign-off

- [ ] All four LastDose widgets and three TapRelay widgets can be added via the Android launcher widget drawer.
- [ ] Widgets display native Material You dynamic colors on Android 12+ (matching wallpaper palette on Pixel 7).
- [ ] Resizing a widget transitions smoothly between layout breakpoints without text clipping or awkward dead space.
- [ ] Logging a dose from a widget updates the database and reflects on the UI in under **100ms**.
- [ ] Chronometers count up continuously with **0% background CPU usage** by the application process.
- [ ] Hidden trackers are guaranteed to never be exposed on widgets.
- [ ] TapRelay widget actions fire with immediate haptic feedback and update visual state.
- [ ] Cross-app logging from TapRelay to LastDose operates seamlessly with signature verification.
- [ ] Both debug and release builds compile cleanly with all existing tests passing.

---

## 20. Release Checklist for Claude Code

1. **Static Analysis & Formatting**:
   - LastDose: `.\gradlew.bat compileDebugJavaWithJavac`
   - TapRelay: `.\gradlew.bat compileDebugKotlin`
2. **Unit Tests**:
   - LastDose: `.\gradlew.bat testDebugUnitTest`
   - TapRelay: `.\gradlew.bat testDebugUnitTest`
3. **Physical Device Verification (Pixel 7 / Android 17)**:
   - Place each widget on the home screen.
   - Test dark and light system theme switching; observe immediate color palette adaptation.
   - Verify tap actions and haptics.
4. **Documentation**:
   - Update `docs/RELEASE_7.1.11.md` in LastDose.
   - Update `docs/RELEASE_0.1.6.md` in TapRelay.

---

## 21. Explicitly Rejected Ideas & Architectural Rationale

1. **REJECTED: Pure Jetpack Glance in LastDose**  
   *Why*: LastDose is a pure Java application. Introducing Glance would add Kotlin Compose runtime dependencies, inflating APK size by ~4MB. More critically, Glance lacks native zero-battery `Chronometer` support, which would force inefficient periodic worker updates.
2. **REJECTED: Real-time Continuous Sliders on Home Screen Widgets**  
   *Why*: Sliders on Android home screen widgets (e.g. brightness 0–100%) are notoriously erratic because launcher gesture interceptors fight horizontal touches with home-screen page swipes. We use stepped presets and discrete buttons instead.
3. **REJECTED: Medical Advice / Predictive AI Claims on Smart Cadence Widget**  
   *Why*: LastDose strictly complies with medical safety standards. The Smart Cadence widget strictly displays historical user statistics (median interval, typical window) and never provides speculative algorithmic medical suggestions.
4. **REJECTED: Continuous Background Polling for Smart Home States in TapRelay**  
   *Why*: Polling cloud APIs (Tuya, Sensibo) every few seconds destroys device battery and risks rate limiting. We use event-driven optimistic state updates on user touch, syncing from the network only when the app is active or upon explicit user interaction.
