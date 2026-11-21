package com.cooler.control.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.roundToInt

/**
 * 数字滚轮：按位滚动到目标值，像仪表盘一样逐位翻动。
 * 非数字字符（℃、负号等）静态显示。
 */
@Composable
fun OdometerText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize, color, fontWeight) {
        TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight)
    }
    val metric = remember(style) { measurer.measure("0", style) }
    val density = LocalDensity.current
    val rowHeightDp = with(density) { metric.size.height.toDp() }
    val colWidthDp = with(density) { metric.size.width.toDp() } * 1.02f

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            if (ch.isDigit()) {
                DigitWheel(
                    digit = ch.digitToInt(),
                    rowHeightDp = rowHeightDp,
                    colWidthDp = colWidthDp,
                    style = style,
                    measurer = measurer,
                )
            } else {
                Box(
                    modifier = Modifier.height(rowHeightDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = ch.toString(), color = color, fontSize = fontSize, fontWeight = fontWeight)
                }
            }
        }
    }
}

@Composable
private fun DigitWheel(
    digit: Int,
    rowHeightDp: androidx.compose.ui.unit.Dp,
    colWidthDp: androidx.compose.ui.unit.Dp,
    style: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeightDp.toPx() }
    val colWidthPx = with(density) { colWidthDp.toPx() }
    val digits = remember(style) { (0..9).map { measurer.measure(it.toString(), style) } }

    val anim = remember { Animatable(digit.toFloat()) }
    LaunchedEffect(digit) {
        anim.animateTo(digit.toFloat(), tween(durationMillis = 420, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier = Modifier
            .width(colWidthDp)
            .height(rowHeightDp)
            .clipToBounds(),
    ) {
        val offsetY = -anim.value * rowHeightPx
        digits.forEachIndexed { i, layout ->
            val top = offsetY + i * rowHeightPx + (rowHeightPx - layout.size.height) / 2f
            drawText(
                textLayoutResult = layout,
                topLeft = Offset((colWidthPx - layout.size.width) / 2f, top),
            )
        }
    }
}

/** 边框光晕扫过：trigger 递增时播放一次 */
@Composable
fun GlowBorder(
    trigger: Int,
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadiusDp: Int = 24,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        }
    }
    val progress = anim.value
    if (progress <= 0f || progress >= 1f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val x = -w * 0.8f + progress * w * 2.6f
        val brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = 0.9f), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + w * 0.7f, h),
        )
        drawRoundRect(
            brush = brush,
            cornerRadius = CornerRadius(cornerRadiusDp.toFloat().dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}