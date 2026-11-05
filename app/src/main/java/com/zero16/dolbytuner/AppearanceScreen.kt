package com.zero16.dolbytuner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 外观 & 个性化：自有版式（分节 + 胶囊选择器），功能项与参数对齐 Flowmix */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var wallpaperUri by remember { mutableStateOf(ThemePrefs.getWallpaperUri(ctx)) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = File(ctx.filesDir, "wallpaper_bg")
                        ctx.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { out -> input.copyTo(out) }
                        }
                        f.path
                    }.getOrNull()
                }
                if (ok != null) {
                    ThemePrefs.setWallpaperUri(ctx, ok)
                    wallpaperUri = ok
                }
            }
        }
    }

    LaunchedEffect(wallpaperUri) {
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val p = ThemePrefs.getWallpaperUri(ctx) ?: return@runCatching null
                if (p.startsWith("content://")) {
                    ctx.contentResolver.openInputStream(Uri.parse(p))?.use { BitmapFactory.decodeStream(it) }
                } else BitmapFactory.decodeFile(p)
            }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 120.dp)
    ) {
        // 顶栏
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = cs.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("外观", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("玻璃质感 · 壁纸 · 动效", fontSize = 10.sp, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── 玻璃 ──
        GlassCard(Modifier.fillMaxWidth()) {
            Text("玻璃质感", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "卡片底色固定，图标与滑条颜色取自壁纸",
                fontSize = 9.sp, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            SwitchRow(
                "高级视觉效果",
                if (Appearance.enabled.value) "已开启" else "已关闭，卡片回退为纯色",
                Appearance.enabled.value
            ) { Appearance.setEnabled(ctx, it) }

            Spacer(Modifier.height(10.dp))
            SectionLabel("玻璃效果")
            SegRow(
                listOf(GlassMode.FROSTED.label, GlassMode.LIQUID.label),
                if (Appearance.mode.value == GlassMode.FROSTED) 0 else 1
            ) { i ->
                Appearance.setMode(ctx, if (i == 0) GlassMode.FROSTED else GlassMode.LIQUID)
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("玻璃模糊程度")
            SegRow(GlassBlur.values().map { it.label }, Appearance.blur.value.ordinal) { i ->
                Appearance.setBlur(ctx, GlassBlur.values()[i])
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when (Appearance.blur.value) {
                    GlassBlur.TRANSPARENT -> "最清透，壁纸细节几乎都在"
                    GlassBlur.DEFAULT -> "日常推荐，内容清晰、背景柔化"
                    GlassBlur.HAZY -> "背景化得更狠，适合花哨壁纸"
                },
                fontSize = 9.sp, color = cs.onSurfaceVariant, lineHeight = 13.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── 液态细节 ──
        val liquid = Appearance.mode.value == GlassMode.LIQUID && Appearance.enabled.value
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("液态细节", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                if (!liquid) {
                    Text("（切到液态玻璃才生效）", fontSize = 9.sp, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("边缘折射")
            SegRow(Level3.values().map { it.label }, Appearance.refract.value.ordinal) {
                Appearance.setRefract(ctx, Level3.values()[it])
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("边缘色散")
            SegRow(BoolLevel.values().map { it.label }, Appearance.dispersion.value.ordinal) {
                Appearance.setDispersion(ctx, BoolLevel.values()[it])
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("深度效果")
            SegRow(BoolLevel.values().map { it.label }, Appearance.depth.value.ordinal) {
                Appearance.setDepth(ctx, BoolLevel.values()[it])
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("可读性增强")
            SegRow(Readability.values().map { it.label }, Appearance.readability.value.ordinal) {
                Appearance.setReadability(ctx, Readability.values()[it])
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("对比度")
            SegRow(LevelTune.values().map { it.label }, Appearance.contrast.value.ordinal) {
                Appearance.setContrast(ctx, LevelTune.values()[it])
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("饱和度")
            SegRow(LevelTune.values().map { it.label }, Appearance.saturation.value.ordinal) {
                Appearance.setSaturation(ctx, LevelTune.values()[it])
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 壁纸 ──
        GlassCard(Modifier.fillMaxWidth()) {
            Text("壁纸", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cs.surfaceVariant.copy(alpha = 0.35f))
                    .border(0.5.dp, cs.outlineVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(), null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("还没有设置壁纸", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("选择图片", true) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PillButton("保存到相册", false) {
                    val b = preview
                    if (b == null) {
                        toast(ctx, "还没有壁纸可以保存")
                    } else {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { saveToGallery(ctx, b) }
                            toast(ctx, if (ok) "已保存到相册" else "保存失败")
                        }
                    }
                }
                PillButton("清除", false) {
                    ThemePrefs.setWallpaperUri(ctx, null)
                    wallpaperUri = null
                    preview = null
                    toast(ctx, "已恢复纯色背景")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 动效 ──
        GlassCard(Modifier.fillMaxWidth()) {
            Text("动效", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            SwitchRow("页面切换动画", "左右滑动 + 淡入淡出", Appearance.animPage.value) {
                Appearance.setAnimPage(ctx, it)
            }
            SwitchRow("卡片入场动画", "卡片依次淡入放大", Appearance.animCard.value) {
                Appearance.setAnimCard(ctx, it)
            }
            SwitchRow("启动动画", "冷启动时淡入", Appearance.animLaunch.value) {
                Appearance.setAnimLaunch(ctx, it)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "玻璃效果只影响卡片与底栏，不会采集背景画面，滚动时也不额外耗电。",
            fontSize = 9.sp, lineHeight = 14.sp, color = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
}

/** 胶囊选择器：一排圆角选项，选中项用强调色 */
@Composable
private fun SegRow(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.35f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (sel) Accent.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) Accent else cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp)
            Text(subtitle, fontSize = 9.sp, color = cs.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.heightIn(min = 34.dp)
            .clip(CircleShape)
            .background(
                if (primary) Accent.copy(alpha = 0.18f)
                else cs.surfaceVariant.copy(alpha = 0.45f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (primary) {
                Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text, fontSize = 11.sp,
                color = if (primary) Accent else cs.onSurface
            )
        }
    }
}

private fun toast(ctx: Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
}

/** 把壁纸写进系统相册（Pictures/DolbyTuner） */
private fun saveToGallery(ctx: Context, bmp: Bitmap): Boolean = runCatching {
    val name = "DolbyTuner_${System.currentTimeMillis()}.png"
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DolbyTuner")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        ctx.contentResolver.update(uri, values, null, null)
        true
    } else {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "DolbyTuner"
        ).apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.insertImage(ctx.contentResolver, f.absolutePath, name, null)
        true
    }
}.getOrDefault(false)