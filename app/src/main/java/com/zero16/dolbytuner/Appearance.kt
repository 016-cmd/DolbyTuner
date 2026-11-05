package com.zero16.dolbytuner

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 外观参数（档位数值取自 Flowmix 的真实参数，表现自己调）
 */
enum class GlassMode(val label: String) {
    FROSTED("毛玻璃"),
    LIQUID("液态玻璃")
}

/** 玻璃模糊三档：blur 主模糊 / edge 边缘光 / depth 纵深 / noise 颗粒 */
enum class GlassBlur(
    val label: String,
    val blur: Float,
    val edge: Float,
    val depth: Float,
    val noise: Float
) {
    TRANSPARENT("通透", 5f, 0f, 3f, 3f),
    DEFAULT("默认", 25f, 5f, 10f, 7f),
    HAZY("朦胧", 50f, 15f, 25f, 20f)
}

/** 边缘折射 / 可读性增强用的三档 */
enum class Level3(val label: String, val factor: Float) {
    OFF("关闭", 0f),
    DEFAULT("默认", 1f),
    ENHANCED("增强", 2f)
}

/** 对比度 / 饱和度三档（它的「默认」是 1.1，不是 1.0） */
enum class LevelTune(val label: String, val value: Float) {
    LOW("较低", 0.9f),
    DEFAULT("默认", 1.1f),
    HIGH("较高", 1.3f)
}

enum class BoolLevel(val label: String, val on: Boolean) {
    OFF("关闭", false),
    ON("开启", true)
}

enum class Readability(val label: String, val amount: Float) {
    OFF("关闭", 0f),
    DEFAULT("默认", 0.5f),
    ENHANCED("增强", 0.8f)
}

object Appearance {

    private const val P = "appearance"
    private const val K_ENABLED = "enabled"
    private const val K_MODE = "glass_mode"
    private const val K_BLUR = "glass_blur"
    private const val K_REFRACT = "refract"
    private const val K_DISPERSION = "dispersion"
    private const val K_DEPTH = "depth"
    private const val K_READ = "readability"
    private const val K_CONTRAST = "contrast"
    private const val K_SAT = "saturation"
    private const val K_ANIM_PAGE = "anim_page"
    private const val K_ANIM_CARD = "anim_card"
    private const val K_ANIM_LAUNCH = "anim_launch"
    private const val K_ACCENT = "accent_cache"

    private fun p(ctx: Context) = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)

    // ── 全局可观察状态（Compose 直接读，改了立刻重组） ──
    val enabled = mutableStateOf(true)
    val mode = mutableStateOf(GlassMode.FROSTED)
    val blur = mutableStateOf(GlassBlur.DEFAULT)
    val refract = mutableStateOf(Level3.DEFAULT)
    val dispersion = mutableStateOf(BoolLevel.OFF)
    val depth = mutableStateOf(BoolLevel.ON)
    val readability = mutableStateOf(Readability.OFF)
    val contrast = mutableStateOf(LevelTune.DEFAULT)
    val saturation = mutableStateOf(LevelTune.DEFAULT)
    val animPage = mutableStateOf(true)
    val animCard = mutableStateOf(true)
    val animLaunch = mutableStateOf(true)

    /** 壁纸取色结果：卡片图标、拉条用它 */
    val accent = mutableStateOf(Color(0xFF22C55E))

    fun load(ctx: Context) {
        val s = p(ctx)
        enabled.value = s.getBoolean(K_ENABLED, true)
        mode.value = runCatching { GlassMode.valueOf(s.getString(K_MODE, GlassMode.FROSTED.name)!!) }
            .getOrDefault(GlassMode.FROSTED)
        blur.value = runCatching { GlassBlur.valueOf(s.getString(K_BLUR, GlassBlur.DEFAULT.name)!!) }
            .getOrDefault(GlassBlur.DEFAULT)
        refract.value = runCatching { Level3.valueOf(s.getString(K_REFRACT, Level3.DEFAULT.name)!!) }
            .getOrDefault(Level3.DEFAULT)
        dispersion.value = runCatching { BoolLevel.valueOf(s.getString(K_DISPERSION, BoolLevel.OFF.name)!!) }
            .getOrDefault(BoolLevel.OFF)
        depth.value = runCatching { BoolLevel.valueOf(s.getString(K_DEPTH, BoolLevel.ON.name)!!) }
            .getOrDefault(BoolLevel.ON)
        readability.value = runCatching { Readability.valueOf(s.getString(K_READ, Readability.OFF.name)!!) }
            .getOrDefault(Readability.OFF)
        contrast.value = runCatching { LevelTune.valueOf(s.getString(K_CONTRAST, LevelTune.DEFAULT.name)!!) }
            .getOrDefault(LevelTune.DEFAULT)
        saturation.value = runCatching { LevelTune.valueOf(s.getString(K_SAT, LevelTune.DEFAULT.name)!!) }
            .getOrDefault(LevelTune.DEFAULT)
        animPage.value = s.getBoolean(K_ANIM_PAGE, true)
        animCard.value = s.getBoolean(K_ANIM_CARD, true)
        animLaunch.value = s.getBoolean(K_ANIM_LAUNCH, true)
        val cached = s.getInt(K_ACCENT, Int.MIN_VALUE)
        if (cached != Int.MIN_VALUE) accent.value = Color(cached)
    }

    fun setEnabled(ctx: Context, v: Boolean) {
        enabled.value = v
        p(ctx).edit().putBoolean(K_ENABLED, v).apply()
    }

    fun setMode(ctx: Context, v: GlassMode) {
        mode.value = v
        p(ctx).edit().putString(K_MODE, v.name).apply()
    }

    fun setBlur(ctx: Context, v: GlassBlur) {
        blur.value = v
        p(ctx).edit().putString(K_BLUR, v.name).apply()
    }

    fun setRefract(ctx: Context, v: Level3) {
        refract.value = v
        p(ctx).edit().putString(K_REFRACT, v.name).apply()
    }

    fun setDispersion(ctx: Context, v: BoolLevel) {
        dispersion.value = v
        p(ctx).edit().putString(K_DISPERSION, v.name).apply()
    }

    fun setDepth(ctx: Context, v: BoolLevel) {
        depth.value = v
        p(ctx).edit().putString(K_DEPTH, v.name).apply()
    }

    fun setReadability(ctx: Context, v: Readability) {
        readability.value = v
        p(ctx).edit().putString(K_READ, v.name).apply()
    }

    fun setContrast(ctx: Context, v: LevelTune) {
        contrast.value = v
        p(ctx).edit().putString(K_CONTRAST, v.name).apply()
    }

    fun setSaturation(ctx: Context, v: LevelTune) {
        saturation.value = v
        p(ctx).edit().putString(K_SAT, v.name).apply()
    }

    fun setAnimPage(ctx: Context, v: Boolean) {
        animPage.value = v
        p(ctx).edit().putBoolean(K_ANIM_PAGE, v).apply()
    }

    fun setAnimCard(ctx: Context, v: Boolean) {
        animCard.value = v
        p(ctx).edit().putBoolean(K_ANIM_CARD, v).apply()
    }

    fun setAnimLaunch(ctx: Context, v: Boolean) {
        animLaunch.value = v
        p(ctx).edit().putBoolean(K_ANIM_LAUNCH, v).apply()
    }

    // ── 壁纸取色 ──
    /**
     * 从壁纸里取一个「能当强调色用」的颜色：
     * 跳过太黑/太白/太灰的像素，按色相分桶统计，取权重最大的一桶。
     */
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
                // 中等亮度 + 高饱和的像素权重更高，避免取到一大片浅色天空
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

        // 统一拉到「当强调色好看」的区间：足够饱和、亮度适中
        val out = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, out)
        out[1] = 0.62f
        out[2] = if (dark) 0.82f else 0.62f
        return Color(android.graphics.Color.HSVToColor(out))
    }

    fun cacheAccent(ctx: Context, c: Color) {
        accent.value = c
        p(ctx).edit().putInt(K_ACCENT, c.toArgb()).apply()
    }
}