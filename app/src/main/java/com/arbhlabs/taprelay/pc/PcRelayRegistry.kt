package com.arbhlabs.taprelay.pc

/** Mutable pairing boundary kept outside tags, so credentials never enter Room or Magic Actions. */
class PcRelayRegistry {
    @Volatile
    private var client: PcRelayClient? = null

    fun configure(value: PcRelayClient) {
        client = value
    }

    fun clear() {
        client = null
    }

    fun isPaired(): Boolean = client != null

    suspend fun send(action: String, value: String? = null, eventId: String): PcRelayResult =
        client?.send(action, value, eventId)
            ?: PcRelayResult.Failed("Pair a Windows relay in TapRelay first.", offline = true)
}
