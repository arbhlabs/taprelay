package com.arbhlabs.taprelay.domain.model

import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.HOME_ASSISTANT_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID

/**
 * The one short line that describes an item.
 *
 * It exists because the same sentence has to appear on the home list, in the Magic Action step
 * picker, on the always-on face and on a home-screen widget - and when each of those had its own
 * copy, every one of them fell through to "Device" because the provider ids are `govee_cloud` and
 * `tuya_cloud`, not `govee` and `tuya`. One place, using the real constants.
 */
object ItemLabels {

    fun providerLabel(providerId: String): String = when (providerId) {
        GOVEE_PROVIDER_ID -> "Govee"
        TUYA_PROVIDER_ID -> "Smart Life"
        SENSIBO_PROVIDER_ID -> "Sensibo"
        HOME_ASSISTANT_PROVIDER_ID -> "Home Assistant"
        else -> "Device"
    }

    /** "4 actions", "Govee light", "Logs to LastDose" - what this item is, in three words. */
    fun summary(tag: TagEntity): String = when {
        tag.isMagicAction -> {
            val count = tag.magicSteps.count { !it.isWait }
            if (count == 1) "1 action" else "$count actions"
        }
        tag.isLastDose -> "Logs to LastDose"
        tag.isWebhook -> {
            val host = tag.webhookUrl?.substringAfter("://")?.substringBefore('/').orEmpty()
            if (host.isBlank()) "Web request" else host
        }
        tag.isLaunch -> if (tag.deviceSku == LaunchKind.LINK) "Opens a link" else "Opens an app"
        tag.isPhone -> PhoneAction.label(tag.deviceId)
        tag.isPcRelay -> "Windows • ${tag.deviceId.replace('.', ' ').replace('_', ' ')}"
        tag.targetType == TargetType.SCENE -> providerLabel(tag.providerId) + " scene"
        else -> providerLabel(tag.providerId)
    }

    /** The same, plus what it does to the device: "Govee • Toggle". */
    fun detailed(tag: TagEntity, actionSummary: String): String =
        if (tag.isNonDevice || tag.targetType == TargetType.SCENE) {
            summary(tag)
        } else {
            "${providerLabel(tag.providerId)} • $actionSummary"
        }
}
