package com.cooler.control.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooler.control.ble.ConnectionState
import com.cooler.control.ble.CoolerState
import com.cooler.control.ble.FoundDevice
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.TempFeeling
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    state: CoolerState,
    modifier: Modifier = Modifier,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onMode: (CoolerMode) -> Unit,
    onOpenLight: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val connected = state.connection == ConnectionState.CONNECTED

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        DeviceCard(state, onScan, onStopScan, onConnect, onDisconnect)
        HeroCard(state, connected)
        ModeSliderCard(state, connected, onMode)

        SectionCard(title = "更多设置", enabled = connected) {
            SettingRow(
                title = "灯效设置",
                value = if (connected) state.lightMode?.label ?: "—" else "未连接",
                showArrow = true,
                enabled = connected,
                onClick = onOpenLight,
            )
            SettingRow(
                title = "定时关闭",
                value = if (connected) timerLabel(state.timerSeconds) else "未连接",
                showArrow = true,
                enabled = connected,
                onClick = onOpenTimer,
            )
            SettingRow(title = "设备信息", showArrow = true, onClick = onOpenMore)
        }

        state.error?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun DeviceCard(
    state: CoolerState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = state.connection == ConnectionState.CONNECTED
    val scanning = state.connection == ConnectionState.SCANNING
    SectionCard(title = "设备") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        connected -> state.deviceName ?: "小米冰封散热背夹"
                        scanning -> "正在搜索…"
                        state.connection == ConnectionState.CONNECTING -> "正在连接…"
                        state.connection == ConnectionState.RECONNECTING -> "正在重连…"
                        else -> "未连接"
                    },
                    fontSize = 16.sp,
                )
                Text(
                    text = when {
                        connected -> state.address ?: ""
                        scanning -> "搜到背夹后会自动连接"
                        else -> "点击搜索，或直接选择下方设备"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            when {
                connected -> Button(onClick = onDisconnect) { Text(text = "断开") }
                scanning -> Button(onClick = onStopScan) { Text(text = "停止") }
                else -> Button(onClick = onScan) { Text(text = "搜索") }
            }
        }
        if (!connected && state.foundDevices.isNotEmpty()) {
            state.foundDevices.forEach { dev: FoundDevice ->
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingRow(
                    title = dev.name,
                    summary = dev.address,
                    showArrow = true,
                    onClick = { onConnect(dev.address) },
                )
            }
        }
    }
}

/** 模型图 + 温度/温感，对应 HyperOS 的设备页上半部分 */
@Composable
private fun HeroCard(state: CoolerState, connected: Boolean) {
    val temp = if (connected) state.temperature else null
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoolerIllustration(
                mode = state.mode,
                connected = connected,
                darkTheme = isSystemInDarkTheme(),
                modifier = Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "背夹温度",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (temp != null) "${temp}℃" else "--",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                VerticalDivider(modifier = Modifier.height(40.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "背夹温感",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (temp != null) TempFeeling.of(temp) else "—",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 散热模式：四档滑块 */
@Composable
private fun ModeSliderCard(state: CoolerState, connected: Boolean, onMode: (CoolerMode) -> Unit) {
    val order = CoolerMode.displayOrder
    val currentIndex = order.indexOfFirst { it == state.mode }.coerceAtLeast(0)
    var slider by remember(state.mode, connected) { mutableFloatStateOf(currentIndex.toFloat()) }

    SectionCard(title = "散热模式", enabled = connected) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            val shown = order[slider.roundToInt().coerceIn(0, order.lastIndex)]
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (connected) shown.label else "未连接",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (connected) shown.desc else "连接后可调节",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = {
                    val idx = slider.roundToInt().coerceIn(0, order.lastIndex)
                    slider = idx.toFloat()
                    onMode(order[idx])
                },
                valueRange = 0f..order.lastIndex.toFloat(),
                keyPoints = order.indices.map { it.toFloat() },
                showKeyPoints = true,
                magnetThreshold = 0.12f,
                enabled = connected,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                order.forEachIndexed { i, m ->
                    Text(
                        text = m.label,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (connected && i == slider.roundToInt()) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

fun timerLabel(seconds: Int?): String {
    if (seconds == null) return "未启用"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}