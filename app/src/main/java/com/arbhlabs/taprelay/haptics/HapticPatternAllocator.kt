package com.arbhlabs.taprelay.haptics

import kotlin.math.abs

/** Pure, deterministic ARBH Labs max-min allocator. Android rendering lives elsewhere. */
class HapticPatternAllocator(private val candidates: List<HapticPattern> = CandidatePatterns.all) {
    fun allocate(
        descriptor: HapticSemanticDescriptor,
        existingPatternIds: Collection<String>,
        capabilities: HapticRenderCapabilities
    ): HapticPattern {
        val semantic = candidates.filter { it.family == descriptor.actionClass }
            .ifEmpty { candidates.filter { it.family == HapticActionClass.OTHER } }
        val existing = existingPatternIds.mapNotNull { id -> candidates.find { it.id == id } }
        if (existing.isEmpty()) return semantic.sortedWith(stableOrder(descriptor)).first()
        return semantic.maxWithOrNull(
            compareBy<HapticPattern> { candidate ->
                existing.minOf { distance(candidate, it, capabilities) }
            }.thenByDescending { stableRank(descriptor.mappingId, it.id) }
        ) ?: semantic.first()
    }

    fun distance(a: HapticPattern, b: HapticPattern, capabilities: HapticRenderCapabilities): Double {
        val ac = a.channels.first(); val bc = b.channels.first()
        val rhythm = normalizedListDistance(ac.timingsMs, bc.timingsMs, 500.0)
        val amplitude = if (capabilities.amplitudeControl) normalizedListDistance(ac.amplitudes, bc.amplitudes, 255.0) else 0.0
        val pulse = abs(a.pulseCount - b.pulseCount).coerceAtMost(4) / 4.0
        val envelope = if (a.envelope == b.envelope) 0.0 else 1.0
        val channel = if (capabilities.channels >= 2 && capabilities.spatialChannelsValidated) {
            if (a.channels.map { it.side } == b.channels.map { it.side }) 0.0 else 1.0
        } else 0.0
        val channelWeight = if (capabilities.spatialChannelsValidated) .22 else 0.0
        val amplitudeWeight = if (capabilities.amplitudeControl) .18 else 0.0
        return .34 * rhythm + channelWeight * channel + amplitudeWeight * amplitude + .12 * pulse + .16 * envelope
    }

    private fun <T : Number> normalizedListDistance(a: List<T>, b: List<T>, scale: Double): Double {
        val size = maxOf(a.size, b.size)
        if (size == 0) return 0.0
        return (0 until size).sumOf { i -> abs((a.getOrNull(i)?.toDouble() ?: 0.0) - (b.getOrNull(i)?.toDouble() ?: 0.0)) / scale } / size
    }

    private fun stableOrder(d: HapticSemanticDescriptor) = compareBy<HapticPattern> { stableRank(d.mappingId, it.id) }
    private fun stableRank(mappingId: String, patternId: String): Long =
        "$mappingId|$patternId".fold(1125899906842597L) { acc, char -> acc * 31 + char.code }
}

object CandidatePatterns {
    private fun channel(side: HapticSide, vararg pulses: Pair<Long, Int>): HapticChannelPattern {
        val timings = mutableListOf(0L); val amplitudes = mutableListOf(0)
        pulses.forEachIndexed { index, (duration, amplitude) ->
            if (index > 0) { timings += 45L; amplitudes += 0 }
            timings += duration; amplitudes += amplitude
        }
        return HapticChannelPattern(side, timings, amplitudes)
    }

    val all: List<HapticPattern> = buildList {
        listOf(255, 225, 205).forEachIndexed { i, peak ->
            add(HapticPattern("device-rise-$i", HapticActionClass.SMART_DEVICE, HapticEnvelope.RISING, listOf(channel(HapticSide.CENTRE, 35L to 90, 55L to peak))))
            add(HapticPattern("toggle-rise-$i", HapticActionClass.TOGGLE, HapticEnvelope.RISING, listOf(channel(HapticSide.CENTRE, 30L to 80, 65L to peak))))
            add(HapticPattern("other-$i", HapticActionClass.OTHER, HapticEnvelope.CRISP, listOf(channel(HapticSide.CENTRE, (45L + i * 12) to peak))))
        }
        listOf(35L, 50L, 65L).forEachIndexed { i, duration ->
            val double = listOf(channel(HapticSide.CENTRE, duration to 235, duration to 190))
            add(HapticPattern("log-double-$i", HapticActionClass.LOG, HapticEnvelope.CRISP, double))
            add(HapticPattern("lastdose-double-$i", HapticActionClass.LASTDOSE, HapticEnvelope.CRISP, double))
            add(HapticPattern("macro-$i", HapticActionClass.MACRO, HapticEnvelope.SEQUENCE, listOf(channel(HapticSide.CENTRE, duration to 130, duration to 185, duration to 240))))
            add(HapticPattern("open-$i", HapticActionClass.OPEN_APP, HapticEnvelope.CRISP, listOf(channel(HapticSide.CENTRE, duration to 170))))
            add(HapticPattern("momentary-$i", HapticActionClass.MOMENTARY, HapticEnvelope.CRISP, listOf(channel(HapticSide.CENTRE, duration to 190))))
        }
    }
}
