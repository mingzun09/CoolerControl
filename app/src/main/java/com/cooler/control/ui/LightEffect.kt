package com.cooler.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode
import kotlin.math.PI
import kotlin.math.sin

/**
 * 灯效取色：把"模式 + 配色 + 相位"折算成颜色。
 * 预览条和风扇发光共用这一套逻辑，保证两处表现一致。
 */
object LightEffect {

    val palette = listOf(
        Color(0xFF3D7EFF), // 蓝
        Color(0xFF8A5CF6), // 紫
        Color(0xFF22C1C3), // 青
        Color(0xFFFF8A3D), // 橙
        Color(0xFFFF4D6D), // 粉
    )

    /** @param t 沿灯带的位置 0..1；@param phase 动画相位 0..1 */
    fun colorAt(mode: LightMode, scheme: LightColor, t: Float, phase: Float): Color = when (mode) {
        LightMode.OFF -> Color.Transparent
        LightMode.ALWAYS_ON -> base(scheme, t)
        LightMode.BREATH -> {
            val v = 0.35f + 0.65f * (0.5f + 0.5f * sin(2 * PI * phase).toFloat())
            dim(base(scheme, t), v)
        }
        LightMode.FLOW -> Color.hsv(((t + phase) % 1f) * 360f, 0.75f, 1f)
        LightMode.RAINBOW -> Color.hsv(((t * 0.5f + phase) % 1f) * 360f, 0.9f, 1f)
    }

    fun base(scheme: LightColor, t: Float): Color = when (scheme) {
        LightColor.SINGLE -> palette[0]
        LightColor.THREE -> palette[(t * 3f).toInt().coerceIn(0, 2).let {
            when (it) {
                0 -> 0
                1 -> 1
                else -> 4
            }
        }]
        LightColor.COLORFUL -> Color.hsv(((t % 1f) * 360f), 0.8f, 1f)
    }

    private fun dim(c: Color, v: Float) = Color(c.red * v, c.green * v, c.blue * v, 1f)
}

/** 灯效动画相位，2.4 秒一轮 */
@Composable
fun rememberLightPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "light")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lightPhase",
    )
}
