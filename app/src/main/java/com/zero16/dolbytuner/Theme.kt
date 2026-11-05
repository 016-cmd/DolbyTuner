package com.zero16.dolbytuner

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.graphics.BitmapFactory

// ── 品牌色 ──
private val AccentGreen = Color(0xFF22C55E)
private val AccentGreenDim = Color(0xFF166534)
private val AccentBlue = Color(0xFF5B9BD5)

private val LightBackground = Color(0xFFF5F5F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEBEBF0)
private val LightTextPrimary = Color(0xFF1A1A1E)
private val LightTextSecondary = Color(0xFF6B6B78)

private val DarkBackground = Color(0xFF0D0D0F)
private val DarkSurface = Color(0xFF1A1A1E)
private val DarkSurfaceVariant = Color(0xFF25252B)
private val DarkTextPrimary = Color(0xFFEBEBF0)
private val DarkTextSecondary = Color(0xFFB0B0B8)  // 提亮，深色卡片中可读

private val LightColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = Color.White,
    primaryContainer = AccentGreenDim.copy(alpha = 0.3f),
    onPrimaryContainer = AccentGreenDim,
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = AccentBlue.copy(alpha = 0.15f),
    onSecondaryContainer = AccentBlue,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFC8C8D0),
    outlineVariant = Color(0xFFE0E0E6),
    error = Color(0xFFCF6679),
    onError = Color.White,
    scrim = Color.Black
)

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color(0xFF1A1A1E),
    primaryContainer = AccentGreenDim,
    onPrimaryContainer = Color(0xFFB3F5C0),
    secondary = AccentBlue,
    onSecondary = Color(0xFF0D0D0F),
    secondaryContainer = Color(0xFF1B3A5C),
    onSecondaryContainer = Color(0xFFB8D8F8),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF3A3A45),
    outlineVariant = Color(0xFF25252B),
    error = Color(0xFFCF6679),
    onError = Color(0xFF0D0D0F),
    scrim = Color.Black
)

/**
 * 自适应提示色：背景亮时用深色，背景暗时自动换亮色。
 * 深色模式下固定的深棕/深红几乎看不见，所有提示文字都走这里。
 */
/**
 * 卡片里的小字：
 *   可读性=关 → 跟主题（浅黑 / 深白）
 *   可读性=自动/强 → 跟**壁纸亮度**（壁纸暗→白字，壁纸亮→黑字）
 */
@Composable
fun subInk(alpha: Float = 0.78f): Color {
    val lvl = Appearance.readLevel.value
    if (lvl == 0) {
        return if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
            Color.Black.copy(alpha = alpha)
        else Color.White.copy(alpha = alpha.coerceAtLeast(0.82f))
    }
    val darkWallpaper = WallpaperState.lum < 0.5f
    val a = if (lvl >= 2) 0.96f else alpha.coerceAtLeast(0.82f)
    return if (darkWallpaper) Color.White.copy(alpha = a)
    else Color.Black.copy(alpha = a)
}

@Composable
fun hintInk(light: Color, dark: Color): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) light else dark

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private const val PREFS_THEME = "dolby_tuner"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_WALLPAPER = "wallpaper_uri"

object ThemePrefs {
    fun getMode(ctx: Context): ThemeMode = try {
        ThemeMode.valueOf(ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, "SYSTEM")!!)
    } catch (_: Exception) { ThemeMode.SYSTEM }

    fun setMode(ctx: Context, mode: ThemeMode) {
        ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getWallpaperUri(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE).getString(KEY_WALLPAPER, null)

    fun setWallpaperUri(ctx: Context, uri: String?) {
        ctx.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE).edit().putString(KEY_WALLPAPER, uri).apply()
    }
}

// ── DolbyTunerTheme ──
@Composable
fun DolbyTunerTheme(content: @Composable () -> Unit) {
    // recreate() 后整个 Composable 树重建，直接从 SharedPreferences 读取
    val ctx = LocalContext.current
    val mode = ThemePrefs.getMode(ctx)
    val wallpaperUri = ThemePrefs.getWallpaperUri(ctx)

    // 外观参数只加载一次（换了壁纸会重新算强调色）
    LaunchedEffect(ctx) { Appearance.load(ctx) }

    val isDark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    // 用 onSizeChanged 获取真实窗口宽度 → WallpaperState.scale（Scene 同款：rootView.width/192）
    var windowWidthPx by remember { mutableIntStateOf(1080) }
    var windowHeightPx by remember { mutableIntStateOf(2400) }

    // 壁纸变化时异步加载 bitmap → 模糊 → WallpaperState
    // 使用 state 计数器触发 Compose 重组（WallpaperState 是非 Compose 对象）
    var wallpaperVersion by remember { mutableIntStateOf(0) }

    // 窗口宽度或壁纸变化时重新加载 / 生成壁纸
    val wpKind = Appearance.wallpaperKind.value
    val wpDim = Appearance.wallpaperDim.value
    val blurDp = Appearance.blurRadius.value
    LaunchedEffect(wallpaperUri, isDark, windowWidthPx, windowHeightPx, wpKind, blurDp) {
        if (wpKind > 0) {
            // 内置氛围壁纸：代码生成，不联网
            withContext(Dispatchers.IO) {
                WallpaperState.setBuiltin(
                    ctx, wpKind, isDark, windowWidthPx, windowHeightPx, blurDp.toFloat()
                )
                val bmp = WallpaperState.original
                if (bmp != null) {
                    val pal = Appearance.computePalette(bmp, isDark)
                    withContext(Dispatchers.Main) { Appearance.cachePalette(ctx, pal) }
                }
            }
            wallpaperVersion++
        } else if (wallpaperUri != null) {
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = if (wallpaperUri.startsWith("content://")) {
                        ctx.contentResolver.openInputStream(
                            android.net.Uri.parse(wallpaperUri)
                        )?.use { BitmapFactory.decodeStream(it) }
                    } else {
                        BitmapFactory.decodeFile(wallpaperUri)
                    }
                    if (bitmap != null) {
                        WallpaperState.setLocal(
                            ctx, bitmap, isDark, windowWidthPx, windowHeightPx, blurDp.toFloat()
                        )
                        val pal = Appearance.computePalette(bitmap, isDark)
                        withContext(Dispatchers.Main) { Appearance.cachePalette(ctx, pal) }
                    }
                }
                wallpaperVersion++
            } catch (_: Exception) {}
        } else {
            // 没有本地图 → 用「晨雾」兜底，避免黑屏
            withContext(Dispatchers.IO) {
                WallpaperState.setBuiltin(
                    ctx, 2, isDark, windowWidthPx, windowHeightPx, blurDp.toFloat()
                )
                val bmp = WallpaperState.original
                if (bmp != null) {
                    val pal = Appearance.computePalette(bmp, isDark)
                    withContext(Dispatchers.Main) { Appearance.cachePalette(ctx, pal) }
                }
            }
            wallpaperVersion++
        }
    }

    // 把取色结果写进全局 Accent（普通 var，但读它的 Composable 会随重组拿到新值）
    val accentNow = Appearance.accent.value
    SideEffect { Accent = accentNow }

    // 读取 wallpaperVersion 确保 Compose 追踪变化
    val version = wallpaperVersion
    // 壁纸层 backdrop：卡片（drawBackdrop）从这一层采样
    val glassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop { drawContent() }

    MaterialTheme(colorScheme = colorScheme, typography = Typography()) {
        // Material3 的 LocalContentColor 默认值是「黑色」，而我们整个页面是直接铺在
        // 背景/壁纸上的（没有 Surface 兜底），于是所有没显式写 color 的 Text
        // ——包括页标题、卡片标题——在深色模式下都会变成黑字。
        // 这里统一按 onBackground 提供，深色/浅色都跟着主题走。
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .onSizeChanged { size ->
                windowWidthPx = size.width
                windowHeightPx = size.height
            }
        ) {
            // 全屏壁纸：直接用 WallpaperState 原始 Bitmap，与模糊壁纸同一来源
            @Suppress("UNUSED_EXPRESSION")
            version // 读取触发重组
            // 壁纸永远清晰；模糊只发生在「卡片自己那块区域」内
            val wallpaperBitmap = WallpaperState.original
            // 壁纸层单独放进 backdrop；卡片是这层之上的内容，不会递归采样自己
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(glassBackdrop)
            ) {
                if (wallpaperBitmap != null) {
                    Image(
                        bitmap = wallpaperBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                // 壁纸压暗（给玻璃提供更稳的对比度）
                if (wpDim > 0) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = wpDim / 100f))
                    )
                }
            }
            GlassBackdrop.instance = glassBackdrop
            // 内容层直接叠加在壁纸（或纯色背景）上
            content()
        }
        }
    }
}