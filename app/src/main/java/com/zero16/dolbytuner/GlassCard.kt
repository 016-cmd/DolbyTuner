package com.zero16.dolbytuner


import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.drawBackdrop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** 切页计数：每次换页 +1，用来让卡片入场动画每次都重播 */
val LocalCardEpoch = androidx.compose.runtime.compositionLocalOf { 0 }


/**
 * 液态玻璃卡片（自研绘制，不抓实时背景 → 无 RenderThread 崩溃风险）。
 *
 * 视觉分层：
 *   1. 壁纸抽样     预模糊壁纸按卡片位置裁一块 → 玻璃「透得过去」
 *   2. 底色         自动 / 纯白 / 纯黑，透明度由「玻璃厚度」控制
 *   3. 提亮         vibrancy
 *   4. 折射内缘     边缘向内的一道亮带，强度 = 折射强度
 *   5. 色散         红/蓝两条微偏移描边
 *   6. 体积光       贴轮廓的多层柔光
 *   7. 厚度         底部内阴影 + 顶部亮线
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Int = Appearance.radius.value,
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
            .cardEnter()
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
    radius: Int = Appearance.radius.value,
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
            .cardEnter()
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat(), pos, sz)
    ) { content() }
}

@Composable
fun GlassSettingsCard(
    modifier: Modifier = Modifier,
    radius: Int = Appearance.radius.value,
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
            .cardEnter()
            .clip(RoundedCornerShape(radius.dp))
            .glassSurface(radius.toFloat(), pos, sz)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            content()
        }
    }
}

/**
 * 果冻按压：按下时轻微压扁、松开回弹。
 * 力度由 Appearance.jelly（0~100）控制，0 = 关闭。
 */
@Composable
fun Modifier.jellyPress(enabled: Boolean): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val amt = (Appearance.jelly.value / 100f).coerceIn(0f, 1f)
    val pressX = 1f + 0.05f * amt
    val pressY = 1f - 0.07f * amt
    val spec = spring<Float>(
        dampingRatio = Appearance.springDamping(),
        stiffness = Appearance.springStiffness(),
        visibilityThreshold = 0.001f
    )
    return this
        .graphicsLayer {
            scaleX = sx.value
            scaleY = sy.value
        }
        .pointerInput(enabled, amt) {
            awaitEachGesture {
                // requireUnconsumed = false：即使外层 clickable 先吃掉 down，也能触发果冻
                awaitFirstDown(requireUnconsumed = false)
                scope.launch { sx.animateTo(pressX, spec) }
                scope.launch { sy.animateTo(pressY, spec) }
                waitForUpOrCancellation()
                scope.launch { sx.animateTo(1f, spec) }
                scope.launch { sy.animateTo(1f, spec) }
            }
        }
}

/**
 * 卡片入场：透明度 + 轻微上移 + 轻微放大。
 * 只播一次（remember），开关是 Appearance.animCard。
 */
@Composable
fun Modifier.cardEnter(): Modifier {
    if (!Appearance.animCard.value) return this
    val anim = remember { Animatable(0f) }
    // 用切页计数当 key：每次切到这个页面都会重新播放，不再「时有时无」
    val epoch = LocalCardEpoch.current
    LaunchedEffect(epoch) {
        anim.snapTo(0f)
        anim.animateTo(
            1f,
            spring(dampingRatio = 0.78f, stiffness = 240f, visibilityThreshold = 0.001f)
        )
    }
    // 注意：这个 modifier 必须放在 clip/glassSurface 之前，
    // 这样缩放会连同「壁纸采样」一起变换，否则卡片会与背景错位
    return this.graphicsLayer {
        val p = anim.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.965f + 0.035f * p
        scaleX = s
        scaleY = s
    }
}

/** 玻璃表面绘制 */
@Composable
fun Modifier.glassSurface(radius: Float, cardPos: Offset, cardSize: IntSize): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red * 0.299f + cs.background.green * 0.587f +
            cs.background.blue * 0.114f < 0.5f

    val on = Appearance.enabled.value
    val tintColor = Appearance.tintColor(isDark)
    val tintAlpha = Appearance.tintAlpha(isDark)
    val vib = Appearance.vibrancy.value / 100f
    val refr = Appearance.refraction.value / 100f
    val disp = Appearance.dispersion.value / 100f
    val vol = Appearance.volumeLight.value
    val thick = Appearance.thickness.value
    val angle = Appearance.lightAngle.value
    val strokeDp = Appearance.strokeDp()

    val blurBmp = WallpaperState.blurred
    val density = LocalDensity.current
    val strokePx = with(density) { strokeDp.dp.toPx() }
    val rPx = with(density) { radius.dp.toPx() }

    // ── 真折射：Backdrop 采样「壁纸层」──────────────────────────
    val bd = GlassBackdrop.instance
    val useBackdrop = on && bd != null &&
            Appearance.cardLiquid.value &&
            Appearance.perfMode.value != 2 &&
            android.os.Build.VERSION.SDK_INT >= 33

    if (useBackdrop) {
        val shape = RoundedCornerShape(radius.dp)
        return this
            .drawBackdrop(
                backdrop = bd!!,
                shape = { shape },
                effects = {
                    val blurPx = Appearance.blurRadius.value.dp.toPx()
                    if (blurPx > 0f) blur(blurPx * 0.55f)
                    vibrancy()
                    val refr = Appearance.refraction.value / 100f
                    if (refr > 0f) {
                        lens(
                            (4f + 8f * refr).dp.toPx(),
                            (6f + 14f * refr).dp.toPx()
                        )
                    }
                },
                onDrawSurface = {
                    drawRoundRect(
                        color = tintColor.copy(alpha = tintAlpha),
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                }
            )
            .drawWithCache {
                onDrawBehind {
                    val w2 = size.width
                    val h2 = size.height
                    val corner2 = CornerRadius(rPx, rPx)
                    // 顶部亮线 + 外框（折射本身交给 Backdrop）
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.42f),
                            0.18f to Color.Transparent
                        ),
                        cornerRadius = corner2
                    )
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.13f)
                        else Color.White.copy(alpha = 0.55f),
                        cornerRadius = corner2,
                        style = Stroke(strokePx)
                    )
                }
            }
    }

    return this.drawWithCache {
        onDrawBehind {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(rPx, rPx)

            // 1) 壁纸抽样（只有卡片这块是糊的，壁纸本体保持清晰）
            if (on && blurBmp != null && cardSize.width > 0 && cardSize.height > 0) {
                drawWallpaperSlice(blurBmp, cardPos, w, h)
            }

            // 2) 底色
            val baseAlpha = if (on) tintAlpha else if (isDark) 0.90f else 0.96f
            drawRoundRect(color = tintColor.copy(alpha = baseAlpha), cornerRadius = corner)

            if (!on) {
                drawRoundRect(
                    color = if (isDark) Color.White.copy(alpha = 0.10f)
                    else Color.Black.copy(alpha = 0.08f),
                    cornerRadius = corner,
                    style = Stroke(strokePx)
                )
                return@onDrawBehind
            }

            // 3) 提亮：从光源方向打一道柔光
            if (vib > 0f) {
                val rad = Math.toRadians(angle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.04f + 0.14f * vib),
                        0.45f to Color.White.copy(alpha = 0.02f + 0.05f * vib),
                        1f to Color.Transparent,
                        start = Offset(w / 2f - dx * w, h / 2f - dy * h),
                        end = Offset(w / 2f + dx * w, h / 2f + dy * h)
                    ),
                    cornerRadius = corner
                )
            }

            // 4) 折射内缘：边缘向内的一道亮带
            if (refr > 0f) {
                val bandAlpha = 0.06f + 0.30f * refr
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = bandAlpha),
                        0.35f to Color.White.copy(alpha = bandAlpha * 0.35f),
                        0.7f to Color.Transparent,
                        1f to Color.White.copy(alpha = bandAlpha * 0.5f)
                    ),
                    cornerRadius = corner,
                    style = Stroke(width = (strokePx * 2.2f) + 3f * refr)
                )
            }

            // 5) 色散：红蓝两条微偏移描边
            if (disp > 0f) {
                val d = (0.6f + 2.2f * disp)
                drawRoundRect(
                    color = Color(0xFFFF5A5A).copy(alpha = 0.05f + 0.22f * disp),
                    topLeft = Offset(-d, 0f),
                    size = Size(w + d * 2f, h),
                    cornerRadius = corner,
                    style = Stroke(strokePx)
                )
                drawRoundRect(
                    color = Color(0xFF4DA3FF).copy(alpha = 0.05f + 0.22f * disp),
                    topLeft = Offset(d, 0f),
                    size = Size(w - d * 2f, h),
                    cornerRadius = corner,
                    style = Stroke(strokePx)
                )
            }

            // 6) 体积光：贴轮廓的多层柔光（向内，避免被 clip 裁掉）
            if (vol > 0) {
                val layers = 3
                for (i in layers downTo 1) {
                    val a = when (vol) {
                        1 -> 0.045f
                        3 -> 0.10f
                        else -> 0.07f
                    } * i / layers
                    drawRoundRect(
                        color = Color.White.copy(alpha = a),
                        cornerRadius = corner,
                        style = Stroke(width = strokePx + i * 4f * (vol * 0.5f + 0.5f))
                    )
                }
            }

            // 7) 厚度：底部内阴影 + 顶部亮线
            if (thick > 0) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (isDark) 0.22f else 0.09f)
                    ),
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (isDark) 0.16f else 0.42f),
                        0.18f to Color.Transparent
                    ),
                    cornerRadius = corner
                )
            }

            // 8) 外框
            drawRoundRect(
                color = if (isDark) Color.White.copy(alpha = 0.13f)
                else Color.White.copy(alpha = 0.55f),
                cornerRadius = corner,
                style = Stroke(strokePx)
            )
        }
    }
}

/** 从预模糊壁纸里裁出卡片所在区域（ContentScale.Crop 换算）。 */
private fun DrawScope.drawWallpaperSlice(
    bmp: android.graphics.Bitmap,
    cardPos: Offset,
    w: Float,
    h: Float
) {
    val iw = bmp.width.toFloat()
    val ih = bmp.height.toFloat()
    val sw = WallpaperState.screenW.toFloat()
    val sh = WallpaperState.screenH.toFloat()
    if (iw <= 0f || ih <= 0f || sw <= 0f || sh <= 0f) return
    val scale = maxOf(sw / iw, sh / ih)
    val offX = (iw * scale - sw) / 2f
    val offY = (ih * scale - sh) / 2f
    val sx = ((offX + cardPos.x) / scale).toInt()
    val sy = ((offY + cardPos.y) / scale).toInt()
    val sW = (w / scale).toInt().coerceAtLeast(1)
    val sH = (h / scale).toInt().coerceAtLeast(1)
    val cx = sx.coerceIn(0, (iw.toInt() - 1).coerceAtLeast(0))
    val cy = sy.coerceIn(0, (ih.toInt() - 1).coerceAtLeast(0))
    val cw = sW.coerceAtMost((iw.toInt() - cx).coerceAtLeast(1))
    val ch = sH.coerceAtMost((ih.toInt() - cy).coerceAtLeast(1))
    drawImage(
        image = bmp.asImageBitmap(),
        srcOffset = IntOffset(cx, cy),
        srcSize = IntSize(cw, ch),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium
    )
}