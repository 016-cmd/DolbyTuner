package com.zero16.dolbytuner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 玻璃卡片。
 *
 * 模糊是「局部的」：从预先模糊好的壁纸里，裁出卡片所在的那一块画进卡片，
 * 壁纸其他地方保持清晰。不抓实时背景 → 不会有 RenderThread 的 SIGSEGV。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned {
                pos = it.positionInRoot()
                sz = it.size
            }
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat(), pos, sz)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) { content() }
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    content: @Composable BoxScope.() -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .onGloballyPositioned {
                pos = it.positionInRoot()
                sz = it.size
            }
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat(), pos, sz)
    ) { content() }
}

@Composable
fun GlassSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var sz by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                pos = it.positionInRoot()
                sz = it.size
            }
            .clip(RoundedCornerShape(16.dp))
            .glassSurface(16f, pos, sz)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * 玻璃表面绘制：
 * 1) 毛玻璃：把壁纸的模糊版按卡片位置裁一块铺底 → 只有卡片区域是糊的
 * 2) 液态玻璃：不铺模糊图，只铺很浅的固定色 + 边缘折射/色散/纵深
 */
@Composable
fun Modifier.glassSurface(radius: Float, cardPos: Offset, cardSize: IntSize): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red * 0.299f + cs.background.green * 0.587f +
            cs.background.blue * 0.114f < 0.5f

    val on = Appearance.enabled.value
    val mode = Appearance.mode.value
    val blurLv = Appearance.blur.value
    val contrast = Appearance.contrast.value.value
    val sat = Appearance.saturation.value.value
    val disp = Appearance.dispersion.value.on
    val depthOn = Appearance.depth.value.on
    val refract = Appearance.refract.value.factor

    // 壁纸模糊版（非 Compose state，但 Theme 会在壁纸/模糊档变化时整体重组）
    val blurBmp = WallpaperState.blurred

    val frosted = mode == GlassMode.FROSTED && on
    val base = if (isDark) 0.24f else 0.42f
    // 铺了模糊底图之后，上面的固定色可以更薄一点
    val fillAlpha = when {
        !on -> if (isDark) 0.32f else 0.50f
        frosted -> base * 0.62f * (contrast / 1.1f)
        else -> base * 0.40f * (contrast / 1.1f)
    }
    val fill = (if (isDark) Color.Black else Color.White).copy(alpha = fillAlpha)

    val warmA = if (sat > 1.1f) (sat - 1.1f) * 0.10f else 0f
    val coolA = if (sat < 1.1f) (1.1f - sat) * 0.10f else 0f

    val density = LocalDensity.current
    val strokeW = with(density) { 1.dp.toPx() }
    val rPx = with(density) { radius.dp.toPx() }
    val liquid = mode == GlassMode.LIQUID && on

    return this.drawWithCache {
        onDrawBehind {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(rPx, rPx)

            // 1) 卡片区域内的模糊壁纸
            if (frosted && blurBmp != null && cardSize.width > 0 && cardSize.height > 0) {
                val iw = blurBmp.width.toFloat()
                val ih = blurBmp.height.toFloat()
                val sw = WallpaperState.screenW.toFloat()
                val sh = WallpaperState.screenH.toFloat()
                if (iw > 0f && ih > 0f && sw > 0f && sh > 0f) {
                    val scale = maxOf(sw / iw, sh / ih)   // ContentScale.Crop
                    val dispW = iw * scale
                    val dispH = ih * scale
                    val offX = (dispW - sw) / 2f
                    val offY = (dispH - sh) / 2f
                    val sx = ((offX + cardPos.x) / scale).toInt()
                    val sy = ((offY + cardPos.y) / scale).toInt()
                    val sW = (w / scale).toInt().coerceAtLeast(1)
                    val sH = (h / scale).toInt().coerceAtLeast(1)
                    val cx = sx.coerceIn(0, (iw.toInt() - 1).coerceAtLeast(0))
                    val cy = sy.coerceIn(0, (ih.toInt() - 1).coerceAtLeast(0))
                    val cw = sW.coerceAtMost((iw.toInt() - cx).coerceAtLeast(1))
                    val ch = sH.coerceAtMost((ih.toInt() - cy).coerceAtLeast(1))
                    drawImage(
                        image = blurBmp.asImageBitmap(),
                        srcOffset = IntOffset(cx, cy),
                        srcSize = IntSize(cw, ch),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium
                    )
                }
            }

            // 2) 固定底色
            drawRoundRect(color = fill, cornerRadius = corner)

            // 3) 顶部高光
            val hl = if (liquid) 0.26f else 0.16f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0f else hl),
                    0.45f to Color.Transparent,
                    endY = h * 0.45f
                ),
                cornerRadius = corner
            )

            if (liquid) {
                if (refract > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.30f * (0.6f + refract * 0.4f)),
                            0.5f to Color.White.copy(alpha = 0.10f),
                            1f to Color.White.copy(alpha = 0.04f)
                        ),
                        cornerRadius = corner,
                        style = Stroke(strokeW * 1.3f)
                    )
                }
                if (disp) {
                    drawRoundRect(
                        color = Color(0x33FF6B6B),
                        topLeft = Offset(-strokeW * 0.8f, 0f),
                        size = Size(w + strokeW * 1.6f, h),
                        cornerRadius = corner,
                        style = Stroke(strokeW)
                    )
                    drawRoundRect(
                        color = Color(0x335CA8FF),
                        topLeft = Offset(strokeW * 0.8f, 0f),
                        size = Size(w, h),
                        cornerRadius = corner,
                        style = Stroke(strokeW)
                    )
                }
                if (depthOn) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = if (isDark) 0.28f else 0.10f)
                        ),
                        cornerRadius = corner
                    )
                }
            } else {
                drawRoundRect(
                    color = if (isDark) Color.White.copy(alpha = 0.10f)
                    else Color.Black.copy(alpha = 0.08f),
                    cornerRadius = corner,
                    style = Stroke(strokeW)
                )
            }

            if (warmA > 0f) {
                drawRoundRect(color = Color(0xFFFFD9C2).copy(alpha = warmA), cornerRadius = corner)
            }
            if (coolA > 0f) {
                drawRoundRect(color = Color(0xFFC2E4FF).copy(alpha = coolA), cornerRadius = corner)
            }

            if (blurLv.noise >= 15f && on) {
                val step = 7f
                var yy = 0f
                while (yy < h) {
                    var xx = 0f
                    while (xx < w) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.02f),
                            topLeft = Offset(xx, yy),
                            size = Size(1.5f, 1.5f)
                        )
                        xx += step
                    }
                    yy += step
                }
            }
        }
    }
}