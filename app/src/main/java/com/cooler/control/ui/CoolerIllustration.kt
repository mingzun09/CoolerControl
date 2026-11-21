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
import androidx.compose.runtime.getValue
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
import kotlin.math.sqrt

/** 底图原始尺寸（app/src/main/assets/cooler_body.webp） */
private const val IMAGE_RATIO = 897f / 676f

// 两个开口（挖洞位置）与整枚风扇的中心/半径，均为相对图片宽度的比例
private const val HOLE1_X = 0.478f
private const val HOLE1_Y = 0.352f
private const val HOLE1_R = 0.172f
private const val HOLE2_X = 0.478f
private const val HOLE2_Y = 0.678f
private const val HOLE2_R = 0.178f
private const val FAN_CX = 0.478f
private const val FAN_CY = 0.510f
private const val FAN_R = 0.290f

/**
 * 背夹外观：
 * 风扇画在**图片后面**，底图对应位置被"挖洞"，外壳边缘自然压住风扇，
 * 看起来就是嵌在机身内部的，而不是浮在图片上的一层贴纸。
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
    // 在两个开口处挖洞，得到"外壳遮罩"
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
    val angle by rememberFanAngle(mode, connected)
    val phase by rememberLightPhase()

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(IMAGE_RATIO)) {
            if (holed != null) {
                // 每个开口内一枚风扇 + 灯环（各自在自己的洞里）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val holes = listOf(
                        Triple(HOLE1_X, HOLE1_Y, HOLE1_R),
                        Triple(HOLE2_X, HOLE2_Y, HOLE2_R),
                    )
                    holes.forEachIndexed { i, h ->
                        val c = Offset(size.width * h.first, size.height * h.second)
                        val rr = size.width * h.third
                        drawLedWheel(
                            center = c,
                            radius = rr * 0.98f,
                            lightMode = lightMode,
                            lightColor = lightColor,
                            phase = phase,
                        )
                        drawFanBlades(
                            center = c,
                            radius = rr * 0.98f,
                            angleDeg = if (i == 0) angle else angle * 0.85f,
                        )
                    }
                }
                // 3) 两个开口内缘的接触阴影（洞外会被机身盖住）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    listOf(
                        Triple(HOLE1_X, HOLE1_Y, HOLE1_R),
                        Triple(HOLE2_X, HOLE2_Y, HOLE2_R),
                    ).forEach { h ->
                        val c = Offset(size.width * h.first, size.height * h.second)
                        val r = size.width * h.third
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.72f to Color.Transparent,
                                    0.92f to Color.Black.copy(alpha = 0.45f),
                                    1.00f to Color.Transparent,
                                ),
                                center = c,
                                radius = r * 1.05f,
                            ),
                            radius = r * 1.05f,
                            center = c,
                        )
                    }
                }
                // 最后画挖过洞的机身：外壳、中缝、黑网都会盖住风扇边缘
                Image(
                    bitmap = holed.asImageBitmap(),
                    contentDescription = "小米冰封散热背夹",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) { drawVectorFallback(darkTheme) }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawFanBlades(
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.width * 0.26f,
                        angleDeg = angle,
                    )
                    drawLedWheel(
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.width * 0.22f,
                        lightMode = lightMode,
                        lightColor = lightColor,
                        phase = phase,
                    )
                }
            }
        }
    }
}

/**
 * 从风扇中心泛洪，把连通的暗色区域（=开口）抠成透明，形成"外壳遮罩"。
 * 这样风扇只会在开口内露出，外壳与中缝会自然压住风扇。
 */
private fun punchHoles(src: Bitmap, holes: List<Triple<Float, Float, Float>>): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    holes.forEach { (cxF, cyF, rF) -> punchOne(out, cxF, cyF, rF) }
    return out
}

private fun punchOne(out: Bitmap, cxF: Float, cyF: Float, rF: Float) {
    val w = out.width
    val h = out.height
    val cx = (w * cxF).toInt()
    val cy = (h * cyF).toInt()
    val maxR = w * rF
    val maxR2 = maxR * maxR

    val src = out
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
        if (lum / 1000f > 95f) continue   // 亮色（外壳/中缝）不挖

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
    val stripW = shellW * 0.10f
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFCBD0D8))),
        topLeft = Offset(left + shellW / 2f - stripW / 2f, top + shellH * 0.06f),
        size = Size(stripW, shellH * 0.88f),
        cornerRadius = CornerRadius(stripW / 2f, stripW / 2f),
    )
}