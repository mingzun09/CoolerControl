package com.cooler.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cooler.control.ble.CoolerState
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode

@Composable
fun LightScreen(
    state: CoolerState,
    onPick: (LightMode, LightColor) -> Unit,
) {
    val mode = state.lightMode ?: LightMode.ALWAYS_ON
    val color = state.lightColor ?: LightColor.SINGLE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionCard(title = "效果预览") {
            LightPreview(
                mode = mode,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        SectionCard(title = "灯效模式") {
            LightMode.displayOrder.forEach { m ->
                SelectableRow(
                    title = m.label,
                    selected = mode == m,
                    onClick = { onPick(m, color) },
                )
            }
        }
        SectionCard(title = "配色", enabled = mode != LightMode.OFF) {
            LightColor.displayOrder.forEach { c ->
                SelectableRow(
                    title = c.label,
                    selected = color == c,
                    enabled = mode != LightMode.OFF,
                    onClick = { onPick(mode, c) },
                )
            }
        }
    }
}

/** 灯带预览：与风扇里的灯环共用 [LightEffect] 的取色逻辑 */
@Composable
fun LightPreview(mode: LightMode, color: LightColor, modifier: Modifier = Modifier) {
    val phase by rememberLightPhase()
    val segments = 24
    Canvas(modifier = modifier) {
        val gap = 4.dp.toPx()
        val segW = (size.width - gap * (segments - 1)) / segments
        val h = size.height
        for (i in 0 until segments) {
            val x = i * (segW + gap)
            val t = i / (segments - 1f)
            val c = if (mode == LightMode.OFF) Color(0xFF2A2A2A)
            else LightEffect.colorAt(mode, color, t, phase)
            drawRoundRect(
                color = c,
                topLeft = Offset(x, 0f),
                size = Size(segW, h),
                cornerRadius = CornerRadius(segW / 2f, segW / 2f),
            )
        }
    }
}
