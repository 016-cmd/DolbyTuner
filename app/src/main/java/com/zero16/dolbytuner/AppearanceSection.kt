package com.zero16.dolbytuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * 设置页里的「外观与个性化」——展开/闭合式，不是跳页面。
 * 全部参数实时生效（写 prefs + 触发重组）。
 */
@Composable
fun AppearanceSection(
    onPickWallpaper: () -> Unit,
    onWallpaperKind: (Int) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    val accent = Appearance.accent.value

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Palette, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("外观与个性化", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    summary(),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowDown, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(arrow)
            )
        }

        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(10.dp))

                ToggleRow("液态玻璃", "关闭后卡片退化为纯色块", Appearance.enabled.value) {
                    Appearance.setEnabled(ctx, it)
                }

                GroupLabel("玻璃")
                SliderRow("折射强度", "", Appearance.refraction.value, 0f..100f) {
                    Appearance.setRefraction(ctx, it)
                }
                SegRow("折射边缘", listOf("窄", "标准", "宽"), Appearance.edgeWidth.value) {
                    Appearance.setEdgeWidth(ctx, it)
                }
                SliderRow("模糊", "dp", Appearance.blurRadius.value, 0f..40f) {
                    Appearance.setBlurRadius(ctx, it)
                }
                SliderRow("提亮", "", Appearance.vibrancy.value, 0f..100f) {
                    Appearance.setVibrancy(ctx, it)
                }

                GroupLabel("颜色")
                SliderRow("色散", "", Appearance.dispersion.value, 0f..100f) {
                    Appearance.setDispersion(ctx, it)
                }
                SliderRow("光源角度", "°", Appearance.lightAngle.value, 0f..360f) {
                    Appearance.setLightAngle(ctx, it)
                }
                SegRow("体积光", listOf("关", "弱", "标准", "强"), Appearance.volumeLight.value) {
                    Appearance.setVolumeLight(ctx, it)
                }
                SegRow("卡片底色", listOf("自动", "纯白", "纯黑"), Appearance.tintMode.value) {
                    Appearance.setTintMode(ctx, it)
                }

                GroupLabel("形状")
                SliderRow("圆角", "dp", Appearance.radius.value, 12f..32f) {
                    Appearance.setRadius(ctx, it)
                }
                SegRow("玻璃厚度", listOf("薄", "标准", "厚"), Appearance.thickness.value) {
                    Appearance.setThickness(ctx, it)
                }

                GroupLabel("动效")
                SliderRow("果冻按压", "", Appearance.jelly.value, 0f..100f) {
                    Appearance.setJelly(ctx, it)
                }
                SegRow("弹簧", listOf("软", "标准", "硬"), Appearance.spring.value) {
                    Appearance.setSpring(ctx, it)
                }
                ToggleRow("光照互通", "按压时照亮邻近玻璃", Appearance.crossLight.value) {
                    Appearance.setCrossLight(ctx, it)
                }
                SegRow("性能", listOf("自动", "高画质", "省电"), Appearance.perfMode.value) {
                    Appearance.setPerfMode(ctx, it)
                }

                GroupLabel("壁纸")
                SegRow(
                    "来源",
                    listOf("本地", "极光", "晨雾", "暖砂"),
                    Appearance.wallpaperKind.value
                ) { onWallpaperKind(it) }
                SliderRow("压暗", "", Appearance.wallpaperDim.value, 0f..60f) {
                    Appearance.setWallpaperDim(ctx, it)
                }
                TextButton(
                    onClick = onPickWallpaper,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("从相册选择图片", fontSize = 11.sp, color = accent)
                }

                GroupLabel("页面")
                ToggleRow("页面切换动画", null, Appearance.animPage.value) {
                    Appearance.setAnimPage(ctx, it)
                }
                ToggleRow("卡片入场动画", null, Appearance.animCard.value) {
                    Appearance.setAnimCard(ctx, it)
                }
                ToggleRow("启动动画", null, Appearance.animLaunch.value) {
                    Appearance.setAnimLaunch(ctx, it)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private fun summary(): String {
    val tint = when (Appearance.tintMode.value) { 1 -> "纯白"; 2 -> "纯黑"; else -> "自动" }
    return "液态玻璃 · ${tint} · 圆角 ${Appearance.radius.value}dp · ${WallpaperState.builtinName(Appearance.wallpaperKind.value)}"
}

@Composable
private fun GroupLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp)
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Appearance.accent.value),
            modifier = Modifier.size(width = 40.dp, height = 24.dp)
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    unit: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(
            "${local.toInt()}$unit",
            fontSize = 10.sp, color = Appearance.accent.value
        )
    }
    Slider(
        value = local,
        onValueChange = {
            local = it
            onChange(it.toInt())
        },
        valueRange = range,
        steps = 0,
        modifier = Modifier.height(24.dp)
    )
}

@Composable
private fun SegRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Spacer(Modifier.height(6.dp))
    Text(title, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .jellyPress(Appearance.jelly.value > 0)
                    .background(
                        if (on) Appearance.accent.value.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s,
                    fontSize = 10.sp,
                    color = if (on) Appearance.accent.value else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}