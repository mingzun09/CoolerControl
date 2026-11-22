package com.cooler.control.service

import android.app.Notification
import android.graphics.BitmapFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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
 * 前台服务：保持连接，并把状态做成"提升型常驻通知"。
 * ColorOS 会把这类通知渲染成流体云胶囊（capsule）。
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
        val modeLabel = state?.mode?.label

        val title = when {
            !connected -> "背夹未连接"
            temp != null -> "背夹 ${temp}℃"
            else -> "背夹已连接"
        }
        val text = when {
            !connected -> "点击打开，搜索并连接散热背夹"
            else -> listOfNotNull(modeLabel, state?.deviceName).joinToString(" · ")
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cycleIntent = Intent(this, CoolerActionReceiver::class.java).apply {
            action = CoolerActionReceiver.ACTION_CYCLE_MODE
        }
        val cyclePi = PendingIntent.getBroadcast(
            this, 1, cycleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_cooler_product)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "切换模式", cyclePi)
            .setShortcutId(SHORTCUT_ID)

        // Android 16：提升为"实时更新"，ColorOS 会渲染成流体云胶囊
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setShortCriticalText(
                when {
                    !connected -> "未连接"
                    temp != null -> "${temp}℃"
                    else -> "--"
                },
            )
            builder.setRequestPromotedOngoing(connected)
        }

        return builder.build()
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Log.d(TAG, "canPostPromoted=${nm.canPostPromotedNotifications()}")
        }
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "CoolerService"
        private const val CHANNEL_ID = "cooler_status"
        private const val NOTIFICATION_ID = 1001
        private const val SHORTCUT_ID = "cooler_live"

        fun start(context: Context) {
            val intent = Intent(context, CoolerService::class.java)
            context.startForegroundService(intent)
        }
    }
}