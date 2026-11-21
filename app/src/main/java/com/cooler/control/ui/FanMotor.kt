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
 * 风扇组件：单独的 KT，负责"转速 + 灯效"。
 *
 * - [rememberFanAngle] 按散热模式给出旋转角度
 * - [drawFanMotor] 在底图的风扇开口内绘制旋转扇叶与灯环，
 *   边缘用渐变和阴影收进开口内部，避免看起来像贴了一层圆形贴纸
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

/** 灯环：位置和粗细都以开口半径为基准 */
private fun DrawScope.drawLedRing(
    center: Offset,
    openingRadius: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
    alpha: Float = 0.9f,
    thin: Boolean = false,
) {
    if (lightMode == LightMode.OFF) return
    val fanR = openingRadius * 0.80f
    val ringR = fanR * 0.94f
    val ringW = fanR * (if (thin) 0.035f else 0.07f)
    val colors = when (lightMode) {
        LightMode.FLOW, LightMode.RAINBOW -> (0 until 24).map { i ->
            LightEffect.colorAt(lightMode, lightColor, i / 24f, phase)
        } + LightEffect.colorAt(lightMode, lightColor, 1f, phase)
        else -> listOf(
            LightEffect.colorAt(lightMode, lightColor, 0f, phase),
            LightEffect.colorAt(lightMode, lightColor, 0.5f, phase),
            LightEffect.colorAt(lightMode, lightColor, 1f, phase),
        )
    }
    val brush = if (colors.size > 3) Brush.sweepGradient(colors, center) else Brush.linearGradient(
        colors,
        start = Offset(center.x - ringR, center.y),
        end = Offset(center.x + ringR, center.y),
    )
    drawCircle(brush = brush, radius = ringR, center = center, style = Stroke(ringW), alpha = alpha)

    if (!thin) {
        val glow = LightEffect.colorAt(lightMode, lightColor, 0.5f, phase)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, glow.copy(alpha = 0.12f), Color.Transparent),
                center = center,
                radius = fanR * 0.9f,
            ),
            radius = fanR * 0.9f,
            center = center,
        )
    }
}

/**
 * 在风扇开口里画一枚风扇。
 *
 * @param openingRadius 开口半径（与底图里的黑洞一致）
 * @param angleDeg 当前旋转角度
 * @param recess 内凹程度 0..1，越大越像陷进去
 */
fun DrawScope.drawFanMotor(
    center: Offset,
    openingRadius: Float,
    angleDeg: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
    spin: Boolean = true,
    recess: Float = 1f,
) {
    val r = openingRadius

    // 1. 开口底面：中心接近纯黑，向边缘渐隐，和底图的黑洞自然衔接
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF0B0C0E),
                0.72f to Color(0xFF0E1013),
                0.94f to Color(0xCC0C0E11),
                1.00f to Color(0x00000000),
            ),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )

    // 2. 内壁阴影：几层半透明描边模拟凹陷
    for (i in 1..3) {
        val t = i / 3f
        drawCircle(
            color = Color.Black.copy(alpha = 0.22f * recess * (1f - t * 0.5f)),
            radius = r * (0.99f - t * 0.06f),
            center = center,
            style = Stroke(width = r * 0.05f),
        )
    }

    // 3. 灯环：先画在扇叶下面，叶片会遮住一部分，更像灯在叶轮后方
    drawLedRing(center, openingRadius, lightMode, lightColor, phase)

    // 4. 旋转扇叶（比开口小一圈，露出内壁）
    val fanR = r * 0.80f
    val blades = 5
    val bladeColor = Color(0xFF2C3036)
    for (i in 0 until blades) {
        rotate((if (spin) angleDeg else 0f) + i * (360f / blades), center) {
            val inner = fanR * 0.22f
            val outer = fanR * 0.97f
            val blade = Path().apply {
                moveTo(center.x - fanR * 0.20f, center.y - inner)
                cubicTo(
                    center.x - fanR * 0.46f, center.y - outer * 0.45f,
                    center.x - fanR * 0.34f, center.y - outer,
                    center.x - fanR * 0.02f, center.y - outer * 0.97f,
                )
                lineTo(center.x + fanR * 0.12f, center.y - outer * 0.97f)
                cubicTo(
                    center.x + fanR * 0.30f, center.y - outer * 0.45f,
                    center.x + fanR * 0.18f, center.y - inner,
                    center.x + fanR * 0.18f, center.y - inner,
                )
                close()
            }
            drawPath(blade, bladeColor)
            drawPath(blade, Color(0x14FFFFFF), style = Stroke(1.2f))
        }
    }

    // 5. 灯环外露部分（半透明），避免整圈被叶片完全挡住
    drawLedRing(center, openingRadius, lightMode, lightColor, phase, alpha = 0.45f, thin = true)

    // 6. 轴心
    drawCircle(Color(0xFF2E3238), fanR * 0.30f, center)
    drawCircle(Color(0xFF0F1113), fanR * 0.24f, center)
    drawCircle(Color(0xFF8A9199), fanR * 0.05f, center)
}