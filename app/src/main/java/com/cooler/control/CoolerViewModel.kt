package com.cooler.control

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode

class CoolerViewModel(app: Application) : AndroidViewModel(app) {

    private val client = (app as CoolerApp).client

    val state = client.state

    fun scan() = client.startScan()
    fun stopScan() = client.stopScan()
    fun connect(address: String) = client.connect(address)
    fun disconnect() = client.disconnect()
    fun setMode(mode: CoolerMode) = client.setMode(mode)
    fun setLight(mode: LightMode, color: LightColor) = client.setLight(mode, color)
    fun setTimer(seconds: Int?) = client.setTimer(seconds)
    fun refresh() = client.requestConfig()
}
