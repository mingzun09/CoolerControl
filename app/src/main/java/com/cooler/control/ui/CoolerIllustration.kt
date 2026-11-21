package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import kotlin.math.max

/**
 * 背夹外观示意（自绘矢量图，不依赖任何官方素材）。
 * 风扇转速随模式变化，未连接时停转。
 */
@Composable
fun CoolerIllustration(
    mode: CoolerMode?,
    connected: Boolean,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = true,
) {
    val speed = when {
        !connected -> 0f
        mode == CoolerMode.SILENT -> 0.35f
        mode == CoolerMode.AUTO -> 0.7f
        mode == CoolerMode.TURBO -> 1.15f
        else -> 1.6f
    }
    val transition = rememberInfiniteTransition(label = "fan")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2600 / max(speed, 0.35f)).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spin = if (connected) angle else 0f

        // 外壳：圆角八边形
        val shellW = w * 0.66f
        val shellH = h * 0.86f
        val left = (w - shellW) / 2f
        val top = (h - shellH) / 2f
        val shell = Path().apply {
            val cut = shellW * 0.16f
            moveTo(left + cut, top)
            lineTo(left + shellW - cut, top)
            lineTo(left + shellW, top + cut)
            lineTo(left + shellW, top + shellH - cut)
            lineTo(left + shellW - cut, top + shellH)
            lineTo(left + cut, top + shellH)
            lineTo(left, top + shellH - cut)
            lineTo(left, top + cut)
            close()
        }
        drawPath(
            path = shell,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFF4F6F8), Color(0xFFD7DBE1), Color(0xFFB9BFC8)),
            ),
        )
        drawPath(
            path = shell,
            color = if (darkTheme) Color(0x33FFFFFF) else Color(0x22000000),
            style = Stroke(width = 1.5f),
        )

        // 左右两个风扇
        val fanR = shellW * 0.30f
        val cy = top + shellH / 2f
        drawFan(Offset(left + shellW * 0.27f, cy), fanR, spin)
        drawFan(Offset(left + shellW * 0.73f, cy), fanR, -spin)

        // 中间银色条
        val stripW = shellW * 0.10f
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFCBD0D8)),
            ),
            topLeft = Offset(left + shellW / 2f - stripW / 2f, top + shellH * 0.06f),
            size = Size(stripW, shellH * 0.88f),
            cornerRadius = CornerRadius(stripW / 2f, stripW / 2f),
        )

        // 四角螺钉
        val screwR = shellW * 0.018f
        listOf(
            Offset(left + shellW * 0.10f, top + shellH * 0.12f),
            Offset(left + shellW * 0.90f, top + shellH * 0.12f),
            Offset(left + shellW * 0.10f, top + shellH * 0.88f),
            Offset(left + shellW * 0.90f, top + shellH * 0.88f),
        ).forEach { drawCircle(Color(0xFF9AA1AB), screwR, it) }
    }
}

private fun DrawScope.drawFan(center: Offset, radius: Float, spin: Float) {
    // 外圈
    drawCircle(Color(0xFF2B2F35), radius, center)
    drawCircle(Color(0xFF3A3F46), radius * 0.94f, center)
    // 扇叶
    val blades = 6
    for (i in 0 until blades) {
        rotate(spin + i * (360f / blades), center) {
            val blade = Path().apply {
                val inner = radius * 0.22f
                val outer = radius * 0.86f
                moveTo(center.x - radius * 0.10f, center.y - inner)
                cubicTo(
                    center.x - radius * 0.30f, center.y - outer * 0.55f,
                    center.x - radius * 0.24f, center.y - outer,
                    center.x - radius * 0.02f, center.y - outer,
                )
                lineTo(center.x + radius * 0.06f, center.y - outer)
                cubicTo(
                    center.x + radius * 0.16f, center.y - outer * 0.5f,
                    center.x + radius * 0.10f, center.y - inner,
                    center.x + radius * 0.10f, center.y - inner,
                )
                close()
            }
            drawPath(blade, Color(0xFF6E747C))
        }
    }
    // 轴心
    drawCircle(Color(0xFF4A4F57), radius * 0.26f, center)
    drawCircle(Color(0xFF23262B), radius * 0.20f, center)
    drawCircle(Color(0xFF8E959E), radius * 0.06f, center)
}
