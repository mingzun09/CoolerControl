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
fun MoreScreen(state: CoolerState, onOpenPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionCard(title = "设备信息") {
            SettingRow(title = "名称", value = state.deviceName ?: "—")
            SettingRow(title = "地址", value = state.address ?: "—")
            SettingRow(title = "当前温度", value = state.temperature?.let { "$it ℃" } ?: "—")
            SettingRow(title = "当前模式", value = state.mode?.label ?: "—")
            SettingRow(title = "定时关闭", value = timerLabel(state.timerSeconds))
        }
        SectionCard(title = "权限") {
            SettingRow(title = "权限管理", showArrow = true, onClick = onOpenPermission)
        }
        SectionCard(title = "关于") {
            SettingRow(title = "协议", summary = "小米背夹私有 BLE（AF00 / AF07 / AF08）")
            SettingRow(title = "版本", value = "0.3.0")
            SettingRow(title = "说明", summary = "第三方客户端，与小米官方无关")
        }
    }
}
