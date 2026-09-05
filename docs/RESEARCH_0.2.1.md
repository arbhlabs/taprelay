# TapRelay 0.2.1 — product research, decisions and evidence

**Date:** 2026-09-05 · **Release:** 0.2.1 (versionCode 17) · **Author:** Claude (autonomous takeover)

This is the traceable record behind 0.2.1. It exists so a future agent can see *why* the release
is shaped this way without re-deriving it. Inputs were Antigravity's
`docs/CLAUDE_IMPLEMENTATION_HANDOFF.md`, `docs/WIDGET_SYSTEM_CLAUDE_HANDOFF.md` and
`PERFORMANCE_FORENSICS_AND_OPTIMIZATION_HANDOFF.md`, plus the primary sources cited below.

---

## 1. The user problem, with evidence

The demand is for **one physical trigger driving several devices**, and the pain is that getting
there today requires assembling an automation stack.

**Primary evidence.** How-To Geek, *["I put an NFC tag under my nightstand…"][htg]*: the author's
bedside tag turns off the main light, powers down the TV, engages the smart lock, deactivates
overnight smart plugs and sets bedside lamp brightness. To build it they needed **two NFC tags,
iOS Shortcuts, Home Assistant input-boolean helpers, custom conditional automation logic, and a
daily 05:00 reset automation**. Five devices, one intent — and a weekend of assembly.

That is the gap. Not "Android cannot do this", but "doing it costs a stack".

**Competitive shape.** Tasker is a one-time purchase of roughly $3.49–$5 and is repeatedly
described as needing a computer-science mindset ([Tasker on Google Play][tasker], secondary
comparisons). MacroDroid's free tier is ad-supported and capped at **5 macros**, with a one-time
Pro unlock ([MacroDroid on Google Play][macrodroid]). NFC Tools is around **$4 one-time** with a
limited free version ([AlternativeTo][alternativeto]). The whole category prices a one-time unlock
between roughly €3 and €6, and nobody in it is fast *and* simple.

**Conclusion.** TapRelay wins by making the five-device bedside routine a two-minute job with no
helpers, no YAML and no conditions — and by keeping sub-second execution while doing it.

---

## 2. What was chosen, and what was not

**Magic Actions** — one trigger, several of the owner's own actions, in order, with optional
pauses. Selected because it is the exact shape of the evidenced demand and because TapRelay's
existing architecture made it cheap (see §3).

**Home Assistant as a provider, not a feature.** The official REST API is a long-lived access
token in an `Authorization: Bearer` header against `/api/states` and
`/api/services/<domain>/<service>` ([Home Assistant developer docs][hadocs]). Community webhook
hacks were rejected in favour of the documented API. Implementing it as a `SmartHomeProvider`
rather than a new target type meant the tag wizard, Quick Controls, controller mapping, places,
Magic Actions, the always-on face and widgets all understood Home Assistant entities on the day it
landed, with none of them modified.

**Deliberately not built:**

- *Conditions, variables, expressions.* This is the line between a remote and Tasker. There is no
  stop-on-failure switch either — see §4.
- *A stop-on-failure option.* Making somebody choose between two failure policies is a Tasker-ism.
- *QR-code triggers* (in Antigravity's list). Deferred: NFC, controller, place, widget and
  always-on face already cover the physical surfaces, and QR needs an encoder dependency.
- *Backup/restore, controller chords.* Deferred to 0.2.2.

---

## 3. The architectural leverage point

TapRelay already routed every trigger through one place:

```
NFC · controller · place · widget · always-on face · in-app
        └──> TriggerRouter.fire(tagId) ──> ActionExecutor.run()
```

So a Magic Action step is **a reference to another item**, and running a step is *re-entering
`ActionExecutor.run()`* — the same call an NFC tap makes. The consequence is that the sequence
engine contains no knowledge of Govee, Tuya, Sensibo, Home Assistant, LastDose, web requests, app
launches or Do Not Disturb, and gains each new target type for free.

This is also why 0.2.1 could add four new target types (`WEBHOOK`, `LAUNCH`, `PHONE`,
`MAGIC_ACTION`) and a provider without touching a single trigger source.

---

## 4. Execution semantics, and why

| Question | Decision | Why |
|---|---|---|
| A step fails — stop or continue? | **Continue**, always | Somebody whose bedtime routine turns off four lights and one unreachable purifier wants the four lights off. They are told which one did not happen. |
| Per-step timeout | 12 s | Cloud providers have their own timeouts; a wedged socket must never leave someone at a light switch that will never answer. |
| Whole-sequence cap (widget path) | 40 s | Comfortably inside the window a manifest-declared receiver is given, even if every step times out. |
| Repeated trigger while running | **Ignored** | A repeated controller pull or re-scan mid-sequence is one intent expressed twice. Enforced by a single-flight claim per item, on top of the existing 1.5 s scan debounce. |
| Retries | None | A physical trigger either landed or it did not. Silent retries make a light flicker twice. |
| Nested Magic Actions | Refused at run time | Keeps a sequence finite and a tap explainable. |
| Feedback | `4 of 5 done` / `3 of 5 • Air purifier unavailable` | Never a raw error. |

---

## 5. Monetisation — the boundary is built, and inert

Antigravity recommended a €4.99 one-time Pro tier gating webhooks, Home Assistant, chains and
widgets. **That is not enabled in 0.2.1**, by explicit owner decision, for three reasons that all
hold independently:

1. **The repo forbids it, twice.** `AGENTS.md` non-negotiable #1 and `dist/SITE_PUBLISH.md`
   ("Do NOT: charge for it, gate it behind a paywall") both record that the Govee and Tuya
   developer APIs are licensed for personal, non-commercial use. The paywall was deliberately
   removed in the unreleased 0.1.3 and that removal shipped in 0.1.4.
2. **The live download page states TapRelay is free.** Enabling a gate would make published copy
   false.
3. **There is no payment path.** `EntitlementRepository` posts to
   `https://arbhlabs.com/api/license/activate`; no checkout exists. A gate today is a wall with no
   door.

What *is* in place: `TapRelayProFeature`, `EntitlementRepository`, the licence-key and founder-PIN
activation path, the 7-day local trial, and every gate call site. `canAccess()` returns `true` for
everything. **Enabling the boundary is one function body.**

**Useful for a future decision:** Google Play's Payments policy applies only to apps distributed
on Google Play — the policy text explicitly notes developers may distribute "directly from a
website … without using Google Play's billing system" ([Google Play Payments policy][playpay]).
TapRelay ships from arbhlabs.com, so a licence-key model needs no Play Billing integration. The
open question is vendor ToS, not billing mechanics.

**What would validate the hypothesis:** how many people build a second Magic Action; whether
anyone asks to buy; whether the bedside/gaming templates get used. None of that is measurable
today — TapRelay has no analytics, deliberately.

---

## 6. Always-on face — why it was redesigned

The 0.1.5 face showed a gamepad diagram, a list of controller mappings labelled by button code,
and a paragraph of explanatory body text. It was an engineering readout, not a product.

0.2.1 makes it an **action surface**: clock, wordmark, one large primary favourite, secondaries
two to a row, a one-line hardware status, and nothing else. Decisions:

- **Black space is the design.** Outlined tiles rather than filled slabs — on an OLED every lit
  pixel is power and, over hours propped on a desk, wear.
- **Insets, not numbers.** `enableEdgeToEdge()` + `Modifier.safeDrawingPadding()`. No hardcoded
  Pixel cutout dimensions.
- **Two taps to run, by default.** One tap arms a tile, the second runs it, and an armed tile
  disarms after 3.5 s. A sleeve on the glass must not turn the bedroom lights off. Configurable to
  immediate for people who want it.
- **Retained from 0.1.5** (it was already right): dozing to the panel's dimmest backlight,
  requesting the low frame-rate category, redrawing once a minute rather than once a second while
  dozing, and pixel drift within safe bounds for burn-in.
- **Slide to wake**, never tap.
- Favourites execute through `TriggerRouter` — there is no execution code on that screen.

---

## 7. Widgets — the real API constraints

- **Responsive layouts via `RemoteViews(Map<SizeF, RemoteViews>)`** (API 31+). The launcher swaps
  pre-computed layouts itself during a resize: no IPC to our process, no stretched frame, no
  clipping. Below API 31 the default size is served.
- **System shape and colour tokens.** `@android:dimen/system_app_widget_background_radius` and the
  `system_accent*` / `system_neutral*` palette in `values-v31`, with a TapRelay-teal fallback in
  `values` / `values-night` for API 30.
- **No `updatePeriodMillis`.** A widget redraws when something happened to it, never on a timer.
- **Animation is not available**, so "buttery" is bought differently: the tap handler writes the
  running state and redraws *before* it does anything asynchronous, so the tile changes within a
  frame of the finger landing rather than when a cloud API answers.
- **No foreground service.** A widget tap is short user-initiated work; `goAsync()` plus the
  executor's own step and overall caps keeps it inside the receiver's window without keeping a
  service alive for a light switch.

---

## 8. Performance findings acted on

From `PERFORMANCE_FORENSICS_AND_OPTIMIZATION_HANDOFF.md`, validated against the current tree:

| Finding | Verdict | Action |
|---|---|---|
| P0 — Tink/Keystore decryption on the main thread during launch (53–61 ms) | **Confirmed.** `refreshConnections()` ran on `viewModelScope` (Main) and every provider's `isConnected()` decrypts through Tink. | `.flowOn(Dispatchers.IO)` on all six `SecureKeyStorage` flows; `refreshConnections()` moved to `Dispatchers.IO`. |
| P0 — a cloud toggle leaves the button looking frozen | **Confirmed in spirit, wrong in detail.** Haptics and the pill were already immediate; what was missing was the *tapped row itself* changing. | `ActionExecutor.running` StateFlow; home cards, always-on tiles and widget tiles all show a running state instantly. |
| P1 — missing `key` in lazy lists | **Stale.** Existing lists already keyed by id. | New lists keyed and `contentType`-tagged. |
| P0 — decouple monolithic `HomeUiState` | **Deferred.** Large refactor, regression risk high, and the measured jank was dominated by the Keystore work above. | Documented; revisit if measurement still shows it. |
| P1 — Baseline Profiles | **Deferred.** Adding `profileinstaller` without generating a profile does nothing; generation needs a macrobenchmark module. | Documented as 0.2.2 work. |
| LastDose findings | Out of scope for a TapRelay release. | Left for a LastDose release. |

---

## 9. Sources

- [htg]: How-To Geek — *I put an NFC tag under my nightstand to kill late-night smart home frustrations* — <https://www.howtogeek.com/nfc-tag-under-nightstand-solved-smart-home-frustrations/>
- [hadocs]: Home Assistant developer documentation — *REST API* — <https://developers.home-assistant.io/docs/api/rest/>
- [playpay]: Google Play — *Payments policy* — <https://support.google.com/googleplay/android-developer/answer/10281818>
- [macrodroid]: MacroDroid — Device Automation on Google Play — <https://play.google.com/store/apps/details?id=com.arlosoft.macrodroid>
- [tasker]: Tasker pricing, via comparison coverage — <https://apps400.com/android-apps/tasker-automation-app-android-app.html>
- [alternativeto]: NFC Tools pricing — <https://alternativeto.net/software/nfc-tools/>

Competitor pricing figures are from secondary coverage and store listings as read on 2026-09-05;
store prices vary by region and change without notice. Nothing here is a quoted price commitment.
