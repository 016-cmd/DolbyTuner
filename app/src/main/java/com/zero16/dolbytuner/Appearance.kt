package com.zero16.dolbytuner

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 液态玻璃参数（自研，不依赖任何第三方玻璃库）。
 *
 * 设计原则：
 *  - 参数全部落在 SharedPreferences，Compose 里读 mutableStateOf 直接重组
 *  - 数值型尽量用 Int（0~100 / 角度 / 档位），减少枚举带来的兼容坑
 *  - 卡片、dock、设置卡共用同一套参数 → 视觉统一
 */
object Appearance {

    private const val P = "appearance"
    private fun p(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    // ── 总开关 ──
    val enabled = mutableStateOf(true)

    // ── 玻璃 ──
    /** 折射强度 0~100（边缘向内弯折的强度） */
    val refraction = mutableStateOf(60)
    /** 折射边缘宽度：0 窄 / 1 标准 / 2 宽 */
    val edgeWidth = mutableStateOf(1)
    /** 模糊 0~40 dp（内置壁纸预模糊用；卡片采样这张预模糊图） */
    val blurRadius = mutableStateOf(16)
    /** 提亮 0~100 */
    val vibrancy = mutableStateOf(55)

    // ── 颜色 ──
    /** 色散 0~100（0 = 关） */
    val dispersion = mutableStateOf(35)
    /** 光源角度 0~360 */
    val lightAngle = mutableStateOf(315)
    /** 体积光：0 关 / 1 弱 / 2 标准 / 3 强 */
    val volumeLight = mutableStateOf(2)
    /** 卡片底色：0 自动（跟随深浅色）/ 1 纯白 / 2 纯黑 */
    val tintMode = mutableStateOf(0)

    // ── 形状 ──
    /** 圆角 12~32 dp */
    val radius = mutableStateOf(22)
    /** 玻璃厚度：0 薄 / 1 标准 / 2 厚 */
    val thickness = mutableStateOf(1)

    // ── 动效 ──
    /** 果冻按压 0~100（0 = 关） */
    val jelly = mutableStateOf(70)
    /** 弹簧：0 软 / 1 标准 / 2 硬 */
    val spring = mutableStateOf(1)
    val crossLight = mutableStateOf(true)
    /** 性能：0 自动 / 1 高画质 / 2 省电 */
    val perfMode = mutableStateOf(0)

    // ── 页面 ──
    val animPage = mutableStateOf(true)
    val animCard = mutableStateOf(true)
    val animLaunch = mutableStateOf(true)

    // ── 壁纸 ──
    /** 壁纸来源：0 本地图 / 1 极光 / 2 晨雾 / 3 暖砂 */
    val wallpaperKind = mutableStateOf(0)
    /** 壁纸压暗 0~60 */
    val wallpaperDim = mutableStateOf(0)

    /** 壁纸取色结果：图标、滑条、选中胶囊用它 */
    val accent = mutableStateOf(Color(0xFF22C55E))

    // ── 存取 ──
    private fun putI(ctx: Context, key: String, st: MutableState<Int>, v: Int) {
        st.value = v
        p(ctx).edit().putInt(key, v).apply()
    }

    private fun putB(ctx: Context, key: String, st: MutableState<Boolean>, v: Boolean) {
        st.value = v
        p(ctx).edit().putBoolean(key, v).apply()
    }

    fun setEnabled(ctx: Context, v: Boolean) = putB(ctx, "enabled", enabled, v)
    fun setRefraction(ctx: Context, v: Int) = putI(ctx, "refraction", refraction, v)
    fun setEdgeWidth(ctx: Context, v: Int) = putI(ctx, "edge_width", edgeWidth, v)
    fun setBlurRadius(ctx: Context, v: Int) = putI(ctx, "blur_radius", blurRadius, v)
    fun setVibrancy(ctx: Context, v: Int) = putI(ctx, "vibrancy", vibrancy, v)
    fun setDispersion(ctx: Context, v: Int) = putI(ctx, "dispersion", dispersion, v)
    fun setLightAngle(ctx: Context, v: Int) = putI(ctx, "light_angle", lightAngle, v)
    fun setVolumeLight(ctx: Context, v: Int) = putI(ctx, "volume_light", volumeLight, v)
    fun setTintMode(ctx: Context, v: Int) = putI(ctx, "tint_mode", tintMode, v)
    fun setRadius(ctx: Context, v: Int) = putI(ctx, "radius", radius, v)
    fun setThickness(ctx: Context, v: Int) = putI(ctx, "thickness", thickness, v)
    fun setJelly(ctx: Context, v: Int) = putI(ctx, "jelly", jelly, v)
    fun setSpring(ctx: Context, v: Int) = putI(ctx, "spring", spring, v)
    fun setCrossLight(ctx: Context, v: Boolean) = putB(ctx, "cross_light", crossLight, v)
    fun setPerfMode(ctx: Context, v: Int) = putI(ctx, "perf_mode", perfMode, v)
    fun setAnimPage(ctx: Context, v: Boolean) = putB(ctx, "anim_page", animPage, v)
    fun setAnimCard(ctx: Context, v: Boolean) = putB(ctx, "anim_card", animCard, v)
    fun setAnimLaunch(ctx: Context, v: Boolean) = putB(ctx, "anim_launch", animLaunch, v)
    fun setWallpaperKind(ctx: Context, v: Int) = putI(ctx, "wp_kind", wallpaperKind, v)
    fun setWallpaperDim(ctx: Context, v: Int) = putI(ctx, "wp_dim", wallpaperDim, v)

    fun load(ctx: Context) {
        val s = p(ctx)
        enabled.value = s.getBoolean("enabled", true)
        refraction.value = s.getInt("refraction", 60).coerceIn(0, 100)
        edgeWidth.value = s.getInt("edge_width", 1).coerceIn(0, 2)
        blurRadius.value = s.getInt("blur_radius", 16).coerceIn(0, 40)
        vibrancy.value = s.getInt("vibrancy", 55).coerceIn(0, 100)
        dispersion.value = s.getInt("dispersion", 35).coerceIn(0, 100)
        lightAngle.value = s.getInt("light_angle", 315).coerceIn(0, 360)
        volumeLight.value = s.getInt("volume_light", 2).coerceIn(0, 3)
        tintMode.value = s.getInt("tint_mode", 0).coerceIn(0, 2)
        radius.value = s.getInt("radius", 22).coerceIn(12, 32)
        thickness.value = s.getInt("thickness", 1).coerceIn(0, 2)
        jelly.value = s.getInt("jelly", 70).coerceIn(0, 100)
        spring.value = s.getInt("spring", 1).coerceIn(0, 2)
        crossLight.value = s.getBoolean("cross_light", true)
        perfMode.value = s.getInt("perf_mode", 0).coerceIn(0, 2)
        animPage.value = s.getBoolean("anim_page", true)
        animCard.value = s.getBoolean("anim_card", true)
        animLaunch.value = s.getBoolean("anim_launch", true)
        wallpaperKind.value = s.getInt("wp_kind", 0).coerceIn(0, 3)
        wallpaperDim.value = s.getInt("wp_dim", 0).coerceIn(0, 60)
        val cached = s.getInt("accent_cache", Int.MIN_VALUE)
        if (cached != Int.MIN_VALUE) accent.value = Color(cached)
    }

    // ── 派生值 ──
    /** 描边宽度（dp 倍数） */
    fun strokeDp(): Float = when (edgeWidth.value) {
        0 -> 0.9f
        2 -> 2.4f
        else -> 1.5f
    }

    /** 卡片底色透明度：越透越像玻璃 */
    fun tintAlpha(isDark: Boolean): Float {
        val base = when (tintMode.value) {
            1 -> if (isDark) 0.34f else 0.62f      // 纯白
            2 -> if (isDark) 0.62f else 0.34f      // 纯黑
            else -> if (isDark) 0.30f else 0.42f   // 自动
        }
        val thick = when (thickness.value) { 0 -> 0.88f; 2 -> 1.12f; else -> 1f }
        return (base * thick).coerceIn(0.12f, 0.80f)
    }

    /** 普通 Card 用的玻璃填充色（透壁纸，不染色） */
    fun glassFill(isDark: Boolean): Color =
        tintColor(isDark).copy(alpha = tintAlpha(isDark).coerceAtLeast(0.18f))

    fun tintColor(isDark: Boolean): Color = when (tintMode.value) {
        1 -> Color.White
        2 -> Color.Black
        else -> if (isDark) Color.Black else Color.White
    }

    // ── 壁纸取色 ──
    /** 跳过太黑/太白/太灰的像素，按色相分桶，取权重最大的一桶 */
    fun computeAccent(src: Bitmap, dark: Boolean): Color {
        val small = runCatching { Bitmap.createScaledBitmap(src, 40, 40, true) }.getOrNull() ?: src
        val buckets = HashMap<Int, FloatArray>()
        val hsv = FloatArray(3)
        for (y in 0 until small.height step 2) {
            for (x in 0 until small.width step 2) {
                val px = small.getPixel(x, y)
                val r = android.graphics.Color.red(px)
                val g = android.graphics.Color.green(px)
                val b = android.graphics.Color.blue(px)
                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val s = hsv[1]
                val v = hsv[2]
                if (s < 0.18f || v < 0.16f || v > 0.94f) continue
                val weight = (s * 0.7f + 0.3f) * (1f - Math.abs(v - 0.55f))
                if (weight <= 0f) continue
                val key = (hsv[0] / 15f).toInt().coerceIn(0, 23)
                val cur = buckets.getOrPut(key) { FloatArray(4) }
                cur[0] += weight
                cur[1] += r * weight
                cur[2] += g * weight
                cur[3] += b * weight
            }
        }
        if (small !== src) runCatching { small.recycle() }
        val best = buckets.maxByOrNull { it.value[0] } ?: return Color(0xFF22C55E)
        val w = best.value[0].coerceAtLeast(0.0001f)
        val r = (best.value[1] / w).toInt().coerceIn(0, 255)
        val g = (best.value[2] / w).toInt().coerceIn(0, 255)
        val b = (best.value[3] / w).toInt().coerceIn(0, 255)
        val out = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, out)
        out[1] = 0.62f
        out[2] = if (dark) 0.82f else 0.62f
        return Color(android.graphics.Color.HSVToColor(out))
    }

    fun cacheAccent(ctx: Context, c: Color) {
        accent.value = c
        p(ctx).edit().putInt("accent_cache", c.toArgb()).apply()
    }

    // ── 动效派生 ──
    fun springDamping(): Float = when (spring.value) { 0 -> 0.72f; 2 -> 0.92f; else -> 0.82f }
    fun springStiffness(): Float = when (spring.value) { 0 -> 220f; 2 -> 480f; else -> 340f }
}