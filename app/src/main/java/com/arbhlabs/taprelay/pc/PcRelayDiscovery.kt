package com.arbhlabs.taprelay.pc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** LAN discovery is intentionally bounded and read-only; pairing still requires a user code. */
class PcRelayDiscovery(
    private val port: Int = PcRelayProtocol.DISCOVERY_PORT,
    private val timeoutMs: Int = 600
) {
    suspend fun discover(): List<PcRelayProtocol.DiscoveryResponse> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, PcRelayProtocol.DiscoveryResponse>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = timeoutMs
            val request = "TAPRELAY_DISCOVER/${PcRelayProtocol.CURRENT_VERSION}".toByteArray()
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), port))
            val buffer = ByteArray(4096)
            while (true) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = PcRelayProtocol.decodeDiscovery(String(packet.data, 0, packet.length))
                    if (response.protocol == PcRelayProtocol.CURRENT_VERSION) {
                        found["${response.host}:${response.port}"] = response
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                }
            }
        }
        found.values.toList()
    }
}
