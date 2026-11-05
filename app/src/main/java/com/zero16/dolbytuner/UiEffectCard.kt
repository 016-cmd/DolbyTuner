package com.zero16.dolbytuner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 音效页总开关状态：关闭时整页参数区全部只读 */
internal val LocalDpOn = androidx.compose.runtime.compositionLocalOf { true }

/**
 * 统一效果大卡片（参考 V4A 页交互，代码自己写）：
 *
 *   ┌ Row：图标 + 标题/摘要 + Switch   ← 点整行展开
 *   └ AnimatedVisibility：参数区
 *
 * @param toggleOnly 只有开关、没有参数时传 true（此时不展开）
 */
@Composable
internal fun EffectCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    accent: Color = Accent,
    toggleOnly: Boolean = false,
    showSwitch: Boolean = true,
    initiallyExpanded: Boolean = false,
    /** false = 永远不展开：参数区（content）直接常驻显示，点标题也不展开 */
    expandable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val dpOn = LocalDpOn.current
    val effEnabled = enabled && dpOn
    val cs = MaterialTheme.colorScheme
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    // 每张卡片按「类别色」上色，不依赖开关状态：
    // 关闭 = 很淡的类别底 + 淡彩边；开启 = 更实的类别底 + 更亮的彩边
    // （浅色 / 深色模式用两档透明度，避免在壁纸上发白或发灰）
    // 卡片本体统一走液态玻璃填充（透壁纸、不染色）；类别色只留在图标与开关上
    val container = Appearance.glassFill(dark)
    val borderColor = if (dark) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.06f)
    val subColor = if (dark) Color.White.copy(alpha = 0.72f) else Color(0xFF2E2E2E).copy(alpha = 0.66f)

    GlassSurface(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        radius = 16
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .then(
                        if (toggleOnly || !expandable) Modifier
                        else Modifier.clickable { expanded = !expanded }
                    )
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon, null,
                    Modifier.size(20.dp),
                    tint = if (effEnabled) accent else cs.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle, fontSize = 9.sp, lineHeight = 11.sp,
                            color = subColor, maxLines = 1
                        )
                    }
                }
                if (showSwitch) {
                    Switch(checked = enabled, onCheckedChange = onEnabledChange, enabled = dpOn)
                }
            }

            if (!toggleOnly && !expandable) {
                Column(
                    Modifier.fillMaxWidth()
                        .alpha(if (dpOn) 1f else 0.45f)
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    content = content
                )
            } else if (!toggleOnly) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .alpha(if (dpOn) 1f else 0.45f)
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                        content = content
                    )
                }
            }
        }
    }
}

/** 参数滑杆：标题 + 右侧数值 + Slider（拖动实时回调） */
@Composable
internal fun ParamSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    enabled: Boolean = true,
    onValue: (Float) -> Unit,
    onFinished: (Float) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var local by remember(value) { mutableStateOf(value) }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                valueText, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) Accent else cs.onSurfaceVariant
            )
        }
        Slider(
            value = local,
            onValueChange = {
                val v = if (step > 0f) (it / step).roundToInt() * step else it
                local = v
                onValue(v)
            },
            onValueChangeFinished = { onFinished(local) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(30.dp)
        )
    }
}

/** 参数开关行：标题 + 说明 + Switch */
@Composable
internal fun ParamToggle(
    label: String,
    hint: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp)
            if (hint.isNotBlank()) {
                Text(hint, fontSize = 9.sp, color = cs.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** 参数多选一：标题 + 一行分段按钮（2~4 个） */
@Composable
internal fun ParamChoice(
    label: String,
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            options.forEachIndexed { i, text ->
                val active = i == selected
                Card(
                    Modifier.weight(1f).padding(end = if (i == options.lastIndex) 0.dp else 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) Accent.copy(alpha = if (enabled) 0.20f else 0.08f)
                        else cs.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (enabled) Modifier.clickable { onSelect(i) } else Modifier)
                            .padding(vertical = 7.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text, fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) Accent else cs.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}