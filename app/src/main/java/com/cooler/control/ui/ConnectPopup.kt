package com.cooler.control.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cooler.control.ble.CoolerState
import com.cooler.control.protocol.TempFeeling
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ColorOS 风格的配件连接弹窗：从顶部滑入的小卡片，显示设备名、温度与当前模式。
 */
@Composable
fun ConnectPopup(
    visible: Boolean,
    state: CoolerState,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(320)) { -it } + fadeIn(tween(220)) +
                scaleIn(tween(320), initialScale = 0.92f),
            exit = slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(180)) +
                scaleOut(tween(220), targetScale = 0.96f),
        ) {
            ConnectPopupCard(state, onDismiss)
        }
    }
}

@Composable
private fun ConnectPopupCard(state: CoolerState, onDismiss: () -> Unit) {
    val temp = state.temperature
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(MiuixTheme.colorScheme.surface)
            .clickable { onDismiss() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧：雪花徽标
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "❄", fontSize = 20.sp, color = MiuixTheme.colorScheme.primary)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.deviceName ?: "小米冰封散热背夹",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(
                    "已连接",
                    state.mode?.label,
                    temp?.let { TempFeeling.of(it) },
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (temp != null) "$temp" else "--",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = "℃",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp, start = 1.dp),
            )
        }
    }
}