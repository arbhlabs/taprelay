package com.arbhlabs.taprelay.domain.model

/** Brightness is stored as a percentage and translated per provider. */
object Brightness {
    const val MIN_PERCENT = 1
    const val MAX_PERCENT = 100
    const val DEFAULT_PERCENT = 60

    fun clampPercent(percent: Int) = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)

    /** Govee's range capability is a plain 1-100 percentage. */
    fun toGovee(percent: Int) = clampPercent(percent)

    /** Tuya's bright_value_v2 data point runs 10..1000. */
    fun toTuya(percent: Int): Int {
        val p = clampPercent(percent)
        return (10 + (p - MIN_PERCENT) * (1000 - 10) / (MAX_PERCENT - MIN_PERCENT)).coerceIn(10, 1000)
    }
}
