package com.cooler.control.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class PermissionItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
)

/**
 * 首次启动的权限引导页，也可以在"设备信息 → 权限"里再次打开。
 */
@Composable
fun PermissionScreen(
    items: List<PermissionItem>,
    firstRun: Boolean,
    onRequest: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
) {
    val allGranted = items.all { it.granted }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = if (firstRun) "开始前需要几项权限" else "权限",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "这些权限只用于连接小米冰封散热背夹，不会用于其他用途。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        SectionCard(title = "需要的权限") {
            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title, fontSize = 16.sp)
                        Text(
                            text = item.desc,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        text = if (item.granted) "已允许" else "未允许",
                        fontSize = 14.sp,
                        color = if (item.granted) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Button(
                onClick = if (allGranted) onDone else onRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (allGranted) "开始使用" else "授予权限")
            }
            Spacer(Modifier.height(10.dp))
            if (!allGranted) {
                Button(onClick = if (firstRun) onSkip else onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (firstRun) "暂不授予，先看看" else "返回")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "提示：如果之前点了「拒绝且不再询问」，需要去系统设置的权限页手动打开。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}