package com.cooler.control.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cooler.control.CoolerApp
import com.cooler.control.MainActivity
import com.cooler.control.R
import com.cooler.control.ble.ConnectionState
import com.cooler.control.ble.CoolerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台服务：让连接在后台存活，并在通知栏显示实时温度。
 */
class CoolerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, build(null))
        val client = (application as CoolerApp).client
        job = scope.launch {
            client.state.collectLatest { s -> notify(build(s)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "背夹状态", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示背夹温度和连接状态"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun build(state: CoolerState?): Notification {
        val connected = state?.connection == ConnectionState.CONNECTED
        val temp = state?.temperature
        val title = when {
            !connected -> "背夹未连接"
            temp != null -> "背夹 ${temp}℃"
            else -> "背夹已连接"
        }
        val text = when {
            !connected -> "点击打开，搜索并连接散热背夹"
            temp != null -> listOfNotNull(state?.mode?.label, state?.deviceName).joinToString(" · ")
            else -> state?.deviceName ?: "等待设备上报"
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL_ID = "cooler_status"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, CoolerService::class.java)
            context.startForegroundService(intent)
        }
    }
}
