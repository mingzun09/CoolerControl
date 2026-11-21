package com.cooler.control.ui

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

/** 底图原始尺寸（app/src/main/assets/cooler_body.webp） */
private const val IMAGE_RATIO = 897f / 676f

// 两个风扇在底图中的相对位置与半径（按图片标定）
private const val FAN1_X = 0.468f
private const val FAN1_Y = 0.336f
private const val FAN1_R = 0.165f
private const val FAN2_X = 0.468f
private const val FAN2_Y = 0.668f
private const val FAN2_R = 0.140f

/**
 * 背夹外观：优先使用 assets/cooler_body.webp 实拍底图，
 * 在其上叠加两枚可旋转、带 RGB 灯效的风扇；没有底图时退回自绘矢量图。
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
    val bitmap = remember {
        runCatching {
            context.assets.open("cooler_body.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    val angle by rememberFanAngle(mode, connected)
    val phase by rememberLightPhase()

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(IMAGE_RATIO)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "小米冰封散热背夹",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r1 = size.width * FAN1_R
                    val r2 = size.width * FAN2_R
                    drawFanMotor(
                        center = Offset(size.width * FAN1_X, size.height * FAN1_Y),
                        radius = r1,
                        angleDeg = angle,
                        lightMode = lightMode,
                        lightColor = lightColor,
                        phase = phase,
                    )
                    drawFanMotor(
                        center = Offset(size.width * FAN2_X, size.height * FAN2_Y),
                        radius = r2,
                        angleDeg = -angle,
                        lightMode = lightMode,
                        lightColor = lightColor,
                        phase = phase,
                    )
                }
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawVectorFallback(darkTheme)
                }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawFanMotor(
                        center = Offset(size.width * 0.30f, size.height * 0.5f),
                        radius = size.width * 0.19f,
                        angleDeg = angle,
                        lightMode = lightMode,
                        lightColor = lightColor,
                        phase = phase,
                    )
                    drawFanMotor(
                        center = Offset(size.width * 0.70f, size.height * 0.5f),
                        radius = size.width * 0.19f,
                        angleDeg = -angle,
                        lightMode = lightMode,
                        lightColor = lightColor,
                        phase = phase,
                    )
                }
            }
        }
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