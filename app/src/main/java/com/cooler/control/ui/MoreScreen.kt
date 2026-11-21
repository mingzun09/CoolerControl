package com.cooler.control.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cooler.control.ble.CoolerState

private const val REPO_URL = "https://github.com/mingzun09/CoolerControl"

@Composable
fun MoreScreen(state: CoolerState, onOpenPermission: () -> Unit) {
    val context = LocalContext.current

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
        SectionCard(title = "开源地址") {
            SettingRow(
                title = "GitHub 仓库",
                summary = "github.com/mingzun09/CoolerControl",
                showArrow = true,
                onClick = { openUrl(context, REPO_URL) },
            )
            SettingRow(
                title = "问题反馈",
                summary = "提交 Issue 或建议",
                showArrow = true,
                onClick = { openUrl(context, "$REPO_URL/issues") },
            )
            SettingRow(
                title = "更新日志",
                summary = "CHANGELOG.md",
                showArrow = true,
                onClick = { openUrl(context, "$REPO_URL/blob/main/CHANGELOG.md") },
            )
        }
        SectionCard(title = "关于") {
            SettingRow(title = "协议", summary = "小米背夹私有 BLE（AF00 / AF07 / AF08）")
            SettingRow(title = "版本", value = "0.12.0")
            SettingRow(title = "说明", summary = "第三方客户端，与小米官方无关")
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
