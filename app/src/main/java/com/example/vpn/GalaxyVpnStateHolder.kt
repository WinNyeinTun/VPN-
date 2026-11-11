package com.example.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus {
    OFFLINE,
    CONNECTING,
    CONNECTED
}

data class VpnTelemetry(
    val status: VpnStatus = VpnStatus.OFFLINE,
    val durationFormatted: String = "00:00:00",
    val downloadSpeedFormatted: String = "0.0 KB/s",
    val uploadSpeedFormatted: String = "0.0 KB/s",
    val serverRemark: String = ""
)

object GalaxyVpnStateHolder {
    private val _telemetry = MutableStateFlow(VpnTelemetry())
    val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

    fun update(
        status: VpnStatus,
        duration: String = "00:00:00",
        dl: String = "0.0 KB/s",
        ul: String = "0.0 KB/s",
        serverRemark: String = ""
    ) {
        _telemetry.value = VpnTelemetry(status, duration, dl, ul, serverRemark)
    }
}
