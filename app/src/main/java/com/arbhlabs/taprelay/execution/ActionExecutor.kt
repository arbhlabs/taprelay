package com.arbhlabs.taprelay.execution

import android.util.Log
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionStep
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.LaunchKind
import com.arbhlabs.taprelay.domain.model.powerIntent
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.Toggle
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import com.arbhlabs.taprelay.data.local.dao.TapLogDao
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.execution.lastdose.LastDoseClient
import com.arbhlabs.taprelay.execution.lastdose.LastDoseResult
import com.arbhlabs.taprelay.execution.phone.AppLauncher
import com.arbhlabs.taprelay.execution.phone.LaunchResult
import com.arbhlabs.taprelay.execution.phone.PhoneController
import com.arbhlabs.taprelay.execution.phone.PhoneResult
import com.arbhlabs.taprelay.execution.webhook.WebhookClient
import com.arbhlabs.taprelay.execution.webhook.WebhookMethod
import com.arbhlabs.taprelay.monetization.EntitlementRepository
import com.arbhlabs.taprelay.monetization.TapRelayProFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime

data class TapFeedback(
    val title: String,
    val isError: Boolean,
    val pending: Boolean = false,
    /** Set only while a Magic Action is running, so a surface can show "3 of 5". */
    val progress: Progress? = null
) {
    data class Progress(val completed: Int, val total: Int)
}

/** What one execution did, so a caller - or a Magic Action step - can act on the outcome. */
data class TapOutcome(
    val success: Boolean,
    /** Short and already user-facing: "Desk Lamp • On", "Air purifier unavailable". */
    val summary: String
)

/**
 * The one place an item becomes an outcome.
 *
 * Every trigger - an NFC tap, a controller button, a place, the always-on face, a home-screen
 * widget, a test tap in the app - arrives here through `TriggerRouter`, and every kind of action
 * leaves here through one of the `run*` branches below. A Magic Action is not an exception to
 * that: its steps re-enter this same method, which is why a sequence supports Home Assistant,
 * Sensibo, LastDose and web requests without the sequence engine containing a single line about
 * any of them.
 *
 * Duplicate triggers are handled twice over: [debounceMs] drops a repeated scan of the same tag,
 * and [running] drops a trigger for an item that is still mid-sequence, so pulling a controller
 * trigger three times during a six-second bedtime routine runs it once.
 */
class ActionExecutor(
    private val tags: TagRepository,
    private val providers: () -> Map<String, SmartHomeProvider>,
    private val haptics: HapticsManager,
    private val debounceMs: Long = 1500L,
    private val tapLogDao: TapLogDao? = null,
    private val entitlements: EntitlementRepository? = null,
    /** Present only where LastDose logging is wired; null in tests and on the smart-home-only path. */
    private val lastDose: LastDoseClient? = null,
    private val webhooks: WebhookClient? = null,
    private val secureStorage: SecureKeyStorage? = null,
    private val launcher: AppLauncher? = null,
    private val phone: PhoneController? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lastFired = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    private val _running = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The items that are executing right now.
     *
     * Every surface subscribes to this and marks the thing the finger actually landed on, which is
     * what stops a cloud call feeling like a frozen button. A toggle has to read the device's real
     * power state before it can invert it, and that round trip is hundreds of milliseconds on a
     * good day - so the answer is not to skip the read and guess, it is to make the surface say
     * "working" the instant it is pressed.
     */
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    private val _executions = MutableSharedFlow<TapOutcome>(extraBufferCapacity = 8)

    /**
     * Fires once per completed execution, whatever triggered it.
     *
     * Home-screen widgets listen here rather than polling: a widget redraws because something
     * happened, not because a minute went by.
     */
    val executions: SharedFlow<TapOutcome> = _executions.asSharedFlow()

    private companion object {
        /** Outcome logging for field diagnostics. Never records tag names or credentials. */
        const val TAG = "TapRelayExec"

        /** Not SmartHomeProviders - just how a row is labelled in tap history. */
        const val LASTDOSE_PROVIDER_ID = "lastdose"
        const val WEBHOOK_PROVIDER_ID = "web"
        const val PHONE_PROVIDER_ID = "phone"
        const val MAGIC_PROVIDER_ID = "magic"

        /**
         * A single step gets this long before the sequence gives up on it and moves on. Cloud
         * providers have their own timeouts, but a wedged socket must never leave somebody
         * standing at a light switch that is never going to answer.
         */
        const val STEP_TIMEOUT_MS = 12_000L
    }

    fun executeByTagId(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        val now = System.currentTimeMillis()
        synchronized(lastFired) {
            val prev = lastFired[tagId]
            if (prev != null && now - prev < debounceMs) return
            lastFired[tagId] = now
        }
        scope.launch { run(tagId, onFeedback) }
    }

    /** 1-tap instant replay from tap history: bypasses the scan debounce, not the single-flight. */
    fun replay(tagId: String, onFeedback: (TapFeedback) -> Unit) {
        scope.launch { run(tagId, onFeedback) }
    }

    /**
     * Runs an item and waits for the outcome. Used where the caller has to stay alive until the
     * action is done - a widget's `goAsync()` receiver, most of all.
     */
    suspend fun executeAndAwait(tagId: String, onFeedback: (TapFeedback) -> Unit = {}): TapOutcome {
        val now = System.currentTimeMillis()
        synchronized(lastFired) {
            val prev = lastFired[tagId]
            if (prev != null && now - prev < debounceMs) {
                return TapOutcome(success = false, summary = "Already running")
            }
            lastFired[tagId] = now
        }
        return run(tagId, onFeedback)
    }

    /**
     * @param depth 0 for a trigger, 1 for a step inside a Magic Action. A step never starts another
     *   sequence, which is what keeps a sequence finite and a tap explainable.
     * @param silent true for a step: the sequence owns the haptics and the single summary, so
     *   individual steps neither buzz nor post their own message.
     */
    private suspend fun run(
        tagId: String,
        onFeedback: (TapFeedback) -> Unit,
        depth: Int = 0,
        silent: Boolean = false
    ): TapOutcome {
        val startTime = System.currentTimeMillis()
        val rawTag = tags.getTagById(tagId)
        if (rawTag == null) {
            return fail(silent, onFeedback, TapError.TAG_UNREGISTERED.message)
        }
        if (!rawTag.enabled) {
            return fail(silent, onFeedback, "${rawTag.friendlyName} is turned off in the app.")
        }

        // One item, one run at a time. A repeated controller pull, a re-scan or a second widget tap
        // while a sequence is still working is the same intent expressed twice, not a request to
        // run it twice.
        if (!claim(tagId)) {
            return TapOutcome(success = false, summary = "${rawTag.friendlyName} is already running.")
        }
        val outcome = try {
            if (!silent) haptics.vibrateClick()
            when {
                rawTag.isMagicAction -> runMagicAction(rawTag, startTime, depth, silent, onFeedback)
                rawTag.isLastDose -> runLastDoseLog(rawTag, startTime, silent, onFeedback)
                rawTag.isWebhook -> runWebhook(rawTag, startTime, silent, onFeedback)
                rawTag.isLaunch -> runLaunch(rawTag, startTime, silent, onFeedback)
                rawTag.isPhone -> runPhoneAction(rawTag, startTime, silent, onFeedback)
                else -> runDevice(rawTag, startTime, silent, onFeedback)
            }
        } finally {
            release(tagId)
        }
        if (depth == 0) _executions.tryEmit(outcome)
        return outcome
    }

    private fun claim(tagId: String): Boolean {
        val claimed = synchronized(inFlight) { inFlight.add(tagId) }
        if (claimed) _running.value = _running.value + tagId
        return claimed
    }

    private fun release(tagId: String) {
        synchronized(inFlight) { inFlight.remove(tagId) }
        _running.value = _running.value - tagId
    }

    // ---------------------------------------------------------------- Magic Actions

    /**
     * Runs the item's steps in order.
     *
     * A failed step does not stop the sequence. That is the deliberate default: somebody whose
     * bedtime routine turns off four lights and one unreachable purifier wants the four lights off,
     * and to be told exactly which one did not happen. There is no stop-on-failure switch, because
     * making people choose between the two is how a remote turns into Tasker.
     */
    private suspend fun runMagicAction(
        tag: TagEntity,
        startTime: Long,
        depth: Int,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        if (depth > 0) {
            // A sequence inside a sequence: configurable in principle, refused at run time, so the
            // outcome is one honest failed step rather than an unbounded tree of taps.
            return TapOutcome(false, "${tag.friendlyName} can't run inside another Magic Action")
        }
        val runnable = tag.magicSteps.filter { it.isWait || it.tagId != null }
        if (runnable.none { !it.isWait }) {
            return finishFailure(tag, MAGIC_PROVIDER_ID, startTime, silent, onFeedback, TapError.CHAIN_EMPTY.message)
        }

        val actionCount = runnable.count { !it.isWait }
        var completed = 0
        val failures = mutableListOf<String>()

        for ((index, step) in runnable.withIndex()) {
            if (step.isWait) {
                delay(step.delayMs.coerceIn(0L, ActionStep.MAX_DELAY_MS))
                continue
            }
            val stepTagId = step.tagId ?: continue
            if (!silent) {
                onFeedback(
                    TapFeedback(
                        "${tag.friendlyName} • ${step.label.ifBlank { "Step ${index + 1}" }}…",
                        isError = false,
                        pending = true,
                        progress = TapFeedback.Progress(completed, actionCount)
                    )
                )
            }
            // Steps are silent and never recurse: the sequence owns feedback and haptics.
            val outcome = withTimeoutOrNull(STEP_TIMEOUT_MS) {
                run(stepTagId, {}, depth = depth + 1, silent = true)
            } ?: TapOutcome(false, "${step.label.ifBlank { "A step" }} took too long")

            if (outcome.success) completed++ else failures += outcome.summary
        }

        val allDone = failures.isEmpty()
        val duration = System.currentTimeMillis() - startTime
        val summary = if (allDone) {
            "$completed of $actionCount done"
        } else {
            "$completed of $actionCount • ${failures.first()}"
        }
        tapLogDao?.insert(
            TapLogEntity(
                tagId = tag.tagId,
                tagName = tag.friendlyName,
                providerId = MAGIC_PROVIDER_ID,
                actionDescription = "$completed/$actionCount steps",
                success = allDone,
                errorMessage = failures.firstOrNull(),
                durationMs = duration
            )
        )
        if (allDone) tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
        Log.i(TAG, "magic steps=$completed/$actionCount ok=$allDone (${duration}ms)")

        val text = "${tag.friendlyName} • $summary"
        if (!silent) {
            withContext(Dispatchers.Main) {
                if (allDone) haptics.vibrateSuccess() else haptics.vibrateError()
                onFeedback(
                    TapFeedback(
                        text,
                        isError = !allDone,
                        progress = TapFeedback.Progress(completed, actionCount)
                    )
                )
            }
        }
        return TapOutcome(allDone, text)
    }

    // ---------------------------------------------------------------- Web requests

    private suspend fun runWebhook(
        tag: TagEntity,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        val client = webhooks
        val url = tag.webhookUrl?.trim().orEmpty()
        if (client == null || url.isBlank()) {
            return finishFailure(
                tag, WEBHOOK_PROVIDER_ID, startTime, silent, onFeedback,
                "That web action is no longer set up."
            )
        }
        val method = runCatching { WebhookMethod.valueOf(tag.webhookMethod ?: "POST") }
            .getOrDefault(WebhookMethod.POST)

        if (!silent) {
            onFeedback(TapFeedback("${tag.friendlyName} • Sending…", isError = false, pending = true))
        }

        // The credential lives encrypted, keyed by item, and is only ever assembled here.
        val headers = buildMap {
            putAll(WebhookClient.parseHeaders(tag.webhookHeadersJson))
            val secretHeader = tag.webhookSecretHeader?.takeIf { it.isNotBlank() }
            if (secretHeader != null) {
                secureStorage?.getWebhookSecret(tag.tagId)?.first()?.let { put(secretHeader, it) }
            }
        }

        return try {
            val status = client.send(url, method, headers, tag.webhookBody)
            Log.i(TAG, "web ${method.name} status=$status")
            finishSuccess(
                tag = tag,
                providerId = WEBHOOK_PROVIDER_ID,
                startTime = startTime,
                silent = silent,
                onFeedback = onFeedback,
                // Only the verb and the host: a query string is where these keys actually live.
                logDescription = WebhookClient.redactForLog(url, method),
                userSummary = "Sent"
            )
        } catch (e: TapException) {
            finishFailure(tag, WEBHOOK_PROVIDER_ID, startTime, silent, onFeedback, e.error.message)
        } catch (e: Exception) {
            finishFailure(tag, WEBHOOK_PROVIDER_ID, startTime, silent, onFeedback, TapError.WEB_UNREACHABLE.message)
        }
    }

    // ---------------------------------------------------------------- Phone-side actions

    private suspend fun runLaunch(
        tag: TagEntity,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        val result = launcher?.launch(tag.deviceSku.ifBlank { LaunchKind.APP }, tag.deviceId)
            ?: return finishFailure(
                tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, "Opening isn't available here."
            )
        return when (result) {
            is LaunchResult.Opened ->
                finishSuccess(tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, "Opened", "Opened")
            is LaunchResult.Failed ->
                finishFailure(tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, result.message)
        }
    }

    private suspend fun runPhoneAction(
        tag: TagEntity,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        val controller = phone
            ?: return finishFailure(
                tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, "This phone can't do that."
            )
        return when (val result = controller.run(tag.deviceId)) {
            is PhoneResult.Done ->
                finishSuccess(tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, result.summary, result.summary)
            PhoneResult.NeedsPermission -> finishFailure(
                tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback,
                "TapRelay needs Do Not Disturb access. Turn it on in Settings."
            )
            is PhoneResult.Failed ->
                finishFailure(tag, PHONE_PROVIDER_ID, startTime, silent, onFeedback, result.message)
        }
    }

    // ---------------------------------------------------------------- Smart-home devices and scenes

    private suspend fun runDevice(
        rawTag: TagEntity,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        // Context-aware time-of-day condition evaluation.
        val tag = if (rawTag.timeConditionEnabled &&
            (entitlements == null || entitlements.canAccess(TapRelayProFeature.TIME_OF_DAY_CONDITIONS))
        ) {
            val localTime = LocalTime.now()
            val currentMinutes = localTime.hour * 60 + localTime.minute
            val startMinutes = (rawTag.startHour ?: 0) * 60 + (rawTag.startMinute ?: 0)
            val endMinutes = (rawTag.endHour ?: 24) * 60 + (rawTag.endMinute ?: 0)
            val inWindow = if (startMinutes <= endMinutes) {
                currentMinutes in startMinutes until endMinutes
            } else {
                currentMinutes >= startMinutes || currentMinutes < endMinutes
            }
            if (inWindow) {
                rawTag
            } else {
                val alt = rawTag.offActionType ?: ActionType.TURN_OFF
                rawTag.copy(
                    actionType = alt,
                    brightnessPercent = if (alt == ActionType.TURN_OFF) null else rawTag.brightnessPercent
                )
            }
        } else {
            rawTag
        }

        val provider = providers()[tag.providerId]
            ?: return finishFailure(
                tag, tag.providerId, startTime, silent, onFeedback, TapError.PROVIDER_UNAVAILABLE.message
            )

        if (tag.targetType == TargetType.SCENE || tag.actionType == ActionType.RUN_SCENE) {
            if (!silent) {
                onFeedback(TapFeedback("${tag.friendlyName} • Running scene…", isError = false, pending = true))
            }
            return try {
                provider.executeAction(
                    targetId = tag.deviceId,
                    targetType = TargetType.SCENE,
                    sku = tag.deviceSku,
                    action = ActionType.RUN_SCENE,
                    targetState = 1
                )
                tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
                finishSuccess(tag, tag.providerId, startTime, silent, onFeedback, "Scene started", "Scene started")
            } catch (e: TapException) {
                finishFailure(tag, tag.providerId, startTime, silent, onFeedback, e.error.message, "Scene failed")
            } catch (e: Exception) {
                finishFailure(tag, tag.providerId, startTime, silent, onFeedback, TapError.UNKNOWN.message, "Scene failed")
            }
        }

        if (tag.actionType == ActionType.TOGGLE_FAN_SPEED) {
            if (!silent) {
                onFeedback(TapFeedback("${tag.friendlyName} • Toggling fan…", isError = false, pending = true))
            }
            return try {
                val p = providers()[tag.providerId]
                val level = if (p is com.arbhlabs.taprelay.domain.provider.SensiboProvider) {
                    p.toggleFanSpeed(tag.deviceId)
                } else {
                    p?.setPower(tag.deviceId, tag.deviceSku, true)
                    "High"
                }
                val outcome = "Fan " + level.replaceFirstChar { it.uppercase() }
                tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
                finishSuccess(tag, tag.providerId, startTime, silent, onFeedback, outcome, outcome)
            } catch (e: TapException) {
                finishFailure(tag, tag.providerId, startTime, silent, onFeedback, e.error.message, "Fan toggle failed")
            } catch (e: Exception) {
                finishFailure(tag, tag.providerId, startTime, silent, onFeedback, TapError.UNKNOWN.message, "Fan toggle failed")
            }
        }

        val finalTarget: Int
        try {
            val intent = tag.actionType.powerIntent()
            if (intent == ActionType.TOGGLE) {
                if (!silent) {
                    onFeedback(TapFeedback("${tag.friendlyName} • Updating…", isError = false, pending = true))
                }
                val real = provider.getPowerState(tag.deviceId, tag.deviceSku)
                finalTarget = if (real != null) Toggle.inverseOf(real) else Toggle.target(intent, tag.lastKnownState)
                Log.i(TAG, "toggle provider=${tag.providerId} read=${real ?: "unknown"} sending=$finalTarget")
            } else {
                finalTarget = Toggle.target(intent, tag.lastKnownState)
                if (!silent) {
                    onFeedback(
                        TapFeedback(
                            "${tag.friendlyName} • ${settingLabel(tag, finalTarget)}",
                            isError = false,
                            pending = true
                        )
                    )
                }
            }

            // Multi-target routine execution (or unified group).
            val targets = tag.allTargets
            var succeeded = 0
            var firstFailure: TapException? = null
            for (target in targets) {
                val p = providers()[target.providerId]
                if (p == null) {
                    firstFailure = firstFailure ?: TapException(TapError.PROVIDER_UNAVAILABLE)
                    continue
                }
                try {
                    val isRoutineStep = target.actionType != null &&
                        (entitlements == null || entitlements.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))

                    val stepAction = if (isRoutineStep) target.actionType!! else tag.actionType
                    val stepBrightness = if (isRoutineStep) target.brightnessPercent else tag.brightnessPercent
                    val stepColor = if (isRoutineStep) target.colorRgb else tag.colorRgb
                    val stepFanLevel = if (isRoutineStep) target.fanLevel else tag.fanLevel
                    val stepState = if (isRoutineStep) {
                        Toggle.target(stepAction.powerIntent(), finalTarget)
                    } else {
                        finalTarget
                    }

                    p.executeAction(
                        targetId = target.deviceId,
                        targetType = TargetType.DEVICE,
                        sku = target.sku,
                        action = stepAction,
                        targetState = stepState,
                        brightnessPercent = stepBrightness,
                        colorRgb = stepColor,
                        fanLevel = stepFanLevel
                    )
                    succeeded++
                } catch (e: TapException) {
                    firstFailure = firstFailure ?: e
                } catch (e: Exception) {
                    firstFailure = firstFailure ?: TapException(TapError.UNKNOWN)
                }
            }

            if (succeeded == 0) throw firstFailure ?: TapException(TapError.UNKNOWN)

            tags.updateStateAndTimestamp(tag.tagId, finalTarget, System.currentTimeMillis())
            val outcome = outcomeLabel(tag, finalTarget)
            val text = if (succeeded < targets.size) "$outcome ($succeeded of ${targets.size})" else outcome
            Log.i(TAG, "ok provider=${tag.providerId} action=${tag.actionType} targets=$succeeded/${targets.size}")
            return finishSuccess(tag, tag.providerId, startTime, silent, onFeedback, outcome, text)
        } catch (e: TapException) {
            Log.w(TAG, "failed provider=${tag.providerId} error=${e.error.name}")
            revert(tag.tagId, tag.lastKnownState)
            return finishFailure(tag, tag.providerId, startTime, silent, onFeedback, e.error.message)
        } catch (e: Exception) {
            Log.w(TAG, "failed provider=${tag.providerId} error=unexpected")
            revert(tag.tagId, tag.lastKnownState)
            return finishFailure(tag, tag.providerId, startTime, silent, onFeedback, TapError.UNKNOWN.message)
        }
    }

    // ---------------------------------------------------------------- LastDose

    /**
     * Writes one log into LastDose without LastDose ever appearing. The request id is derived from
     * the item and a coarse time slice, so a duplicated delivery of the same physical press is
     * recognised on the LastDose side and cannot produce a second entry.
     */
    private suspend fun runLastDoseLog(
        tag: TagEntity,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit
    ): TapOutcome {
        val client = lastDose
        val itemId = tag.lastDoseItemId ?: 0L
        val display = tag.lastDoseItemName?.takeIf { it.isNotBlank() } ?: tag.friendlyName
        if (client == null || itemId <= 0L) {
            return finishFailure(
                tag, LASTDOSE_PROVIDER_ID, startTime, silent, onFeedback,
                "That LastDose log is no longer set up.", "LastDose log failed"
            )
        }
        if (!silent) {
            onFeedback(TapFeedback("$display • Logging…", isError = false, pending = true))
        }

        val requestId = "${tag.tagId}:${startTime / 1000L}"
        val result = client.log(itemId, tag.lastDoseAmount, tag.lastDoseUnit, requestId)

        return when (result) {
            is LastDoseResult.Logged -> finishSuccess(
                tag, LASTDOSE_PROVIDER_ID, startTime, silent, onFeedback,
                "Logged to LastDose", logSummary(display, result.amount, result.unit), useTagName = false
            )
            // The log the owner asked for exists; a repeated delivery is still that one success.
            is LastDoseResult.Duplicate -> finishSuccess(
                tag, LASTDOSE_PROVIDER_ID, startTime, silent, onFeedback,
                "Logged to LastDose",
                logSummary(display, tag.lastDoseAmount.orEmpty(), tag.lastDoseUnit.orEmpty()),
                useTagName = false
            )
            is LastDoseResult.Failed -> finishFailure(
                tag, LASTDOSE_PROVIDER_ID, startTime, silent, onFeedback, result.message, "LastDose log failed"
            )
        }
    }

    // ---------------------------------------------------------------- Shared endings

    /**
     * Records the row in tap history, buzzes and posts the pill. Every success path ends here, so
     * history, haptics and the message can never disagree about what happened.
     */
    private suspend fun finishSuccess(
        tag: TagEntity,
        providerId: String,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit,
        logDescription: String,
        userSummary: String,
        useTagName: Boolean = true
    ): TapOutcome {
        val duration = System.currentTimeMillis() - startTime
        tapLogDao?.insert(
            TapLogEntity(
                tagId = tag.tagId,
                tagName = tag.friendlyName,
                providerId = providerId,
                actionDescription = logDescription,
                success = true,
                durationMs = duration
            )
        )
        val text = if (useTagName) "${tag.friendlyName} • $userSummary" else userSummary
        if (!silent) {
            withContext(Dispatchers.Main) {
                haptics.vibrateSuccess()
                onFeedback(TapFeedback(text, isError = false))
            }
        }
        return TapOutcome(success = true, summary = text)
    }

    private suspend fun finishFailure(
        tag: TagEntity,
        providerId: String,
        startTime: Long,
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit,
        message: String,
        logDescription: String = "Failed"
    ): TapOutcome {
        val duration = System.currentTimeMillis() - startTime
        tapLogDao?.insert(
            TapLogEntity(
                tagId = tag.tagId,
                tagName = tag.friendlyName,
                providerId = providerId,
                actionDescription = logDescription,
                success = false,
                errorMessage = message,
                durationMs = duration
            )
        )
        if (!silent) {
            withContext(Dispatchers.Main) {
                haptics.vibrateError()
                onFeedback(TapFeedback(message, isError = true))
            }
        }
        // A step's summary names its item, so a sequence can say which one went wrong.
        return TapOutcome(success = false, summary = "${tag.friendlyName}: $message")
    }

    /** The failures that happen before an item is resolved, so there is no row to write. */
    private suspend fun fail(
        silent: Boolean,
        onFeedback: (TapFeedback) -> Unit,
        message: String
    ): TapOutcome {
        if (!silent) {
            withContext(Dispatchers.Main) {
                haptics.vibrateError()
                onFeedback(TapFeedback(message, isError = true))
            }
        }
        return TapOutcome(success = false, summary = message)
    }

    /** "Bowl +1 logged" - the amount only when there is one, never the raw contract. */
    private fun logSummary(name: String, amount: String, unit: String): String {
        val qualifier = listOf(amount, unit).filter { it.isNotBlank() }.joinToString(" ")
        return if (qualifier.isBlank()) "$name logged" else "$name $qualifier logged"
    }

    private suspend fun revert(tagId: String, previous: Int) =
        tags.updateStateAndTimestamp(tagId, previous, System.currentTimeMillis())

    /** Describes whatever combination the tag carries, not a single fixed action. */
    private fun settingLabel(tag: TagEntity, state: Int) = outcomeLabel(tag, state) + "…"

    private fun outcomeLabel(tag: TagEntity, state: Int): String {
        if (tag.actionType == ActionType.TOGGLE_FAN_SPEED) return "Fan Low ↔ High"
        if (state != 1) return "Off"
        val parts = buildList {
            tag.fanLevel?.let { add("Fan ${it.replaceFirstChar { c -> c.uppercase() }}") }
            tag.colorRgb?.let { add(LightPresets.nameFor(it)) }
            tag.brightnessPercent?.let { add("${Brightness.clampPercent(it)}%") }
        }
        return if (parts.isEmpty()) "On" else "On • " + parts.joinToString(" ")
    }
}
