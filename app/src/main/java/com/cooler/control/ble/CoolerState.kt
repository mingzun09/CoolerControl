package com.cooler.control.ble

import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode

enum class ConnectionState { IDLE, SCANNING, CONNECTING, CONNECTED, RECONNECTING, FAILED }

data class FoundDevice(val name: String, val address: String, val rssi: Int)

data class CoolerState(
    val connection: ConnectionState = ConnectionState.IDLE,
    val deviceName: String? = null,
    val address: String? = null,
    val temperature: Int? = null,
    val mode: CoolerMode? = null,
    val lightMode: LightMode? = null,
    val lightColor: LightColor? = null,
    val timerSeconds: Int? = null,
    val error: String? = null,
    val foundDevices: List<FoundDevice> = emptyList(),
)
