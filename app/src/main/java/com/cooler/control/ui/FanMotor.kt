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
 * 风扇组件：单独的 KT，负责"转速 + 灯效"。
 *
 * 真机效果（见参考视频）：灯是一圈按扇区分色的灯盘，外沿更亮，
 * 细扇叶压在灯盘前面；整盘颜色随时间整体流转。
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

/**
 * 在风扇开口里画一枚风扇。
 *
 * @param openingRadius 开口半径（与底图里的黑洞一致）
 * @param angleDeg 当前旋转角度
 */
fun DrawScope.drawFanMotor(
    center: Offset,
    openingRadius: Float,
    angleDeg: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
    spin: Boolean = true,
) {
    val r = openingRadius
    val fanR = r * 0.92f

    // 1. 开口底面：中心近黑，边缘渐隐，和底图黑洞自然衔接
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF0A0B0D),
                0.70f to Color(0xFF0D0F12),
                0.94f to Color(0xCC0C0E11),
                1.00f to Color(0x00000000),
            ),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )

    if (lightMode != LightMode.OFF) {
        // 2. 分扇区灯盘：由内向外逐渐变亮，模仿真机的导光环
        val inner = fanR * 0.34f
        val outer = fanR * 0.80f
        val layers = 14
        val sweep = 360f / SECTORS
        for (k in 0 until layers) {
            val f = k / (layers - 1f)
            val radius = inner + (outer - inner) * f
            val width = (outer - inner) / layers + 0.8f
            val alpha = 0.05f + 0.55f * f * f
            for (s in 0 until SECTORS) {
                val t = (s + 0.5f) / SECTORS
                val c = LightEffect.colorAt(lightMode, lightColor, t, phase)
                drawArc(
                    color = c.copy(alpha = alpha),
                    startAngle = s * sweep + 1.2f,
                    sweepAngle = sweep - 2.4f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width),
                )
            }
        }

        // 3. 外沿最亮的一圈 LED
        val ringR = fanR * 0.84f
        val ringW = fanR * 0.035f
        for (s in 0 until SECTORS) {
            val t = (s + 0.5f) / SECTORS
            val c = LightEffect.colorAt(lightMode, lightColor, t, phase)
            drawArc(
                color = c,
                startAngle = s * sweep,
                sweepAngle = sweep - 1.5f,
                useCenter = false,
                topLeft = Offset(center.x - ringR, center.y - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = Stroke(width = ringW),
            )
            // 灯珠高光
            val mid = (s + 0.5f) * sweep
            val rad = Math.toRadians(mid.toDouble())
            val px = center.x + (ringR * kotlin.math.cos(rad)).toFloat()
            val py = center.y + (ringR * kotlin.math.sin(rad)).toFloat()
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = ringW * 0.5f, center = Offset(px, py))
        }

        // 4. 外溢光晕（轻微，压在底图黑区里）
        val glow = LightEffect.colorAt(lightMode, lightColor, 0.5f, phase)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, glow.copy(alpha = 0.16f), Color.Transparent),
                center = center,
                radius = r * 0.98f,
            ),
            radius = r * 0.98f,
            center = center,
        )
    }

    // 5. 细扇叶（压在灯盘前面，像真机那样露出大部分灯光）
    val blades = 7
    val bladeColor = Color(0xFF0E1013).copy(alpha = 0.82f)
    for (i in 0 until blades) {
        rotate((if (spin) angleDeg else 0f) + i * (360f / blades), center) {
            val blade = Path().apply {
                val innerR = fanR * 0.30f
                val outerR = fanR * 0.96f
                moveTo(center.x - fanR * 0.10f, center.y - innerR)
                cubicTo(
                    center.x - fanR * 0.24f, center.y - outerR * 0.45f,
                    center.x - fanR * 0.18f, center.y - outerR,
                    center.x - fanR * 0.02f, center.y - outerR,
                )
                lineTo(center.x + fanR * 0.03f, center.y - outerR)
                cubicTo(
                    center.x + fanR * 0.09f, center.y - outerR * 0.45f,
                    center.x + fanR * 0.06f, center.y - innerR,
                    center.x + fanR * 0.06f, center.y - innerR,
                )
                close()
            }
            drawPath(blade, bladeColor)
        }
    }

    // 6. 轴心
    drawCircle(Color(0xFF1B1E22), fanR * 0.30f, center)
    drawCircle(Color(0xFF0E1013), fanR * 0.24f, center)
    drawCircle(Color(0xFF9AA1A9), fanR * 0.055f, center)
}