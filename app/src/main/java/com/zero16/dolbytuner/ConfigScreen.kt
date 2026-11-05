package com.zero16.dolbytuner

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
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
    var status by remember { mutableStateOf("预设包含 GEQ 与音效参数") }

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var saveScope by remember { mutableStateOf(SCOPE_DOLBY) }

    var showExportConfirm by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf("") }
    var exportSel by remember { mutableStateOf(-1) }   // -1 = 全部

    var applyTarget by remember { mutableStateOf<Preset?>(null) }
    var deleteTarget by remember { mutableStateOf<Preset?>(null) }
    var detailTarget by remember { mutableStateOf<Preset?>(null) }
    var appliedName by remember { mutableStateOf("") }
    var appliedScope by remember { mutableStateOf(SCOPE_DOLBY) }
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
                    val dstr = o.optString("dp", "")
                    if (geq.size == DapRealtime.BANDS) incoming += Preset(
                        o.getString("name"), geq,
                        o.readFxJson(),
                        dpUnpack(dstr),
                        o.optInt("scope", SCOPE_DOLBY),
                        o.optInt("bb", 0) == 1, o.optInt("bba", 60),
                        o.optInt("ds", 0) == 1, o.optInt("dsa", 40),
                        o.optInt("ag", 0) == 1
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
            colors = CardDefaults.cardColors(containerColor = Appearance.glassFill(androidx.compose.foundation.isSystemInDarkTheme()))
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
                        exportSel = -1
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
                        "选择要保存的范围",
                        fontSize = 10.sp, color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            SCOPE_DOLBY to "Dolby",
                            SCOPE_DP to "DP",
                            SCOPE_BOTH to "两者"
                        ).forEach { (v, label) ->
                            val on = saveScope == v
                            Surface(
                                Modifier.weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { saveScope = v },
                                shape = RoundedCornerShape(8.dp),
                                color = if (on) Accent.copy(alpha = 0.22f)
                                else cs.surfaceVariant.copy(alpha = 0.40f),
                                border = BorderStroke(
                                    1.dp, Accent.copy(alpha = if (on) 0.7f else 0.2f)
                                )
                            ) {
                                Text(
                                    label, fontSize = 10.sp,
                                    modifier = Modifier.padding(vertical = 7.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    color = if (on) Accent else cs.onSurface
                                )
                            }
                        }
                    }
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
                    val dp = if (saveScope != SCOPE_DOLBY) loadDpSnapshot(ctx) else null
                    val dpPrefs = ctx.getSharedPreferences("dynproc", Context.MODE_PRIVATE)
                    writePresets(
                        ctx,
                        presets.filterNot { it.name == name } +
                            Preset(
                                name, loadGeq(ctx), loadFx(ctx), dp, saveScope,
                                dpPrefs.getBoolean("bb_on", false), dpPrefs.getInt("bb_amt", 60),
                                dpPrefs.getBoolean("ds_on", false), dpPrefs.getInt("ds_amt", 40),
                                dpPrefs.getBoolean("auto_gain", false)
                            )
                    )
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
                    Text(
                        if (exportSel < 0) "选择要导出的预设（当前：全部 ${presets.size} 个）"
                        else "选择要导出的预设（当前：${presets.getOrNull(exportSel)?.name ?: "--"}）",
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth().height(190.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ExportPickRow("全部预设（${presets.size} 个）", exportSel < 0, cs) { exportSel = -1 }
                        presets.forEachIndexed { i, p ->
                            ExportPickRow(p.name, exportSel == i, cs) { exportSel = i }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("保存位置", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                    Text("内部存储 / Download / DolbyTuner /", fontSize = 10.sp, color = Accent)
                    Spacer(Modifier.height(6.dp))
                    Text("文件名", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
                    Text(
                        if (exportSel < 0) exportName
                        else exportName.replace(".json", "_${presets.getOrNull(exportSel)?.name ?: ""}.json"),
                        fontSize = 10.sp, color = Accent
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    scope.launch {
                        val sel = exportSel
                        val target = if (sel < 0) presets else listOfNotNull(presets.getOrNull(sel))
                        if (target.isEmpty()) {
                            status = "没有可导出的预设"
                            return@launch
                        }
                        val fname = if (sel < 0) exportName
                        else exportName.replace(".json", "_${target[0].name}.json")
                        val name = withContext(Dispatchers.IO) { exportPresets(ctx, fname, target) }
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
                    Text("将应用「${p.name}」：", fontSize = 11.sp)
                    if (p.scope == SCOPE_DOLBY || p.scope == SCOPE_BOTH) {
                        Text("· 杜比：GEQ 20 段 + 音效页参数", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
                    if (p.scope == SCOPE_DP || p.scope == SCOPE_BOTH) {
                        Text("· 音效页（DP）：EQ / 限幅 / MBC / 等响", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    }
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
                            appliedScope = target.scope
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
                    // 指向这个预设的设备绑定一起清掉，否则主页会挂着一个不存在的预设
                    val freed = DeviceTuningStore.unbindPreset(ctx, name)
                    DeviceState.sync(ctx)
                    reload()
                    status = if (freed > 0) "已删除：$name（同时解绑 $freed 个设备）" else "已删除：$name"
                }) { Text("删除", color = hintInk(Color(0xFFDC2626), Color(0xFFF87171)), fontSize = 12.sp) }
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
                    Text("「$appliedName」已生效，当前设备马上能听到。", fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    when (appliedScope) {
                        SCOPE_DP -> {
                            Text(
                                "这份预设只含音效页参数，已写进 DynamicsProcessing。",
                                fontSize = 10.sp, lineHeight = 15.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "它存在 APP 的本地偏好里，不需要额外「保存」：只要音效页总开关是开的，重启后打开 APP 会自动按这份参数重新下发。",
                                fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                            )
                        }
                        SCOPE_DOLBY -> {
                            Text(
                                "这份预设只含杜比参数，已写入杜比的实时层。",
                                fontSize = 10.sp, lineHeight = 15.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "实时层只在这次开机内有效：想让它重启手机后仍然沿用，请到「音效」页点『保存到 Dolby（GEQ + 音效）』。",
                                fontSize = 10.sp, lineHeight = 15.sp,
                                color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24))
                            )
                        }
                        else -> {
                            Text(
                                "这份预设同时含杜比与音效页两部分，都已写入。",
                                fontSize = 10.sp, lineHeight = 15.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "杜比部分只在这次开机内有效：想让它重启后仍然沿用，请到「音效」页点『保存到 Dolby（GEQ + 音效）』。",
                                fontSize = 10.sp, lineHeight = 15.sp,
                                color = hintInk(Color(0xFFB45309), Color(0xFFFBBF24))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "音效页部分不需要保存，重启后打开 APP 会自动重新下发。",
                                fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                            )
                        }
                    }
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
                val d = p.dp
                if (p.scope == SCOPE_DP && d != null) {
                    DpEqCanvas(
                        curve = d.curve,
                        fMin = 20f,
                        fMax = 20000f,
                        accent = Color(0xFF6366F1),
                        height = 140.dp
                    )
                } else {
                    BandCurveCanvas(
                        points = DapRealtime.FREQS.mapIndexed { i, f -> f to p.geq[i] },
                        lineColor = Color(0xFF16A34A),
                        showDelta = false
                    )
                }
            }

            // 中 20%：音效页参数摘要（点击看详细参数）
            Column(
                Modifier.fillMaxHeight().weight(0.20f).padding(horizontal = 2.dp)
                    .clickable { onOpen() },
                verticalArrangement = Arrangement.Center
            ) {
                val d = p.dp
                if (p.scope == SCOPE_DP && d != null) {
                    FxLine("EQ", if (d.eqTab == 0) "GEQ" else "PEQ", ink)
                    FxLine("增益", String.format("%+.1f", d.gain / 10f), ink)
                    FxLine("限幅", mark(d.limOn), ink)
                    FxLine("MBC", mark(d.mbcOn), ink)
                    FxLine("等响", mark(d.elOn), ink)
                } else {
                    FxLine("环绕", "耳${mark(p.fx.hpVirtual)} 外${mark(p.fx.spVirtual)}", ink)
                    FxLine("人声", "${mark(p.fx.dialogOn)} ${p.fx.dialogAmt}", ink)
                    FxLine("低音", mark(p.fx.bassOn), ink)
                    FxLine("增益", p.fx.bassGain.toString(), ink)
                    FxLine("截止", p.fx.bassCutoff.toString(), ink)
                    FxLine("带宽", p.fx.bassWidth.toString(), ink)
                }
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

/** 在 127 点曲线上取最接近 hz 的那个点的增益 */
private fun atFreq(curve: FloatArray, hz: Int): Float {
    if (curve.size != 127) return 0f
    var bi = 0
    var bd = Float.MAX_VALUE
    for (i in 0 until 127) {
        val dd = kotlin.math.abs(EqMath.FREQ127[i] - hz)
        if (dd < bd) { bd = dd; bi = i }
    }
    return curve[bi]
}

/** 0.1 dB 单位 → 带符号字符串 */
private fun fmtDb(v: Int): String = String.format("%+.1f", v / 10f)

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
                if (p.scope != SCOPE_DP) {
                    DetailTitle("杜比 GEQ · 20 段", cs)
                    Text(
                        DapRealtime.FREQS.mapIndexed { i, f -> "${f.toInt()}Hz ${p.geq[i]}" }
                            .chunked(3).joinToString("\n") { it.joinToString("   ") },
                        fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                    )
                }

                DetailTitle("杜比音效参数", cs)
                DetailRow("耳机虚拟环绕", mark(p.fx.hpVirtual), cs)
                DetailRow("扬声器虚拟环绕", mark(p.fx.spVirtual), cs)
                DetailRow("人声增强", mark(p.fx.dialogOn), cs)
                DetailRow("人声强度", p.fx.dialogAmt.toString(), cs)
                DetailRow("低音增强", mark(p.fx.bassOn), cs)
                DetailRow("低音增益", p.fx.bassGain.toString(), cs)
                DetailRow("低音截止", p.fx.bassCutoff.toString(), cs)
                DetailRow("低音带宽", p.fx.bassWidth.toString(), cs)
                DetailRow("虚拟低频", mark(p.fx.vbOn), cs)
                DetailRow("虚拟·强度 / 谐波混合", "${p.fx.vbAmt} / ${p.fx.vbBlend}", cs)
                DetailRow("虚拟·谐波增益 / 起始频率", "${p.fx.vbHarm} / ${p.fx.vbLow} Hz", cs)

                val d = p.dp
                if (p.scope != SCOPE_DOLBY && d != null) {
                    DetailTitle("音效页（DP）· EQ", cs)
                    DetailRow("EQ 模式", if (d.eqTab == 0) "GEQ · 20 段" else "PEQ · ${d.peq.split(";").count { it.isNotBlank() }} 段", cs)
                    if (d.eqTab == 0) {
                        DetailRow("段间过渡 Q", String.format("%.1f", d.geqQ / 10f), cs)
                        DetailRow("总增益 Master", String.format("%+.1f dB", d.geqMaster / 10f), cs)
                        DetailRow("曲线采样（dB）", "", cs)
                    } else {
                        Text(
                            d.peq.split(";").filter { it.isNotBlank() }.map { seg ->
                                val a = seg.split(",").mapNotNull { it.toFloatOrNull() }
                                if (a.size < 3) seg else "${a[0].toInt()}Hz ${String.format("%+.1f", a[1])}dB Q${String.format("%.1f", a[2])}"
                            }.chunked(2).joinToString("\n") { it.joinToString("   ") },
                            fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                        )
                    }
                    if (d.eqTab == 0) {
                        Text(
                            DapRealtime.FREQS.map { hz -> "${hz}Hz ${String.format("%+.1f", atFreq(d.curve, hz))}" }
                                .chunked(2).joinToString("\n") { it.joinToString("   ") },
                            fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                        )
                    }

                    DetailTitle("音效页（DP）· 动态", cs)
                    DetailRow("输入增益", String.format("%+.1f dB", d.gain / 10f), cs)
                    DetailRow("动态限幅", mark(d.limOn), cs)
                    DetailRow("阈值 / 启动", String.format("%+.1f dB / %.1f ms", d.lim.getOrElse(0) { -20 } / 10f, d.lim.getOrElse(1) { 10 } / 10f), cs)
                    DetailRow("释放 / 压缩比", String.format("%d ms / %.1f:1", d.lim.getOrElse(2) { 60 }, d.lim.getOrElse(3) { 100 } / 10f), cs)
                    DetailRow("后级增益 / 联动", String.format("%+.1f dB / %s", d.lim.getOrElse(4) { 0 } / 10f, if (d.lim.getOrElse(5) { 0 } == 0) "立体声联动" else "组 ${d.lim.getOrElse(5) { 0 }}"), cs)
                    DetailRow("多段压缩", mark(d.mbcOn), cs)
                    d.mbc.forEachIndexed { i, b ->
                        val nm = listOf("Low 250", "Mid 2k", "High 8k", "Air 20k").getOrElse(i) { "段 ${i + 1}" }
                        Text(
                            "$nm ${mark(b.getOrElse(9) { 0 } == 1)}" +
                                " 阈${fmtDb(b.getOrElse(0) { -180 })} 比${String.format("%.1f", b.getOrElse(1) { 20 } / 10f)}" +
                                " 启${String.format("%.1f", b.getOrElse(2) { 20 } / 10f)}ms 放${b.getOrElse(3) { 150 }}ms" +
                                " 膝${b.getOrElse(4) { 60 }} 门${fmtDb(b.getOrElse(5) { -900 })}" +
                                " 扩${String.format("%.1f", b.getOrElse(6) { 10 } / 10f)}" +
                                " 前${fmtDb(b.getOrElse(7) { 0 })} 后${fmtDb(b.getOrElse(8) { 0 })}",
                            fontSize = 9.sp, lineHeight = 13.sp, color = cs.onSurfaceVariant
                        )
                    }

                    DetailRow(
                        "低频增强",
                        if (p.bbOn) String.format("开 · +%.1f dB", p.bbAmt / 10f) else "关", cs
                    )
                    DetailRow("齿音抑制", if (p.dsOn) "开 · ${p.dsAmt}%" else "关", cs)
                    DetailRow("等比增益", mark(p.autoGain), cs)
                    DetailTitle("音效页（DP）· 后置均衡", cs)

                    DetailRow("后置均衡", mark(d.postOn), cs)
                    Text(
                        listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                            .mapIndexed { i, f -> "$f ${String.format("%+.1f", d.post.getOrElse(i) { 0 } / 10f)}" }
                            .chunked(5).joinToString("\n") { it.joinToString("  ") },
                        fontSize = 10.sp, lineHeight = 15.sp, color = cs.onSurfaceVariant
                    )
                    DetailRow("左右平衡", String.format("%+.1f dB", d.post.getOrElse(10) { 0 } / 10f), cs)
                    DetailRow("等响补偿", mark(d.elOn), cs)
                    DetailRow("基准音量", if (d.elBase > 0) "${d.elBase}%" else "未设定", cs)
                    DetailRow("补偿强度 / 上限", String.format("%.1f / %.1f dB", d.elStrength / 10f, d.elMaxDb / 10f), cs)
                    DetailRow("整体强度", "${d.eqIntensity}%", cs)
                    if (d.aeq.isNotBlank()) DetailRow("曲线来源", "AutoEQ 导入（127 点）", cs)
                    val nz = d.geqInt.toList().mapIndexedNotNull { idx, v ->
                        if (v != 100) "${DapRealtime.FREQS[idx]}Hz ${v}%" else null
                    }
                    if (nz.isNotEmpty()) DetailRow("逐段强度", nz.joinToString(" / "), cs)
                }

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
private fun ExportPickRow(name: String, selected: Boolean, cs: androidx.compose.material3.ColorScheme, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected) "◉" else "○",
            fontSize = 11.sp,
            color = if (selected) Accent else cs.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name, fontSize = 11.sp, maxLines = 1,
            color = if (selected) Accent else cs.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
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
private fun exportPresets(ctx: Context, name: String, list: List<Preset>): String? {
    val arr = org.json.JSONArray()
    list.forEach { p ->
        arr.put(org.json.JSONObject().apply {
            put("name", p.name)
            put("geq", org.json.JSONArray(p.geq.toList()))
            putFxJson(p.fx)
            put("scope", p.scope)
            put("dp", p.dp?.let { dpPack(it) } ?: "")
            putDpMacros(p)
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