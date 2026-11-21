package com.cooler.control.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.cooler.control.protocol.CoolerMode
import com.cooler.control.protocol.LightColor
import com.cooler.control.protocol.LightMode

/** 底图原始比例（app/src/main/assets/cooler_body.webp） */
private const val IMAGE_RATIO = 897f / 676f

// 两处镂空的位置（用于把机身"挖穿"），以及整枚风扇的半径比例
private const val HOLE1_X = 0.478f
private const val HOLE1_Y = 0.352f
private const val HOLE1_R = 0.172f
private const val HOLE2_X = 0.478f
private const val HOLE2_Y = 0.678f
private const val HOLE2_R = 0.178f

/** 风扇半径：按整个背夹高度取比例 */
private const val FAN_RADIUS_RATIO = 0.40f

/**
 * 三层 Z 轴叠加（画家算法），全部共享同一个全局中心：
 *
 *   1. 底壳 + 黑色圆形内腔
 *   2. 灯盘 + 扇叶（一枚，圆心 = 全局中心，半径 = 高度 × 0.40）
 *   3. 抠空的机身（外壳 + COOLER FOR GAMING 横梁）压在最上面，自然遮住风扇中间一条
 *
 * 没有上下分区，也没有第二个风扇。
 */
@Composable
fun CoolerIllustration(
    mode: CoolerMode?,
    connected: Boolean,
    lightMode: LightMode,
    lightColor: LightColor,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = true,
) {
    val context = LocalContext.current
    val origin = remember {
        runCatching {
            context.assets.open("cooler_body.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    val holed = remember(origin) {
        origin?.let {
            punchHoles(
                it,
                listOf(
                    Triple(HOLE1_X, HOLE1_Y, HOLE1_R * 1.04f),
                    Triple(HOLE2_X, HOLE2_Y, HOLE2_R * 1.04f),
                ),
            )
        }
    }
    val motion = rememberFanMotion(mode, connected)
    val (lightPhase, lightAlpha) = rememberLightState(lightMode)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(IMAGE_RATIO),
            contentAlignment = Alignment.Center,
        ) {
            if (holed != null) {
                // 第 1、2 层：内腔 + 灯盘 + 扇叶（单一全局中心）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.height * FAN_RADIUS_RATIO
                    drawCircle(Color(0xFF090A0D), radius = radius, center = center)
                    drawLedWheel(center, radius, lightMode, lightColor, lightPhase, lightAlpha)
                    drawFanBlades(center, radius, motion)
                    drawCavityShadow(center, radius)
                }
                // 第 3 层：机身（两处镂空），横梁与外壳压住风扇
                Image(
                    bitmap = holed.asImageBitmap(),
                    contentDescription = "小米冰封散热背夹",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.height * FAN_RADIUS_RATIO
                    drawVectorFallback(darkTheme)
                    drawCircle(Color(0xFF090A0D), radius = radius, center = center)
                    drawLedWheel(center, radius, lightMode, lightColor, lightPhase, lightAlpha)
                    drawFanBlades(center, radius, motion)
                    drawCavityShadow(center, radius)
                }
            }
        }
    }
}

/**
 * 把机身图片上两处风扇开口挖成透明（泛洪，只吃掉连通的暗色区域，
 * 白色外壳和中间横梁保持不透明，用于遮挡风扇）。
 */
private fun punchHoles(src: Bitmap, holes: List<Triple<Float, Float, Float>>): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    holes.forEach { (cxF, cyF, rF) -> punchOne(out, cxF, cyF, rF) }
    return out
}

private fun punchOne(out: Bitmap, cxF: Float, cyF: Float, rF: Float) {
    val w = out.width
    val h = out.height
    val src = out
    val cx = (w * cxF).toInt()
    val cy = (h * cyF).toInt()
    val maxR = w * rF
    val maxR2 = maxR * maxR

    val visited = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var top = 0
    stack[top++] = cy * w + cx

    while (top > 0) {
        val idx = stack[--top]
        if (visited[idx]) continue
        visited[idx] = true
        val x = idx % w
        val y = idx / w
        val dx = x - cx
        val dy = y - cy
        if (dx * dx + dy * dy > maxR2) continue

        val p = src.getPixel(x, y)
        val lum = (p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114
        if (lum / 1000f > 95f) continue

        out.setPixel(x, y, 0)

        if (x > 0 && !visited[idx - 1]) stack[top++] = idx - 1
        if (x < w - 1 && !visited[idx + 1]) stack[top++] = idx + 1
        if (y > 0 && !visited[idx - w]) stack[top++] = idx - w
        if (y < h - 1 && !visited[idx + w]) stack[top++] = idx + w
    }
}

/** 没有实拍底图时的自绘外壳（开源仓库默认走这里） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVectorFallback(darkTheme: Boolean) {
    val w = size.width
    val h = size.height
    val shellW = w * 0.66f
    val shellH = h * 0.86f
    val left = (w - shellW) / 2f
    val top = (h - shellH) / 2f
    val cut = shellW * 0.16f
    val shell = Path().apply {
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
}
