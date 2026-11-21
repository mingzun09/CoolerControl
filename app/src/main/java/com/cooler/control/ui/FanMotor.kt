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
import androidx.compose.ui.graphics.lerp
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 风扇与灯效的绘制 */
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

/** 扇叶组：细叶片 + 高光边缘，画在灯盘之上 */
fun DrawScope.drawFanBlades(
    center: Offset,
    radius: Float,
    angleDeg: Float,
    spin: Boolean = true,
) {
    val base = Color(0xFF121418)
    for (i in 0 until BLADES) {
        rotate((if (spin) angleDeg else 0f) + i * (360f / BLADES), center) {
            val innerR = radius * 0.20f
            val outerR = radius * 0.95f
            val blade = Path().apply {
                moveTo(center.x - radius * 0.055f, center.y - innerR)
                cubicTo(
                    center.x - radius * 0.16f, center.y - outerR * 0.42f,
                    center.x - radius * 0.13f, center.y - outerR * 0.98f,
                    center.x - radius * 0.005f, center.y - outerR,
                )
                lineTo(center.x + radius * 0.02f, center.y - outerR)
                cubicTo(
                    center.x + radius * 0.06f, center.y - outerR * 0.45f,
                    center.x + radius * 0.045f, center.y - innerR,
                    center.x + radius * 0.045f, center.y - innerR,
                )
                close()
            }
            drawPath(
                blade,
                brush = Brush.linearGradient(
                    colors = listOf(base, Color(0xFF262A30)),
                    start = Offset(center.x, center.y - outerR),
                    end = Offset(center.x, center.y),
                ),
            )
            drawPath(blade, Color(0x1AFFFFFF), style = Stroke(1.1f))
        }
    }
    // 轮毂
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF2A2E34), Color(0xFF101215)),
            center = center,
            radius = radius * 0.13f,
        ),
        radius = radius * 0.13f,
        center = center,
    )
    drawCircle(Color(0xFF0A0B0D), radius * 0.085f, center)
    drawCircle(Color(0xFF9BA2AA), radius * 0.018f, center)
}

/**
 * 灯盘：扇区底色 + 发光环 + 灯珠高光。
 * 绘制顺序上它在扇叶之前，所以光会从叶缝里透出来。
 */
fun DrawScope.drawLedWheel(
    center: Offset,
    radius: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
) {
    if (lightMode == LightMode.OFF) return
    val sweep = 360f / SECTORS

    // 1) 扇区底色：整片淡淡的一层，颜色随角度变化
    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val c = LightEffect.colorAt(lightMode, lightColor, t, phase)
        drawArc(
            color = c.copy(alpha = 0.22f),
            startAngle = s * sweep + 1.5f,
            sweepAngle = sweep - 3f,
            useCenter = true,
            topLeft = Offset(center.x - radius * 0.62f, center.y - radius * 0.62f),
            size = Size(radius * 1.24f, radius * 1.24f),
        )
    }

    // 2) 光晕：宽而淡，营造"透出来"的感觉
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.55f to Color.Transparent,
                0.82f to LightEffect.colorAt(lightMode, lightColor, 0.5f, phase).copy(alpha = 0.16f),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius * 0.95f,
        ),
        radius = radius * 0.95f,
        center = center,
    )

    // 3) 发光环：外柔光 + 中层 + 亮芯，逐层叠加
    val ringR = radius * 0.80f
    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val c = LightEffect.colorAt(lightMode, lightColor, t, phase)
        val start = s * sweep + 0.6f
        val sw = sweep - 1.2f
        drawArc(
            color = c.copy(alpha = 0.18f),
            startAngle = start, sweepAngle = sw, useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(radius * 0.26f),
        )
        drawArc(
            color = c.copy(alpha = 0.65f),
            startAngle = start, sweepAngle = sw, useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(radius * 0.07f),
        )
        drawArc(
            color = lerp(c, Color.White, 0.55f),
            startAngle = start, sweepAngle = sw, useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(radius * 0.022f),
        )
    }

    // 4) 灯珠高光
    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val c = LightEffect.colorAt(lightMode, lightColor, t, phase)
        val ang = Math.toRadians(((s + 0.5f) * sweep).toDouble())
        val x = center.x + (ringR * cos(ang)).toFloat()
        val y = center.y + (ringR * sin(ang)).toFloat()
        drawCircle(lerp(c, Color.White, 0.6f).copy(alpha = 0.85f), radius * 0.035f, Offset(x, y))
    }
}

/** 玻璃反光：让开口看起来有层镜面 */
fun DrawScope.drawGlassSheen(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = 0.001f),
                0.55f to Color.White.copy(alpha = 0.05f),
                0.80f to Color.White.copy(alpha = 0.015f),
                1.0f to Color.Transparent,
            ),
            center = Offset(center.x - radius * 0.22f, center.y - radius * 0.26f),
            radius = radius * 1.1f,
        ),
        radius = radius,
        center = center,
    )
}
