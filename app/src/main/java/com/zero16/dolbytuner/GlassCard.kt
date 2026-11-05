package com.zero16.dolbytuner

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** 切页计数：每次换页 +1，用来让卡片入场动画每次都重播 */
val LocalCardEpoch = androidx.compose.runtime.compositionLocalOf { 0 }

/**
 * 液态玻璃卡片（Backdrop 真折射）。
 *
 * 采样的是 Theme 里那唯一一层「壁纸层 backdrop」，
 * 不是每张卡各抓一次背景 —— 不会踩 RenderThread 递归崩溃。
 *
 * Backdrop 不可用（API<33 / 省电 / 用户关闭）时退回纯色玻璃，
 * 不再做旧的「按坐标裁壁纸」那一套。
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
 * 卡片入场：透明度 + 轻微放大（弹簧）。
 * LocalCardEpoch 变化时（切页）会重播。
 */
@Composable
fun Modifier.cardEnter(): Modifier {
    if (!Appearance.animCard.value) return this
    val anim = remember { Animatable(0f) }
    val epoch = LocalCardEpoch.current
    LaunchedEffect(epoch) {
        anim.snapTo(0f)
        anim.animateTo(
            1f,
            spring(dampingRatio = 0.78f, stiffness = 240f, visibilityThreshold = 0.001f)
        )
    }
    // 必须在 clip / glassSurface 之前，缩放才会连同玻璃一起变换，不会错位
    return this.graphicsLayer {
        val p = anim.value
        alpha = p.coerceIn(0f, 1f)
        val s = 0.965f + 0.035f * p
        scaleX = s
        scaleY = s
    }
}

/**
 * 果冻按压：只给按钮用（卡片不要用）。
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
                awaitFirstDown(requireUnconsumed = false)
                scope.launch { sx.animateTo(pressX, spec) }
                scope.launch { sy.animateTo(pressY, spec) }
                waitForUpOrCancellation()
                scope.launch { sx.animateTo(1f, spec) }
                scope.launch { sy.animateTo(1f, spec) }
            }
        }
}

/** 卡片玻璃表面：Backdrop 真折射优先，否则纯色回退 */
@Composable
fun Modifier.glassSurface(radius: Float, cardPos: Offset, cardSize: IntSize): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red * 0.299f + cs.background.green * 0.587f +
            cs.background.blue * 0.114f < 0.5f

    val on = Appearance.enabled.value
    val tintColor = Appearance.tintColor(isDark)
    val tintAlpha = Appearance.tintAlpha(isDark)
    val refr = Appearance.refraction.value / 100f
    val dispersion = Appearance.dispersion.value
    val angle = Appearance.lightAngle.value
    val vib = Appearance.vibrancy.value / 100f

    val bd = GlassBackdrop.instance
    val useBackdrop = on && bd != null &&
            Appearance.cardLiquid.value &&
            Appearance.perfMode.value != 2 &&
            android.os.Build.VERSION.SDK_INT >= 33

    val density = LocalDensity.current
    val rPx = with(density) { radius.dp.toPx() }
    val strokePx = with(density) { Appearance.strokeDp().dp.toPx() }

    if (useBackdrop) {
        val shape = RoundedCornerShape(radius.dp)
        // 液态玻璃的关键：模糊要小、折射要大、带色散，否则看起来就是毛玻璃
        val blurDp = (Appearance.blurRadius.value * 0.22f).coerceIn(0f, 5f)
        return this
            .drawBackdrop(
                backdrop = bd!!,
                shape = { shape },
                effects = {
                    if (blurDp > 0f) blur(blurDp.dp.toPx())
                    vibrancy()
                    if (refr > 0f) {
                        lens(
                            refractionHeight = (10f + 12f * refr).dp.toPx(),
                            refractionAmount = (18f + 30f * refr).dp.toPx(),
                            chromaticAberration = dispersion > 0
                        )
                    }
                },
                onDrawSurface = {
                    // 底色压得很薄，让折射与壁纸透出来
                    drawRoundRect(
                        color = tintColor.copy(alpha = tintAlpha * 0.62f),
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                }
            )
            .drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    val corner = CornerRadius(rPx, rPx)
                    // 顶部亮线（光源方向）
                    if (vib > 0f) {
                        val rad = Math.toRadians(angle.toDouble())
                        val dx = cos(rad).toFloat()
                        val dy = sin(rad).toFloat()
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0.05f + 0.18f * vib),
                                0.5f to Color.White.copy(alpha = 0.02f),
                                1f to Color.Transparent,
                                start = Offset(w / 2f - dx * w, h / 2f - dy * h),
                                end = Offset(w / 2f + dx * w, h / 2f + dy * h)
                            ),
                            cornerRadius = corner
                        )
                    }
                    // 外框
                    drawRoundRect(
                        color = if (isDark) Color.White.copy(alpha = 0.18f)
                        else Color.White.copy(alpha = 0.66f),
                        cornerRadius = corner,
                        style = Stroke(strokePx)
                    )
                    // 内侧一圈暗边，增加玻璃厚度
                    drawRoundRect(
                        color = Color.Black.copy(alpha = if (isDark) 0.10f else 0.04f),
                        topLeft = Offset(strokePx, strokePx),
                        size = androidx.compose.ui.geometry.Size(
                            w - strokePx * 2f,
                            h - strokePx * 2f
                        ),
                        cornerRadius = corner,
                        style = Stroke(strokePx * 0.8f)
                    )
                }
            }
    }

    // ── 回退：纯色玻璃（不做壁纸采样） ──
    return this.drawWithCache {
        onDrawBehind {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(rPx, rPx)
            drawRoundRect(
                color = tintColor.copy(alpha = if (on) tintAlpha else if (isDark) 0.88f else 0.94f),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.30f),
                    0.20f to Color.Transparent
                ),
                cornerRadius = corner
            )
            drawRoundRect(
                color = if (isDark) Color.White.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.55f),
                cornerRadius = corner,
                style = Stroke(strokePx)
            )
        }
    }
}

/**
 * dock 专用玻璃：不参与外观自定义，参数固定。
 * 与卡片的液体玻璃分开，避免用户把 dock 调花。
 */
@Composable
fun Modifier.dockSurface(): Modifier {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red * 0.299f + cs.background.green * 0.587f +
            cs.background.blue * 0.114f < 0.5f
    val density = LocalDensity.current
    val strokePx = with(density) { 1.dp.toPx() }

    return this.drawWithCache {
        onDrawBehind {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(h / 2f, h / 2f)
            // 固定的悬浮玻璃底
            drawRoundRect(
                color = if (isDark) Color.Black.copy(alpha = 0.30f)
                else Color.White.copy(alpha = 0.42f),
                cornerRadius = corner
            )
            // 顶部高光
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (isDark) 0.14f else 0.55f),
                    0.45f to Color.Transparent
                ),
                cornerRadius = corner
            )
            // 外框
            drawRoundRect(
                color = if (isDark) Color.White.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.60f),
                cornerRadius = corner,
                style = Stroke(strokePx)
            )
        }
    }
}