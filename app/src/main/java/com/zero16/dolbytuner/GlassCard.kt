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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 玻璃卡片。
 * 底色固定（不随壁纸变），只有图标 / 拉条 / 选中态走壁纸取色。
 * 不做 backdrop 抓取 —— 模糊来自整屏壁纸层，避开 RenderThread 的 SIGSEGV。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat())
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat())
    ) { content() }
}

@Composable
fun GlassSettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glassSurface(16f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * 玻璃表面：固定底色 + 顶部高光 + 描边；
 * 液态模式再加边缘折射 / 色散 / 纵深。全是纯绘制 → 不会 native crash。
 */
@Composable
fun Modifier.glassSurface(radius: Float): Modifier {
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

    val base = if (isDark) 0.24f else 0.42f
    val fillAlpha = if (on) base * (contrast / 1.1f) else (if (isDark) 0.32f else 0.50f)
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

            // 1) 底色
            drawRoundRect(color = fill, cornerRadius = corner)

            // 2) 顶部高光
            val hl = if (mode == GlassMode.LIQUID) 0.26f else 0.16f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0f else hl),
                    0.45f to Color.Transparent,
                    endY = h * 0.45f
                ),
                cornerRadius = corner
            )

            if (liquid) {
                // 3) 边缘折射
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
                // 4) 边缘色散
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
                // 5) 纵深
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
                // 毛玻璃：细内描边
                drawRoundRect(
                    color = if (isDark) Color.White.copy(alpha = 0.10f)
                    else Color.Black.copy(alpha = 0.08f),
                    cornerRadius = corner,
                    style = Stroke(strokeW)
                )
            }

            // 冷暖微调
            if (warmA > 0f) {
                drawRoundRect(color = Color(0xFFFFD9C2).copy(alpha = warmA), cornerRadius = corner)
            }
            if (coolA > 0f) {
                drawRoundRect(color = Color(0xFFC2E4FF).copy(alpha = coolA), cornerRadius = corner)
            }

            // 朦胧档：细颗粒
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