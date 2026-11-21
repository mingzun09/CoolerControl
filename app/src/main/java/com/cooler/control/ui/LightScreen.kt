package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlin.math.PI
import kotlin.math.sin

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

/**
 * 用色块模拟灯带效果：
 * 常亮=整条按配色静止；呼吸=整体明暗起伏；流光=渐变横向流动；彩虹=色相循环；关闭=熄灭。
 */
@Composable
fun LightPreview(mode: LightMode, color: LightColor, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "light")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val segments = 24
    val schemeColors = listOf(
        Color(0xFF3D7EFF), Color(0xFF8A5CF6), Color(0xFF22C1C3), Color(0xFFFF8A3D), Color(0xFFFF4D6D),
    )

    Canvas(modifier = modifier) {
        val gap = 4.dp.toPx()
        val segW = (size.width - gap * (segments - 1)) / segments
        val h = size.height
        for (i in 0 until segments) {
            val x = i * (segW + gap)
            val base = baseColor(i, segments, color, schemeColors)
            val c = modulate(mode, base, i, segments, phase)
            drawRoundRect(
                color = c,
                topLeft = Offset(x, 0f),
                size = Size(segW, h),
                cornerRadius = CornerRadius(segW / 2f, segW / 2f),
            )
        }
    }
}

private fun baseColor(i: Int, n: Int, scheme: LightColor, palette: List<Color>): Color = when (scheme) {
    LightColor.SINGLE -> palette[0]
    LightColor.THREE -> palette[when ((i * 3) / n) {
        0 -> 0
        1 -> 1
        else -> 4
    }]
    LightColor.COLORFUL -> Color.hsv((i.toFloat() / n) * 360f, 0.75f, 1f)
}

private fun modulate(mode: LightMode, base: Color, i: Int, n: Int, phase: Float): Color = when (mode) {
    LightMode.OFF -> Color(0xFF2A2A2A)
    LightMode.ALWAYS_ON -> base
    LightMode.BREATH -> {
        val v = 0.25f + 0.75f * (0.5f + 0.5f * sin(2 * PI * phase).toFloat())
        base.copy(alpha = 1f).let {
            Color(it.red * v, it.green * v, it.blue * v, 1f)
        }
    }
    LightMode.FLOW -> Color.hsv(
        ((i.toFloat() / n + phase) % 1f) * 360f,
        0.8f,
        1f,
    )
    LightMode.RAINBOW -> Color.hsv(
        ((i.toFloat() / n + phase) % 1f) * 360f,
        0.9f,
        1f,
    )
}
