package com.example.net

import com.example.core.XrayConfigBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NetworkPingTester {

    suspend fun testPing(configUri: String): Int = withContext(Dispatchers.IO) {
        val parsed = XrayConfigBuilder.parseUri(configUri) ?: return@withContext -1
        val host = parsed.address
        val port = parsed.port

        if (host.isBlank() || port <= 0) return@withContext -1

        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3500)
                val ping = (System.currentTimeMillis() - startTime).toInt()
                return@withContext ping.coerceAtLeast(1)
            }
        } catch (_: Exception) {
            -1
        }
    }
}
