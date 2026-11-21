package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
 * 风扇组件：一枚位于机身内部的扇叶组 + 每个开口内的灯盘。
 *
 * 叶片是一整枚大风扇，机身两个开口各露出它的一半，中缝和外壳压在上面；
 * 灯盘则按开口分别绘制，所以两边都能看到灯效。
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

private const val SECTORS = 8
private const val BLADES = 7

/** 一整枚扇叶组（不含灯盘），画在机身后面 */
fun DrawScope.drawFanBlades(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    spin: Boolean = true,
) {
    val bladeColor = Color(0xFF0D0F12).copy(alpha = 0.9f)
    for (i in 0 until BLADES) {
        rotate((if (spin) angleDeg else 0f) + i * (360f / BLADES), center) {
            val blade = Path().apply {
                val innerR = radius * 0.16f
                val outerR = radius * 0.97f
                moveTo(center.x - radius * 0.075f, center.y - innerR)
                cubicTo(
                    center.x - radius * 0.20f, center.y - outerR * 0.45f,
                    center.x - radius * 0.15f, center.y - outerR,
                    center.x - radius * 0.012f, center.y - outerR,
                )
                lineTo(center.x + radius * 0.026f, center.y - outerR)
                cubicTo(
                    center.x + radius * 0.075f, center.y - outerR * 0.45f,
                    center.x + radius * 0.05f, center.y - innerR,
                    center.x + radius * 0.05f, center.y - innerR,
                )
                close()
            }
            drawPath(blade, bladeColor)
        }
    }
    // 轮毂
    drawCircle(Color(0xFF181B1F), radius * 0.115f, center)
    drawCircle(Color(0xFF0E1013), radius * 0.09f, center)
    drawCircle(Color(0xFF8A9199), radius * 0.02f, center)
}

/** 一个开口内的分扇区灯盘 + LED 环 */
fun DrawScope.drawLedWheel(
    center: Offset,
    radius: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
) {
    if (lightMode == LightMode.OFF) return
    val sweep = 360f / SECTORS

    // 径向分层的扇区色块：内暗外亮
    val inner = radius * 0.34f
    val outer = radius * 0.80f
    val layers = 14
    for (k in 0 until layers) {
        val f = k / (layers - 1f)
        val rr = inner + (outer - inner) * f
        val width = (outer - inner) / layers + 0.8f
        val alpha = 0.05f + 0.55f * f * f
        for (s in 0 until SECTORS) {
            val t = (s + 0.5f) / SECTORS
            drawArc(
                color = LightEffect.colorAt(lightMode, lightColor, t, phase).copy(alpha = alpha),
                startAngle = s * sweep + 1.2f,
                sweepAngle = sweep - 2.4f,
                useCenter = false,
                topLeft = Offset(center.x - rr, center.y - rr),
                size = Size(rr * 2, rr * 2),
                style = Stroke(width = width),
            )
        }
    }

    // 外沿 LED
    val ringR = radius * 0.84f
    val ringW = radius * 0.035f
    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        drawArc(
            color = LightEffect.colorAt(lightMode, lightColor, t, phase),
            startAngle = s * sweep,
            sweepAngle = sweep - 1.5f,
            useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(width = ringW),
        )
        val rad = Math.toRadians(((s + 0.5f) * sweep).toDouble())
        val px = center.x + (ringR * kotlin.math.cos(rad)).toFloat()
        val py = center.y + (ringR * kotlin.math.sin(rad)).toFloat()
        drawCircle(Color.White.copy(alpha = 0.32f), ringW * 0.5f, Offset(px, py))
    }

    // 内溢光
    val glow = LightEffect.colorAt(lightMode, lightColor, 0.5f, phase)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, glow.copy(alpha = 0.14f), Color.Transparent),
            center = center,
            radius = radius * 0.9f,
        ),
        radius = radius * 0.9f,
        center = center,
    )
}
