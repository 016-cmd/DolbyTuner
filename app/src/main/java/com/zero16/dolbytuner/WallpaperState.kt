package com.zero16.dolbytuner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * 全局壁纸状态（新系统）。
 *
 * 两种来源：
 *  - 本地图：用户从相册/文件选的图，复制到 filesDir 后加载
 *  - 内置氛围图：代码生成（极光 / 晨雾 / 暖砂），不联网、不依赖任何第三方接口
 *
 * 同时缓存一份「预模糊版」：卡片玻璃直接采样这张图，不需要每帧抓实时背景。
 */
object WallpaperState {
    @Volatile var original: Bitmap? = null
        private set

    @Volatile var blurred: Bitmap? = null
        private set

    @Volatile var isDark: Boolean = false
        private set

    /** 当前窗口尺寸（px） */
    @Volatile var screenW: Int = 1080
        private set

    @Volatile var screenH: Int = 2400
        private set

    /** 当前壁纸类型（0 本地 / 1~3 内置），用于换主题时判断要不要重建 */
    @Volatile var kind: Int = 0
        private set

    /** 壁纸平均亮度 0~1（用于文字自动黑白） */
    @Volatile var lum: Float = 0.5f
        private set

    fun setLocal(
        ctx: Context,
        bitmap: Bitmap,
        dark: Boolean,
        screenWidthPx: Int,
        screenHeightPx: Int,
        blurRadius: Float
    ): Bitmap? = apply(
        ctx, bitmap, kind = 0, dark = dark,
        w = screenWidthPx, h = screenHeightPx, blur = blurRadius
    )

    /** 内置壁纸：代码生成，不联网 */
    fun setBuiltin(
        ctx: Context,
        kind: Int,
        dark: Boolean,
        screenWidthPx: Int,
        screenHeightPx: Int,
        blurRadius: Float
    ): Bitmap? {
        // 生成 720 宽即可——反正卡片玻璃是模糊采样，屏幕尺寸的锐度没意义
        val w = 720
        val h = (720f * screenHeightPx / screenWidthPx.coerceAtLeast(1)).toInt().coerceIn(720, 2400)
        val bmp = generate(ctx, kind, w, h)
        return apply(ctx, bmp, kind, dark, screenWidthPx, screenHeightPx, blurRadius)
    }

    private fun apply(
        ctx: Context,
        bitmap: Bitmap,
        kind: Int,
        dark: Boolean,
        w: Int,
        h: Int,
        blur: Float
    ): Bitmap {
        screenW = w
        screenH = h
        this.kind = kind
        val oldOriginal = original
        val oldBlurred = blurred
        // 先放新图，再回收旧图 —— 避免 Compose 读到已回收 Bitmap
        original = bitmap
        blurred = BlurUtils.blur(ctx, bitmap, radius = blur)
        isDark = dark
        lum = runCatching { averageLum(bitmap) }.getOrDefault(0.5f)
        // 旧图不能立刻回收：Compose 可能还在绘制它，启动时可能有另一条取色协程
        // 正持有它做 getPixel（曾在冷启动偶发 "Can't call getPixel() on a
        // recycled bitmap" 闪退）。统一延后到主线程再回收。
        recycleLater(oldOriginal, oldBlurred)
        return bitmap
    }

    /** 延迟回收旧壁纸，避开绘制/取色的竞态 */
    private fun recycleLater(vararg bmps: Bitmap?) {
        val list = bmps.filterNotNull()
        if (list.isEmpty()) return
        Handler(Looper.getMainLooper()).postDelayed({
            list.forEach { b -> runCatching { if (!b.isRecycled) b.recycle() } }
        }, 3000L)
    }

    fun clear() {
        val o = original
        val b = blurred
        original = null
        blurred = null
        recycleLater(o, b)
    }

    private fun averageLum(b: Bitmap): Float {
        val s = runCatching { Bitmap.createScaledBitmap(b, 24, 24, true) }.getOrNull() ?: b
        var sum = 0f
        var n = 0
        for (y in 0 until s.height) for (x in 0 until s.width) {
            val px = s.getPixel(x, y)
            sum += (android.graphics.Color.red(px) * 0.299f +
                    android.graphics.Color.green(px) * 0.587f +
                    android.graphics.Color.blue(px) * 0.114f) / 255f
            n++
        }
        if (s !== b) runCatching { s.recycle() }
        return if (n > 0) sum / n else 0.5f
    }

    // ── 内置壁纸生成 ──
    /** 内置壁纸对应的 assets 文件 */
    private fun builtinAsset(kind: Int): String? = when (kind) {
        1 -> "wp_zzz.jpg"
        2 -> "wp_sea.jpg"
        3 -> "wp_buzz.jpg"
        else -> null
    }

    private fun generate(ctx: Context, kind: Int, w: Int, h: Int): Bitmap {
        // 优先用 assets 里的图片（绝区零 / 海风 / 微醺）
        val name = builtinAsset(kind)
        if (name != null) {
            val raw = runCatching {
                ctx.assets.open(name).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (raw != null) {
                val scale = maxOf(w.toFloat() / raw.width, h.toFloat() / raw.height)
                val nw = (raw.width * scale).toInt().coerceAtLeast(w)
                val nh = (raw.height * scale).toInt().coerceAtLeast(h)
                val scaled = Bitmap.createScaledBitmap(raw, nw, nh, true)
                if (scaled !== raw) raw.recycle()
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val cc = Canvas(out)
                cc.drawBitmap(
                    scaled, ((w - nw) / 2f), ((h - nh) / 2f),
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
                scaled.recycle()
                return out
            }
        }
        // 兜底：旧的渐变生成
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            // 极光：深蓝底 + 青 / 紫 / 蓝三团光
            1 -> {
                c.drawColor(0xFF070B1E.toInt())
                paint.shader = RadialGradient(
                    w * 0.18f, h * 0.16f, w * 0.85f,
                    intArrayOf(0xCC1B6BFF.toInt(), 0x331B6BFF, 0x001B6BFF),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                    w * 0.86f, h * 0.34f, w * 0.80f,
                    intArrayOf(0xCC8B3DFF.toInt(), 0x338B3DFF, 0x008B3DFF),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                    w * 0.50f, h * 0.88f, w * 0.95f,
                    intArrayOf(0xAA00E0C6.toInt(), 0x2200E0C6, 0x0000E0C6),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            // 晨雾：浅灰蓝渐变 + 一点白光
            2 -> {
                paint.shader = LinearGradient(
                    0f, 0f, w * 0.35f, h.toFloat(),
                    intArrayOf(0xFFF7FAFF.toInt(), 0xFFDCE6F2.toInt(), 0xFFB9C9DC.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                    w * 0.72f, h * 0.18f, w * 0.75f,
                    intArrayOf(0x99FFFFFF.toInt(), 0x22FFFFFF, 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            // 暖砂：米色 → 焦糖 + 橙光
            else -> {
                paint.shader = LinearGradient(
                    0f, 0f, w * 0.4f, h.toFloat(),
                    intArrayOf(0xFFFAF1E4.toInt(), 0xFFE8CFB2.toInt(), 0xFFC79A72.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.shader = RadialGradient(
                    w * 0.80f, h * 0.75f, w * 0.85f,
                    intArrayOf(0x88FF9E4D.toInt(), 0x22FF9E4D, 0x00FF9E4D),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
        }
        paint.shader = null
        return bmp
    }

    /** 内置壁纸名字，给设置页用 */
    fun builtinName(k: Int): String = when (k) {
        1 -> "绝区零"
        2 -> "海风"
        3 -> "微醺"
        else -> "本地图片"
    }
}