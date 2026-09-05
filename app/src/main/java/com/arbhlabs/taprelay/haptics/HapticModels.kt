package com.arbhlabs.taprelay.haptics

enum class HapticActivationSource { NFC, GAMEPAD, UI, OTHER }
enum class HapticActionClass { LOG, SMART_DEVICE, TOGGLE, MOMENTARY, OPEN_APP, MACRO, LASTDOSE, OTHER }
enum class HapticTransition { ON, OFF, TOGGLE, INCREMENT, DECREMENT, LOG, OTHER }
enum class HapticResult { SUCCESS, FAILURE, CANCELLED, CONFIRMATION_REQUIRED }
enum class HapticSide { LEFT, RIGHT, CENTRE }
enum class HapticEnvelope { CRISP, RISING, FALLING, WARNING, QUESTION, SEQUENCE }
enum class HapticIntensity(val scale: Float) { LIGHT(.65f), NORMAL(1f), STRONG(1.25f) }

data class HapticSemanticDescriptor(
    val mappingId: String,
    val source: HapticActivationSource,
    val physicalInput: String? = null,
    val actionClass: HapticActionClass,
    val targetIdentity: String,
    val transition: HapticTransition,
    val result: HapticResult
)

data class HapticChannelPattern(
    val side: HapticSide,
    val timingsMs: List<Long>,
    val amplitudes: List<Int>
)

data class HapticPattern(
    val id: String,
    val family: HapticActionClass,
    val envelope: HapticEnvelope,
    val channels: List<HapticChannelPattern>
) {
    val pulseCount: Int get() = channels.maxOfOrNull { channel -> channel.amplitudes.count { it > 0 } } ?: 0
}

enum class HapticFallbackStrategy { MULTI_CHANNEL, SINGLE_EXTERNAL, PHONE, NONE }

data class ControllerHapticCapabilities(
    val inputDeviceId: Int,
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val vibratorIds: List<Int>,
    val amplitudeControlById: Map<Int, Boolean>,
    val usableChannels: Int,
    val apiStrategy: HapticFallbackStrategy,
    /** IDs are deliberately not labelled as left/right/trigger until hardware probing validates them. */
    val channelIdentitiesValidated: Boolean = false
)

data class HapticRenderCapabilities(
    val channels: Int,
    val amplitudeControl: Boolean,
    val spatialChannelsValidated: Boolean = false
)
