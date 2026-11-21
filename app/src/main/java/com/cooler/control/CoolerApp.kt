package com.cooler.control

import android.app.Application
import com.cooler.control.ble.CoolerBleClient

/**
 * 全局持有唯一的 BLE 客户端，Activity / ViewModel / 前台服务共用同一个连接。
 */
class CoolerApp : Application() {

    val client: CoolerBleClient by lazy { CoolerBleClient(this) }
}
