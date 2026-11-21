package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode
import kotlin.math.max

/**
 * 风扇组件：单独的 KT，负责"转速 + 灯效"两件事。
 *
 * - [rememberFanAngle] 根据散热模式给出旋转角度（自动/静音/高效/冰封转速不同）
 * - [drawFanMotor] 在画布上绘制一枚风扇：遮住底图原扇叶、画旋转扇叶、圈内 RGB 灯环
 */
@Composable
fun rememberFanAngle(mode: CoolerMode?, connected: Boolean): State<Float> {
    val speed = when {
        !connected -> 0f
        mode == CoolerMode.SILENT -> 0.30f
        mode == CoolerMode.AUTO -> 0.65f
        mode == CoolerMode.TURBO -> 1.10f
        else -> 1.60f
    }
    val transition = rememberInfiniteTransition(label = "fan")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2800 / max(speed, 0.30f)).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fanAngle",
    )
}

/**
 * 画一枚风扇。
 *
 * @param center 圆心
 * @param radius 扇叶半径（不含外框）
 * @param angleDeg 当前旋转角度
 * @param lightMode / lightColor 灯效，用于圈内 RGB
 * @param phase 灯效相位 0..1
 */
fun DrawScope.drawFanMotor(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
    spin: Boolean = true,
) {
    // 1. 深色底，盖住底图里静止的扇叶
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF15171A), Color(0xFF0A0B0D)),
            center = center,
            radius = radius * 1.05f,
        ),
        radius = radius,
        center = center,
    )

    // 2. 扇叶
    val blades = 5
    val bladeColor = Color(0xFF2F3339)
    for (i in 0 until blades) {
        rotate((if (spin) angleDeg else 0f) + i * (360f / blades), center) {
            val blade = Path().apply {
                val inner = radius * 0.22f
                val outer = radius * 0.97f
                moveTo(center.x - radius * 0.20f, center.y - inner)
                cubicTo(
                    center.x - radius * 0.46f, center.y - outer * 0.45f,
                    center.x - radius * 0.34f, center.y - outer,
                    center.x - radius * 0.02f, center.y - outer * 0.97f,
                )
                lineTo(center.x + radius * 0.12f, center.y - outer * 0.97f)
                cubicTo(
                    center.x + radius * 0.30f, center.y - outer * 0.45f,
                    center.x + radius * 0.18f, center.y - inner,
                    center.x + radius * 0.18f, center.y - inner,
                )
                close()
            }
            drawPath(blade, bladeColor)
            drawPath(blade, Color(0x18FFFFFF), style = Stroke(1.2f))
        }
    }

    // 3. 圈内 RGB 灯环（灯效）
    if (lightMode != LightMode.OFF) {
        val ringR = radius * 0.93f
        val ringW = radius * 0.055f
        val brush = when (lightMode) {
            LightMode.FLOW, LightMode.RAINBOW -> Brush.sweepGradient(
                colors = (0 until 24).map { i ->
                    LightEffect.colorAt(lightMode, lightColor, i / 24f, phase)
                } + LightEffect.colorAt(lightMode, lightColor, 1f, phase),
                center = center,
            )
            else -> Brush.linearGradient(
                colors = listOf(
                    LightEffect.colorAt(lightMode, lightColor, 0f, phase),
                    LightEffect.colorAt(lightMode, lightColor, 0.5f, phase),
                    LightEffect.colorAt(lightMode, lightColor, 1f, phase),
                ),
                start = Offset(center.x - ringR, center.y),
                end = Offset(center.x + ringR, center.y),
            )
        }
        drawCircle(brush = brush, radius = ringR, center = center, style = Stroke(ringW))
        // 内侧泛光：只往风扇里面晕，不溢出到外壳
        val glow = LightEffect.colorAt(lightMode, lightColor, 0.5f, phase)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, glow.copy(alpha = 0.16f), Color.Transparent),
                center = center,
                radius = radius * 0.86f,
            ),
            radius = radius * 0.86f,
            center = center,
        )
    }

    // 4. 轴心
    drawCircle(Color(0xFF30343A), radius * 0.34f, center)
    drawCircle(Color(0xFF0F1113), radius * 0.27f, center)
    drawCircle(Color(0xFF8A9199), radius * 0.06f, center)
}