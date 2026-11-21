package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SECTORS = 12
private const val BLADES = 7

/** 风扇运动状态：角度 + 角速度（用于高速残影） */
@Stable
data class FanMotionState(val angleDeg: Float, val velocityDegPerSec: Float)

/** 带惯性加减速的转动：切档时自然过渡，停稳后不再消耗帧 */
@Composable
fun rememberFanMotion(mode: CoolerMode?, connected: Boolean): FanMotionState {
    val targetVelocity = when {
        !connected || mode == null -> 0f
        mode == CoolerMode.SILENT -> 180f
        mode == CoolerMode.AUTO -> 420f
        else -> 1260f
    }
    val velocity by animateFloatAsState(
        targetValue = targetVelocity,
        animationSpec = tween(durationMillis = 900),
        label = "fanVelocity",
    )
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(velocity > 0.5f) {
        if (velocity <= 0.5f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    angle = (angle + velocity * dt) % 360f
                }
                last = now
            }
        }
    }
    return FanMotionState(angle, velocity)
}

/** 灯效相位 + 开关淡入淡出 */
@Composable
fun rememberLightState(lightMode: LightMode): Pair<Float, Float> {
    val transition = rememberInfiniteTransition(label = "rgbLight")
    val duration = when (lightMode) {
        LightMode.FLOW -> 1800
        LightMode.RAINBOW -> 3600
        LightMode.BREATH -> 2800
        LightMode.OFF -> 3600
        else -> 3000
    }
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lightPhase",
    )
    val masterAlpha by animateFloatAsState(
        targetValue = if (lightMode == LightMode.OFF) 0f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "lightAlpha",
    )
    return phase to masterAlpha
}

/** 扇叶组：高速残影 + 高光边，画在灯盘之上 */
fun DrawScope.drawFanBlades(center: Offset, radius: Float, motion: FanMotionState) {
    val speed = motion.velocityDegPerSec
    if (speed > 550f) {
        val f = ((speed - 550f) / 710f).coerceIn(0f, 1f)
        val step = 7f + 7f * f
        drawBladeLayer(center, radius, motion.angleDeg - step * 3f, 0.08f * f)
        drawBladeLayer(center, radius, motion.angleDeg - step * 2f, 0.16f * f)
        drawBladeLayer(center, radius, motion.angleDeg - step, 0.28f * f)
    }
    drawBladeLayer(center, radius, motion.angleDeg, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF2E333A), Color(0xFF101215)),
            center = center,
            radius = radius * 0.14f,
        ),
        radius = radius * 0.14f,
        center = center,
    )
    drawCircle(Color(0xFF0A0B0D), radius * 0.088f, center)
    drawCircle(Color(0xFF9BA2AA), radius * 0.018f, center)
}

private fun DrawScope.drawBladeLayer(center: Offset, radius: Float, angleDeg: Float, alpha: Float) {
    val base = Color(0xFF121418).copy(alpha = alpha)
    val tip = Color(0xFF282D34).copy(alpha = alpha)
    val edge = Color(0x1EFFFFFF).copy(alpha = 0.12f * alpha)
    for (i in 0 until BLADES) {
        rotate(angleDeg + i * (360f / BLADES), center) {
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
                    colors = listOf(base, tip),
                    start = Offset(center.x, center.y - outerR),
                    end = Offset(center.x, center.y),
                ),
            )
            if (alpha > 0.5f) drawPath(blade, edge, style = Stroke(1.1f))
        }
    }
}

/** 灯盘：扇区底色 + 发光环 + 灯珠，画在扇叶之前 */
fun DrawScope.drawLedWheel(
    center: Offset,
    radius: Float,
    lightMode: LightMode,
    lightColor: LightColor,
    phase: Float,
    masterAlpha: Float = 1f,
) {
    if (masterAlpha <= 0.01f) return
    val sweep = 360f / SECTORS
    val breathFactor = if (lightMode == LightMode.BREATH) {
        0.15f + 0.85f * (0.5f + 0.5f * sin(phase * 2.0 * PI).toFloat())
    } else 1f
    val effectiveAlpha = masterAlpha * breathFactor

    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val (c, intensity) = sampleRgbColor(lightMode, lightColor, t, phase)
        val a = 0.24f * intensity * effectiveAlpha
        if (a <= 0.005f) continue
        drawArc(
            color = c.copy(alpha = a),
            startAngle = s * sweep + 1f,
            sweepAngle = sweep - 2f,
            useCenter = true,
            topLeft = Offset(center.x - radius * 0.68f, center.y - radius * 0.68f),
            size = Size(radius * 1.36f, radius * 1.36f),
            blendMode = BlendMode.Plus,
        )
    }

    val halo = sampleRgbColor(lightMode, lightColor, 0.5f, phase).first
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.48f to Color.Transparent,
                0.80f to halo.copy(alpha = 0.22f * effectiveAlpha),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius * 0.96f,
        ),
        radius = radius * 0.96f,
        center = center,
        blendMode = BlendMode.Plus,
    )

    val ringR = radius * 0.80f
    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val (c, intensity) = sampleRgbColor(lightMode, lightColor, t, phase)
        val a = intensity * effectiveAlpha
        if (a <= 0.01f) continue
        val start = s * sweep + 0.5f
        val sw = sweep - 1f
        val topLeft = Offset(center.x - ringR, center.y - ringR)
        val arcSize = Size(ringR * 2, ringR * 2)
        drawArc(
            color = c.copy(alpha = 0.20f * a), startAngle = start, sweepAngle = sw,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(radius * 0.26f), blendMode = BlendMode.Plus,
        )
        drawArc(
            color = c.copy(alpha = 0.68f * a), startAngle = start, sweepAngle = sw,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(radius * 0.07f), blendMode = BlendMode.Plus,
        )
        drawArc(
            color = lerp(c, Color.White, 0.55f).copy(alpha = a), startAngle = start, sweepAngle = sw,
            useCenter = false, topLeft = topLeft, size = arcSize,
            style = Stroke(radius * 0.022f), blendMode = BlendMode.Plus,
        )
    }

    for (s in 0 until SECTORS) {
        val t = (s + 0.5f) / SECTORS
        val (c, intensity) = sampleRgbColor(lightMode, lightColor, t, phase)
        val a = 0.85f * intensity * effectiveAlpha
        if (a <= 0.02f) continue
        val ang = Math.toRadians(((s + 0.5f) * sweep).toDouble())
        val x = center.x + (ringR * cos(ang)).toFloat()
        val y = center.y + (ringR * sin(ang)).toFloat()
        drawCircle(
            color = lerp(c, Color.White, 0.65f).copy(alpha = a),
            radius = radius * 0.032f,
            center = Offset(x, y),
            blendMode = BlendMode.Plus,
        )
    }
}

/** 腔体内阴影，增强硬件纵深 */
fun DrawScope.drawCavityShadow(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.76f to Color.Transparent,
                0.92f to Color.Black.copy(alpha = 0.45f),
                1.00f to Color.Black.copy(alpha = 0.85f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** 玻璃反光 */
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

/** 灯效取色：模式 × 配色 × 相位 → (颜色, 局部亮度) */
private fun sampleRgbColor(
    mode: LightMode,
    scheme: LightColor,
    t: Float,
    phase: Float,
): Pair<Color, Float> = when (mode) {
    LightMode.OFF -> Color.Transparent to 0f
    LightMode.RAINBOW -> {
        val hue = ((t + phase) % 1f) * 360f
        Color.hsv(hue, 0.92f, 1f) to 1f
    }
    LightMode.FLOW -> {
        val dist = (phase - t + 1f) % 1f
        val tail = (1f - dist).coerceIn(0f, 1f)
        val intensity = 0.12f + 0.88f * (tail * tail * tail)
        val hue = ((t * 0.35f + phase) % 1f) * 360f
        lerp(Color.hsv(hue, 0.88f, 1f), schemeColor(scheme, t), 0.35f) to intensity
    }
    LightMode.BREATH -> schemeColor(scheme, t) to 1f
    LightMode.ALWAYS_ON -> schemeColor(scheme, t) to 1f
}

/** 配色：单色 / 三色 / 彩色 */
private fun schemeColor(scheme: LightColor, t: Float): Color = when (scheme) {
    LightColor.SINGLE -> Color(0xFF00B0FF)
    LightColor.THREE -> when ((t * 3f).toInt().coerceIn(0, 2)) {
        0 -> Color(0xFFFF2A4B)
        1 -> Color(0xFF00E676)
        else -> Color(0xFF00B0FF)
    }
    LightColor.COLORFUL -> Color.hsv((t % 1f) * 360f, 0.85f, 1f)
}