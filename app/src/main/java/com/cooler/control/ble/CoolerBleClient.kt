package com.cooler.control.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.CoolerProtocol
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 背夹 BLE 客户端：扫描、连接、读写配置、解析上报。
 * 设备不需要配对，直接连接即可。
 *
 * 扫描注意：Android 同一时刻只允许一个有效扫描，重复 startScan 会回调
 * SCAN_FAILED_ALREADY_STARTED（错误码 1），所以这里用 running 标志串行化。
 */
class CoolerBleClient(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _state = MutableStateFlow(CoolerState())
    val state: StateFlow<CoolerState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private var seq = 0
    private var wantConnection = false
    private var reconnectAttempts = 0
    private var lastAddress: String? = null

    private var scanning = false
    private var fallbackScheduled = false

    /** 搜到设备就自动连一次；用户手动断开后置 false */
    private var autoConnectArmed = false

    private val prefs = context.getSharedPreferences("cooler", Context.MODE_PRIVATE)

    private val lastKnownAddress: String?
        get() = prefs.getString(KEY_LAST_ADDRESS, null)

    private val lastKnownName: String?
        get() = prefs.getString(KEY_LAST_NAME, null)

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: result.device.name
            val hasService = record?.serviceUuids?.any { it.uuid == CoolerProtocol.SERVICE_UUID } == true
            if (!isCoolerName(name) && !hasService) return
            val label = if (isCoolerName(name)) name!! else "小米背夹"
            Log.d(TAG, "found ${result.device.address} name=$name rssi=${result.rssi} svc=$hasService")
            val found = FoundDevice(label, result.device.address, result.rssi)
            val list = _state.value.foundDevices.filterNot { it.address == found.address } + found
            if (list.size != _state.value.foundDevices.size ||
                _state.value.foundDevices.none { it.address == found.address }
            ) {
                _state.value = _state.value.copy(foundDevices = list, error = null)
            }
            if (autoConnectArmed && _state.value.connection != ConnectionState.CONNECTED) {
                autoConnectArmed = false
                Log.d(TAG, "auto connect to ${found.address}")
                connect(found.address)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "onScanFailed code=$errorCode")
            // 错误码 1 = ALREADY_STARTED：系统里还有一次扫描在跑，稍后重试而不是放弃
            if (errorCode == SCAN_FAILED_ALREADY_STARTED) {
                handler.postDelayed({
                    if (_state.value.connection == ConnectionState.SCANNING) startScan()
                }, 1200)
                return
            }
            scanning = false
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = scanErrorMessage(errorCode),
            )
        }
    }

    /** 连上后定期拉一次配置：温度靠上报，但倒计时只有轮询才会走 */
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (_state.value.connection == ConnectionState.CONNECTED) {
                requestConfig()
                handler.postDelayed(this, 8000)
            }
        }
    }

    // ---- 扫描 ----

    @SuppressLint("MissingPermission")
    fun startScan() {
        val a = adapter ?: return
        if (!hasPermission()) {
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = "缺少蓝牙权限，请在系统设置里允许",
            )
            return
        }
        if (!a.isEnabled) {
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = "蓝牙未开启",
            )
            return
        }
        stopScanInternal()

        val seeds = loadKnownDevices()
        Log.d(TAG, "startScan seeds=${seeds.size}")
        _state.value = _state.value.copy(
            connection = ConnectionState.SCANNING,
            foundDevices = seeds,
            error = null,
        )
        if (seeds.isNotEmpty()) {
            // 已配对/上次连过的背夹不用等广播，直接连
            Log.d(TAG, "auto connect to known ${seeds.first().address}")
            connect(seeds.first().address)
            return
        }
        autoConnectArmed = true
        fallbackScheduled = false
        startScanInternal(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CoolerProtocol.SERVICE_UUID)).build()),
        )
        // 有些固件广播里不带服务 UUID，4 秒后仍没结果就换成无过滤扫描
        handler.postDelayed({
            if (scanning && _state.value.foundDevices.isEmpty() && !fallbackScheduled) {
                fallbackScheduled = true
                stopScanInternal()
                _state.value = _state.value.copy(connection = ConnectionState.SCANNING)
                startScanInternal(null)
                handler.postDelayed({ if (scanning) stopScan() }, 11000)
            }
        }, 4000)
        handler.postDelayed({ if (scanning) stopScan() }, 15000)
    }

    @SuppressLint("MissingPermission")
    private fun startScanInternal(filters: List<ScanFilter>?) {
        if (scanning) return
        try {
            adapter?.bluetoothLeScanner?.startScan(filters, scanSettings, scanCallback)
            scanning = true
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = "无法开始扫描：${e.message}",
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanInternal()
        if (_state.value.connection == ConnectionState.SCANNING ||
            _state.value.connection == ConnectionState.FAILED
        ) {
            _state.value = _state.value.copy(connection = ConnectionState.IDLE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        handler.removeCallbacksAndMessages(null)
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    private fun scanErrorMessage(code: Int): String = when (code) {
        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描注册失败，请关闭再打开蓝牙后重试"
        SCAN_FAILED_INTERNAL_ERROR -> "系统蓝牙内部错误，请重试"
        SCAN_FAILED_FEATURE_UNSUPPORTED -> "系统不支持 BLE 扫描"
        SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙资源紧张，请稍后重试"
        SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "扫描过于频繁，请稍后重试"
        else -> "扫描失败（错误码 $code）"
    }

    // ---- 连接 ----

    @SuppressLint("MissingPermission")
    fun connect(address: String, autoReconnect: Boolean = true) {
        val a = adapter ?: return
        if (!hasPermission()) {
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = "缺少蓝牙权限，请在系统设置里允许",
            )
            return
        }
        wantConnection = autoReconnect
        lastAddress = address
        prefs.edit().putString(KEY_LAST_ADDRESS, address).apply()
        stopScanInternal()
        closeGatt()
        _state.value = _state.value.copy(
            connection = ConnectionState.CONNECTING,
            address = address,
            error = null,
        )
        val device: BluetoothDevice = try {
            a.getRemoteDevice(address)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                connection = ConnectionState.FAILED,
                error = "地址无效：$address",
            )
            return
        }
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        wantConnection = false
        autoConnectArmed = false
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacksAndMessages(null)
        if (hasPermission()) {
            gatt?.disconnect()
        }
        closeGatt()
        _state.value = _state.value.copy(
            connection = ConnectionState.IDLE,
            temperature = null,
            mode = null,
            lightMode = null,
            lightColor = null,
            timerSeconds = null,
        )
    }

    private fun scheduleReconnect() {
        val address = lastAddress ?: return
        reconnectAttempts++
        _state.value = _state.value.copy(connection = ConnectionState.RECONNECTING)
        val delay = (1500L * reconnectAttempts).coerceAtMost(15000L)
        handler.postDelayed({
            if (wantConnection) connect(address)
        }, delay)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "connected ${g.device.address}")
                    prefs.edit()
                        .putString(KEY_LAST_ADDRESS, g.device.address)
                        .putString(KEY_LAST_NAME, g.device.name)
                        .apply()
                    reconnectAttempts = 0
                    _state.value = _state.value.copy(
                        connection = ConnectionState.CONNECTING,
                        deviceName = g.device.name ?: _state.value.deviceName,
                        address = g.device.address,
                        error = null,
                    )
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    g.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "disconnected status=$status")
                    handler.removeCallbacks(pollRunnable)
                    writeChar = null
                    notifyChar = null
                    closeGatt()
                    if (wantConnection) {
                        scheduleReconnect()
                    } else {
                        _state.value = _state.value.copy(
                            connection = ConnectionState.IDLE,
                            temperature = null,
                        )
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(
                    connection = ConnectionState.FAILED,
                    error = "服务发现失败：$status",
                )
                return
            }
            Log.d(TAG, "services discovered, uuids=${g.services.map { it.uuid }}")
            val service = g.getService(CoolerProtocol.SERVICE_UUID)
            if (service == null) {
                _state.value = _state.value.copy(
                    connection = ConnectionState.FAILED,
                    error = "设备没有背夹服务，可能不是小米冰封散热背夹",
                )
                return
            }
            writeChar = service.getCharacteristic(CoolerProtocol.WRITE_UUID)
            notifyChar = service.getCharacteristic(CoolerProtocol.NOTIFY_UUID)
            Log.d(TAG, "chars in AF00 = ${service.characteristics.map { it.uuid.toString().substring(4, 8) }} write=${writeChar != null} notify=${notifyChar != null}")
            if (writeChar == null || notifyChar == null) {
                _state.value = _state.value.copy(
                    connection = ConnectionState.FAILED,
                    error = "设备缺少读写特征",
                )
                return
            }
            enableNotifications(g, notifyChar!!)
            _state.value = _state.value.copy(connection = ConnectionState.CONNECTED, error = null)
            // 刚连上时立刻写会偶发 201（忙），稍等一下再读
            handler.postDelayed({ requestConfig() }, 400)
            handler.removeCallbacks(pollRunnable)
            handler.postDelayed(pollRunnable, 3000)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleIncoming(value)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(error = "写入失败：$status")
            }
        }
    }

    // ---- 通知 ----

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CoolerProtocol.CCCD_UUID)
        Log.d(TAG, "enableNotify descriptor=${descriptor != null}")
        if (descriptor == null) return
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
            if (descriptor.value != null) 0 else -1
        }
        Log.d(TAG, "enableNotify write=$status")
    }

    private fun handleIncoming(raw: ByteArray) {
        Log.d(TAG, "RX ${raw.hex()}")
        for (frame in CoolerProtocol.decodeAll(raw)) {
            if (frame.isCommand) continue
            Log.d(TAG, "frame op=${frame.op} seq=${frame.seq} tlvs=${frame.tlvs.map { it.type }}")
            var s = _state.value
            frame.tlv(CoolerProtocol.ATTR_TEMP)?.let {
                s = s.copy(temperature = CoolerProtocol.tempOf(it))
            }
            frame.tlv(CoolerProtocol.ATTR_MODE)?.let {
                s = s.copy(mode = CoolerMode.from(CoolerProtocol.modeOf(it)))
            }
            frame.tlv(CoolerProtocol.ATTR_TIMER)?.let {
                s = s.copy(timerSeconds = CoolerProtocol.timerOf(it))
            }
            frame.tlv(CoolerProtocol.ATTR_LIGHT)?.let {
                s = s.copy(
                    lightMode = LightMode.from(CoolerProtocol.lightModeOf(it)),
                    lightColor = LightColor.from(CoolerProtocol.lightColorOf(it)),
                )
            }
            _state.value = s
        }
    }

    // ---- 控制 ----

    fun requestConfig() {
        send(
            CoolerProtocol.encodeGetConfig(
                listOf(
                    CoolerProtocol.ATTR_TEMP,
                    CoolerProtocol.ATTR_MODE,
                    CoolerProtocol.ATTR_TIMER,
                    CoolerProtocol.ATTR_LIGHT,
                ),
                nextSeq(),
            ),
        )
    }

    fun setMode(mode: CoolerMode) {
        if (_state.value.connection != ConnectionState.CONNECTED) return
        send(
            CoolerProtocol.encodeSetConfig(
                listOf(CoolerProtocol.Tlv(CoolerProtocol.ATTR_MODE, CoolerProtocol.modeValue(mode.value))),
                nextSeq(),
            ),
        )
        _state.value = _state.value.copy(mode = mode)
    }

    fun setLight(mode: LightMode, color: LightColor) {
        if (_state.value.connection != ConnectionState.CONNECTED) return
        send(
            CoolerProtocol.encodeSetConfig(
                listOf(CoolerProtocol.Tlv(CoolerProtocol.ATTR_LIGHT, CoolerProtocol.lightValue(mode.value, color.value))),
                nextSeq(),
            ),
        )
        _state.value = _state.value.copy(lightMode = mode, lightColor = color)
    }

    /** seconds = null 表示取消定时 */
    fun setTimer(seconds: Int?) {
        if (_state.value.connection != ConnectionState.CONNECTED) return
        send(
            CoolerProtocol.encodeSetConfig(
                listOf(CoolerProtocol.Tlv(CoolerProtocol.ATTR_TIMER, CoolerProtocol.timerValue(seconds))),
                nextSeq(),
            ),
        )
        _state.value = _state.value.copy(timerSeconds = seconds)
    }

    @SuppressLint("MissingPermission")
    private fun send(frame: ByteArray) {
        val g = gatt ?: return
        val c = writeChar ?: return
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION")
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            c.value = frame
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
        Log.d(TAG, "TX ${frame.hex()} result=$ok")
    }

    private fun ByteArray.hex(): String = joinToString(" ") { String.format("%02X", it) }

    private fun nextSeq(): Int {
        seq = (seq + 1) and 0xFF
        return seq
    }

    /** 已配对 / 上次连接过的背夹：配对后设备可能不再广播，直接从系统里取 */
    @SuppressLint("MissingPermission")
    private fun loadKnownDevices(): List<FoundDevice> {
        val out = ArrayList<FoundDevice>()
        if (hasPermission()) {
            adapter?.bondedDevices?.forEach { d ->
                if (isCoolerName(d.name)) {
                    out.add(FoundDevice(d.name ?: "小米背夹", d.address, 0))
                }
            }
        }
        val addr = lastKnownAddress
        if (addr != null && out.none { it.address == addr }) {
            out.add(FoundDevice(lastKnownName ?: "上次连接的背夹", addr, 0))
        }
        return out
    }

    private fun isCoolerName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.contains("背夹") ||
            name.contains("COOLER", ignoreCase = true) ||
            name.contains("MI COOLER", ignoreCase = true)
    }

    private fun hasPermission(): Boolean {
        val scan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        val connect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        return scan == PackageManager.PERMISSION_GRANTED && connect == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CoolerBle"
        private const val KEY_LAST_ADDRESS = "last_address"
        private const val KEY_LAST_NAME = "last_name"

        const val SCAN_FAILED_ALREADY_STARTED = 1
        const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
        const val SCAN_FAILED_INTERNAL_ERROR = 3
        const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4
        const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5
        const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6
    }
}