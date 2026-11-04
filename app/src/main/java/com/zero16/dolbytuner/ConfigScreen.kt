package com.zero16.dolbytuner

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 配置页：预设的保存 / 导入 / 导出 / 应用 / 删除。
 *
 * 一行一个预设大卡片，卡片三段：
 *   左 60%  GEQ 曲线预览
 *   中 20%  音效页 8 项参数（淡绿，渐变色过渡）
 *   右 20%  预设名 + 应用 / 删除（深绿）
 */
@Composable
internal fun ConfigScreen() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var presets by remember { mutableStateOf(loadPresets(ctx)) }
    var status by remember { mutableStateOf("预设包含 GEQ、音效与 A 类参数") }

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    var showExportConfirm by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf("") }

    var applyTarget by remember { mutableStateOf<Preset?>(null) }
    var deleteTarget by remember { mutableStateOf<Preset?>(null) }
    var detailTarget by remember { mutableStateOf<Preset?>(null) }
    var appliedName by remember { mutableStateOf("") }
    var showAppliedTip by remember { mutableStateOf(false) }

    fun reload() { presets = loadPresets(ctx) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            }
            if (text == null) {
                status = "导入失败：读不到文件"
                return@launch
            }
            try {
                val arr = org.json.JSONArray(text)
                val incoming = mutableListOf<Preset>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getJSONArray("geq")
                    val geq = IntArray(g.length()) { g.getInt(it) }
                    if (geq.size == DapRealtime.BANDS) incoming += Preset(
                        o.getString("name"), geq,
                        FxState(
                            o.optInt("hp", 0) == 1, o.optInt("sp", 0) == 1,
                            o.optInt("bass", 0) == 1, o.optInt("gain", 0),
                            o.optInt("cutoff", 150), o.optInt("width", 0),
                            o.optInt("dialog", 0) == 1, o.optInt("damt", 4)
                        )
                    )
                }
                val merged = (presets + incoming).distinctBy { it.name }
                writePresets(ctx, merged)
                reload()
                status = "已导入 ${incoming.size} 个预设"
            } catch (t: Throwable) {
                status = "导入失败：${t.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 96.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("配置预设", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // ── 顶部：导入 / 导出 ──────────────────────────────
        Card(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.85f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("备份与恢复", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "导出位置：内部存储 / Download / DolbyTuner",
                    fontSize = 9.sp, color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConfigBtn(Icons.Default.FileDownload, "导入预设", Modifier.weight(1f)) {
                        importer.launch(arrayOf("application/json", "text/plain"))
                    }
                    ConfigBtn(Icons.Default.Share, "导出预设", Modifier.weight(1f)) {
                        exportName = exportFileName()
                        showExportConfirm = true
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("当前共 ${presets.size} 个预设", fontSize = 9.sp, color = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 加号：保存当前为预设 ───────────────────────────
        Box(
            Modifier.fillMaxWidth().height(86.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Accent.copy(alpha = 0.18f), Accent.copy(alpha = 0.05f))
                    )
                )
                .clickable {
                    presetName = ""
                    showSaveDialog = true
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = Accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("保存当前为预设", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Accent)
                    Text("GEQ 20 段 + 全部音效参数", fontSize = 9.sp, color = cs.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 预设大卡片列表 ─────────────────────────────────
        if (presets.isEmpty()) {
            Text(
                "还没有预设，点上面的加号保存一个",
                fontSize = 10.sp, color = cs.onSurfaceVariant
            )
        } else {
            presets.forEach { p ->
                PresetBigCard(
                    p = p,
                    onOpen = { detailTarget = p },
                    onApply = { applyTarget = p },
                    onDelete = { deleteTarget = p }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(status, fontSize = 10.sp, color = cs.onSurfaceVariant)
    }

    // 点击预设卡片：详细参数
    detailTarget?.let { p ->
        PresetDetailDialog(p) { detailTarget = null }
    }

    // ── 保存预设：输入名字 ─────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text(
                        "会把当前 GEQ 20 段、音效页参数与 A 类扩展参数一起保存",
                        fontSize = 10.sp, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp),
                        label = { Text("预设名称", fontSize = 11.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = presetName.trim().ifBlank { "预设 ${presets.size + 1}" }
                    writePresets(ctx, presets.filterNot { it.name == name } + Preset(name, loadGeq(ctx), loadFx(ctx)))
                    reload()
                    presetName = ""
                    showSaveDialog = false
                    status = "已保存预设：$name"
                }) { Text("保存", color = Accent, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    // ── 导出：二次确认 + 解释位置与文件名 ──────────────────
    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("导出预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("将导出 ${presets.size} 个预设（JSON）", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("保存位置", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                    Text("内部存储 / Download / DolbyTuner /", fontSize = 10.sp, color = Accent)
                    Spacer(Modifier.height(6.dp))
                    Text("文件名", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                    Text(exportName, fontSize = 10.sp, color = Accent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "若已有同名文件，系统会自动加序号，不会覆盖旧的。",
                        fontSize = 9.sp, color = cs.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    scope.launch {
                        val name = withContext(Dispatchers.IO) { exportPresets(ctx, exportName) }
                        status = if (name == null) "导出失败：无法写入 Download/DolbyTuner"
                        else "已导出：Download/DolbyTuner/$name"
                    }
                }) { Text("导出", color = Accent, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    // ── 应用：二次确认 ─────────────────────────────────────
    applyTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { applyTarget = null },
            title = { Text("应用预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("将把「${p.name}」写入杜比实时层：", fontSize = 11.sp)
                    Text("· GEQ 20 段", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    Text("· 音效页 8 项参数", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("当前正在输出的声音会立刻变化。", fontSize = 9.sp, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = p
                    applyTarget = null
                    scope.launch {
                        status = "正在应用：${target.name}"
                        val err = applyPreset(ctx, target)
                        if (err == null) {
                            reload()
                            appliedName = target.name
                            status = "已应用：${target.name}"
                            showAppliedTip = true
                        } else {
                            status = "应用失败：$err"
                        }
                    }
                }) { Text("应用", color = Accent, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { applyTarget = null }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    // ── 删除：二次确认 ─────────────────────────────────────
    deleteTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("确定删除「${p.name}」？", fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("删除后无法恢复，已应用到 DSP 的参数不受影响。", fontSize = 9.sp, color = cs.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = p.name
                    deleteTarget = null
                    writePresets(ctx, presets.filterNot { it.name == name })
                    reload()
                    status = "已删除：$name"
                }) { Text("删除", color = Color(0xFFDC2626), fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", fontSize = 12.sp) }
            }
        )
    }

    // ── 应用成功提示：强调必须去音效页保存到 Dolby ─────────
    if (showAppliedTip) {
        AlertDialog(
            onDismissRequest = { showAppliedTip = false },
            title = { Text("已应用", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("「$appliedName」已写入杜比实时层，现在就能听到。", fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "注意：这只是实时生效。要让重启手机后继续沿用，请到「音效」页点『保存到 Dolby（GEQ + 音效）』按钮。",
                        fontSize = 10.sp, color = Color(0xFFB45309)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppliedTip = false }) { Text("知道了", color = Accent, fontSize = 12.sp) }
            }
        )
    }
}

// ────────────────────────────────────────────────────────────
//  卡片与小组件
// ────────────────────────────────────────────────────────────

@Composable
private fun PresetBigCard(
    p: Preset,
    onOpen: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val light = Color(0xFFF0FDF4)
    val mid = Color(0xFFBBF7D0)
    val deep = Color(0xFF22C55E)
    val ink = Color(0xFF14532D)

    Box(
        Modifier.fillMaxWidth().height(152.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    0.00f to cs.surfaceVariant.copy(alpha = 0.92f),
                    0.52f to cs.surfaceVariant.copy(alpha = 0.55f),
                    0.60f to light,
                    0.80f to mid,
                    1.00f to deep
                )
            )
    ) {
        Row(Modifier.fillMaxSize()) {
            // 左 60%：GEQ 曲线（点击看详细参数）
            Box(
                Modifier.fillMaxHeight().weight(0.60f).clickable { onOpen() },
                contentAlignment = Alignment.Center
            ) {
                BandCurveCanvas(
                    points = DapRealtime.FREQS.mapIndexed { i, f -> f to p.geq[i] },
                    lineColor = Color(0xFF16A34A),
                    showDelta = false
                )
            }

            // 中 20%：音效页 8 项 + A 类摘要（点击看详细参数）
            Column(
                Modifier.fillMaxHeight().weight(0.20f).padding(horizontal = 2.dp)
                    .clickable { onOpen() },
                verticalArrangement = Arrangement.Center
            ) {
                FxLine("环绕", "耳${mark(p.fx.hpVirtual)} 外${mark(p.fx.spVirtual)}", ink)
                FxLine("人声", "${mark(p.fx.dialogOn)} ${p.fx.dialogAmt}", ink)
                FxLine("低音", mark(p.fx.bassOn), ink)
                FxLine("增益", p.fx.bassGain.toString(), ink)
                FxLine("截止", p.fx.bassCutoff.toString(), ink)
                FxLine("带宽", p.fx.bassWidth.toString(), ink)
            }

            // 右 20%：名字 + 应用 / 删除（从上到下均分整列高度）
            Column(
                Modifier.fillMaxHeight().weight(0.20f).padding(horizontal = 5.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    p.name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
                GreenMiniButton("应用", Color.White.copy(alpha = 0.26f), onApply)
                GreenMiniButton("删除", Color.Black.copy(alpha = 0.20f), onDelete)
            }
        }
    }
}

@Composable
private fun PresetDetailDialog(p: Preset, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        },
        text = {
            Column(
                Modifier.fillMaxWidth().height(380.dp).verticalScroll(rememberScrollState())
            ) {
                DetailTitle("GEQ · 20 段", cs)
                Text(
                    DapRealtime.FREQS.mapIndexed { i, f -> "${f.toInt()}Hz ${p.geq[i]}" }
                        .chunked(3).joinToString("\n") { it.joinToString("   ") },
                    fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                )

                DetailTitle("音效页参数", cs)
                DetailRow("耳机虚拟环绕", mark(p.fx.hpVirtual), cs)
                DetailRow("扬声器虚拟环绕", mark(p.fx.spVirtual), cs)
                DetailRow("人声增强", mark(p.fx.dialogOn), cs)
                DetailRow("人声强度", p.fx.dialogAmt.toString(), cs)
                DetailRow("低音增强", mark(p.fx.bassOn), cs)
                DetailRow("低音增益", p.fx.bassGain.toString(), cs)
                DetailRow("低音截止", p.fx.bassCutoff.toString(), cs)
                DetailRow("低音带宽", p.fx.bassWidth.toString(), cs)

            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭", fontSize = 12.sp) }
        }
    )
}

@Composable
private fun DetailTitle(text: String, cs: androidx.compose.material3.ColorScheme) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = if (cs.background.luminance() > 0.5f) Color(0xFF14532D) else Color(0xFF86EFAC)
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DetailRow(label: String, value: String, cs: androidx.compose.material3.ColorScheme) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 10.sp, color = cs.onSurface, fontWeight = FontWeight.Medium)
    }
}

private fun mark(b: Boolean) = if (b) "●" else "○"

@Composable
private fun FxLine(label: String, value: String, color: Color) {
    Text(
        "$label $value",
        color = color,
        fontSize = 7.5.sp,
        lineHeight = 9.5.sp,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GreenMiniButton(text: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfigBtn(icon: ImageVector, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
    ) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 11.sp)
    }
}

// ────────────────────────────────────────────────────────────
//  导出到 Download/DolbyTuner/
// ────────────────────────────────────────────────────────────

private fun exportFileName(): String =
    "DolbyTuner_presets_" +
        java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date()) +
        ".json"

/** 写入 Download/DolbyTuner/<name>，成功返回实际文件名 */
private fun exportPresets(ctx: Context, name: String): String? {
    val list = loadPresets(ctx)
    val arr = org.json.JSONArray()
    list.forEach { p ->
        arr.put(org.json.JSONObject().apply {
            put("name", p.name)
            put("geq", org.json.JSONArray(p.geq.toList()))
            put("hp", if (p.fx.hpVirtual) 1 else 0)
            put("sp", if (p.fx.spVirtual) 1 else 0)
            put("bass", if (p.fx.bassOn) 1 else 0)
            put("gain", p.fx.bassGain)
            put("cutoff", p.fx.bassCutoff)
            put("width", p.fx.bassWidth)
            put("dialog", if (p.fx.dialogOn) 1 else 0)
            put("damt", p.fx.dialogAmt)
        })
    }
    return try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DolbyTuner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(arr.toString(2).toByteArray()) }
        name
    } catch (t: Throwable) {
        null
    }
}