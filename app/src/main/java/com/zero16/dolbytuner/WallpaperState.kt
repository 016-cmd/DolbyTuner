package com.zero16.dolbytuner

import android.content.Context
import android.graphics.Bitmap

/**
 * 全局壁纸状态 — Scene 同款设计：单例持有原始壁纸和轻度模糊版。
 * GlassCard 读取 [blurred] 实现毛玻璃背景。
 */
object WallpaperState {
    @Volatile
    var original: Bitmap? = null
        private set

    @Volatile
    var blurred: Bitmap? = null
        private set

    @Volatile
    var isDark: Boolean = false
        private set

    /** 屏幕物理宽度 / 192（Scene 同款缩放比），用于坐标换算 */
    @Volatile
    var scale: Float = 1f
        private set

    /** 当前窗口尺寸（px），卡片按这个把模糊壁纸映射到自己那块区域 */
    @Volatile
    var screenW: Int = 1080
        private set

    @Volatile
    var screenH: Int = 2400
        private set

    fun set(
        ctx: Context,
        bitmap: Bitmap,
        dark: Boolean,
        screenWidthPx: Int,
        screenHeightPx: Int,
        blurRadius: Float = 16f
    ) {
        screenW = screenWidthPx
        screenH = screenHeightPx
        val oldOriginal = original
        val oldBlurred = blurred
        // 先设置新图，再回收旧图 — 避免 Compose 读已回收 Bitmap 崩溃
        original = bitmap
        blurred = BlurUtils.blur(ctx, bitmap, radius = blurRadius)
        isDark = dark
        scale = if (blurred != null && blurred!!.width > 0) {
            screenWidthPx.toFloat() / blurred!!.width.toFloat()
        } else 1f
        oldOriginal?.recycle()
        oldBlurred?.recycle()
    }

    fun clear() {
        val oldOriginal = original
        val oldBlurred = blurred
        original = null
        blurred = null
        scale = 1f
        oldOriginal?.recycle()
        oldBlurred?.recycle()
    }
}