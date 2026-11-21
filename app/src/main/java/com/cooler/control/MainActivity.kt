package com.cooler.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cooler.control.ble.ConnectionState
import com.cooler.control.service.CoolerService
import com.cooler.control.ui.CoolerTheme
import com.cooler.control.ui.HomeScreen
import com.cooler.control.ui.LightScreen
import com.cooler.control.ui.MoreScreen
import com.cooler.control.ui.PermissionItem
import com.cooler.control.ui.PermissionScreen
import com.cooler.control.ui.TimerScreen
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton

enum class Screen { Home, Light, Timer, More, Permission }

private const val PREFS = "cooler"
private const val KEY_ONBOARDED = "permission_onboarded"

class MainActivity : ComponentActivity() {

    private val vm: CoolerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CoolerTheme { CoolerApp(vm) }
        }
    }
}

/** App 实际需要的运行时权限 */
private fun runtimePermissions(): List<String> {
    val list = ArrayList<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CoolerApp(vm: CoolerViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var screen by remember { mutableStateOf(Screen.Home) }
    var showPermission by remember { mutableStateOf(!prefs.getBoolean(KEY_ONBOARDED, false)) }
    var permVersion by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permVersion++
        if (runtimePermissions().all { granted(context, it) }) vm.scan()
    }

    fun requestPermissions() = launcher.launch(runtimePermissions().toTypedArray())

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun permissionItems(): List<PermissionItem> {
        // 读取 permVersion，保证授权回来后状态刷新
        @Suppress("UNUSED_EXPRESSION") permVersion
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val notify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
        return listOf(
            PermissionItem(
                title = "附近的设备",
                desc = "扫描并连接小米冰封散热背夹（蓝牙）",
                granted = nearby,
            ),
            PermissionItem(
                title = "通知",
                desc = "常驻通知里显示温度，方便随时查看",
                granted = notify,
            ),
        )
    }

    // 首次进入且已授权时自动开始搜索
    LaunchedEffect(Unit) {
        if (!showPermission && runtimePermissions().all { granted(context, it) }) {
            // 有权限就拉起前台服务：后台保持连接 + 通知栏温度
            runCatching { CoolerService.start(context) }
            if (state.connection == ConnectionState.IDLE) vm.scan()
        }
    }

    if (showPermission) {
        Scaffold(
            topBar = { SmallTopAppBar(title = "权限") },
        ) { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(padding).padding(top = 4.dp),
            ) {
                PermissionScreen(
                    items = permissionItems(),
                    firstRun = !prefs.getBoolean(KEY_ONBOARDED, false),
                    onRequest = { requestPermissions() },
                    onDone = {
                        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                        showPermission = false
                        screen = Screen.Home
                        if (runtimePermissions().all { granted(context, it) }) {
                            runCatching { CoolerService.start(context) }
                            vm.scan()
                        }
                    },
                    onSkip = {
                        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
                        showPermission = false
                    },
                )
            }
        }
        return
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    val title = when (screen) {
        Screen.Home -> "散热背夹"
        Screen.Light -> "灯效"
        Screen.Timer -> "定时关闭"
        Screen.More -> "设备信息"
        Screen.Permission -> "权限"
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = if (screen == Screen.Home) {
                    {}
                } else {
                    { TextButton(text = "返回", onClick = { screen = Screen.Home }) }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding).padding(top = 4.dp)
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState != Screen.Home) {
                    // 进入子页面：新页面从右侧淡入，旧页面向左轻移
                    (slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it / 12 } + fadeOut(tween(140)))
                } else {
                    (slideInHorizontally(tween(300)) { -it / 12 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(300)) { it / 5 } + fadeOut(tween(140)))
                }
            },
            label = "screen",
        ) { current -> when (current) {
            Screen.Home -> HomeScreen(
                state = state,
                modifier = contentModifier,
                onScan = {
                    if (runtimePermissions().all { granted(context, it) }) vm.scan()
                    else requestPermissions()
                },
                onStopScan = vm::stopScan,
                onConnect = { address ->
                    if (runtimePermissions().all { granted(context, it) }) vm.connect(address)
                    else requestPermissions()
                },
                onDisconnect = vm::disconnect,
                onMode = vm::setMode,
                onOpenLight = { screen = Screen.Light },
                onOpenTimer = { screen = Screen.Timer },
                onOpenMore = { screen = Screen.More },
            )

            Screen.Light -> androidx.compose.foundation.layout.Box(modifier = contentModifier) {
                LightScreen(state) { mode, color -> vm.setLight(mode, color) }
            }

            Screen.Timer -> androidx.compose.foundation.layout.Box(modifier = contentModifier) {
                TimerScreen(state) { seconds -> vm.setTimer(seconds) }
            }

            Screen.More -> androidx.compose.foundation.layout.Box(modifier = contentModifier) {
                MoreScreen(
                    state = state,
                    onOpenPermission = { showPermission = true },
                )
            }

            Screen.Permission -> Unit
        }
        }
    }
}