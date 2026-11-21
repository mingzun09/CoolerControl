package com.cooler.control

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode
import com.cooler.control.update.UpdateChecker
import com.cooler.control.update.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

class CoolerViewModel(app: Application) : AndroidViewModel(app) {

    private val client = (app as CoolerApp).client

    val state = client.state

    private val _update = MutableStateFlow<UpdateResult?>(null)
    val update: StateFlow<UpdateResult?> = _update.asStateFlow()

    fun checkUpdate() {
        val version = getApplication<Application>().let { app ->
            runCatching {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0"
            }.getOrDefault("0")
        }
        viewModelScope.launch { _update.value = UpdateChecker.check(version) }
    }

    fun scan() = client.startScan()
    fun stopScan() = client.stopScan()
    fun connect(address: String) = client.connect(address)
    fun disconnect() = client.disconnect()
    fun setMode(mode: CoolerMode) = client.setMode(mode)
    fun setLight(mode: LightMode, color: LightColor) = client.setLight(mode, color)
    fun setTimer(seconds: Int?) = client.setTimer(seconds)
    fun refresh() = client.requestConfig()
}