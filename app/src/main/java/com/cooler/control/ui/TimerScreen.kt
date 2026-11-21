package com.cooler.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cooler.control.ble.CoolerState

@Composable
fun TimerScreen(state: CoolerState, onPick: (Int?) -> Unit) {
    val options = listOf<Pair<String, Int?>>(
        "不自动关闭" to null,
        "30 分钟" to 30 * 60,
        "1 小时" to 60 * 60,
        "2 小时" to 2 * 60 * 60,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionCard(title = "到时自动关闭") {
            options.forEach { (label, seconds) ->
                SelectableRow(
                    title = label,
                    selected = state.timerSeconds == seconds,
                    onClick = { onPick(seconds) },
                )
            }
        }
        if (state.timerSeconds != null) {
            SectionCard {
                SettingRow(
                    title = "剩余时间",
                    value = timerLabel(state.timerSeconds),
                    summary = "设备每秒上报，倒计时会实时更新",
                )
            }
        }
    }
}