package com.zero16.dolbytuner

import android.content.ContentValues
import android.content.Context
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal data class CapResult(val total: Int, val usable: Int, val file: String)

/**
 * 音效能力检测：枚举系统里所有 AudioEffect 实现，逐条尝试构造并看能否拿到控制权，
 * 再对我们用到的几条链路单独下结论，最后把完整报告写到 Download/DolbyTuner/。
 */
internal object AudioCap {

    private fun modeName(m: Int): String = when (m) {
        AudioEffect.EFFECT_CONNECT_MODE_INSERT -> "插入式 INSERT（跟随单个播放器）"
        AudioEffect.EFFECT_CONNECT_MODE_AUXILIARY -> "辅助式 AUX（挂在混音总线上）"
        else -> "其它模式($m)"
    }

    private val TARGETS = listOf(
        "杜比 DAP（本机实时层）" to "9d4921da-8225-4f29-aefa-39537a04bcaa",
        "杜比 DAP（旧机型 type）" to "ec7178ec-e5e1-4432-a3f4-4657e6795210",
        "DAP 空间器 Spatializer" to "ccd4cf09-a79d-46c2-9aae-06a1698d6c8f",
        "预设混响 PresetReverb" to "47382d60-ddd8-11db-bf3a-0002a5d5c51b",
        "环境混响 EnvironmentalReverb" to "c2e5d5fc-e57f-4418-9e89-9a6dd28e5e2c",
        "小米音效 MiSound" to "5b8e36a5-144a-4c38-b1d7-0002a5d5c51b",
        "动态处理 DynamicsProcessing" to "e0e6539b-1781-7261-676f-6d7564617365",
        "均衡器 Equalizer" to "0bed4300-ddd6-11db-8f34-0002a5d5c51b",
        "低音增强 BassBoost" to "0634f220-ddd4-11db-a0fc-0002a5d5c51b",
        "虚拟环绕 Virtualizer" to "37cc2c00-dddd-11db-9b61-0002a5d5c51b",
        "响度增强 LoudnessEnhancer" to "fe3199be-aed0-11db-88e9-0002a5d5c51b"
    )

    fun scan(ctx: Context): CapResult {
        val sb = StringBuilder()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val ver = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "?"

        sb.appendLine("DolbyTuner 音效能力检测报告")
        sb.appendLine("生成时间：$now")
        sb.appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("系统：Android ${Build.VERSION.RELEASE}（SDK ${Build.VERSION.SDK_INT}）")
        sb.appendLine("APP：DolbyTuner $ver")
        sb.appendLine("说明：本报告只读取系统注册信息并做一次短暂构造测试，不改任何参数。")
        sb.appendLine()

        val list = runCatching { AudioEffect.queryEffects()?.toList() ?: emptyList() }
            .getOrDefault(emptyList())

        sb.appendLine("一、系统上报的音效实现：共 ${list.size} 个")
        sb.appendLine()
        var usable = 0
        val usableNames = ArrayList<String>()
        list.forEachIndexed { idx, d ->
            val name = d.name ?: "未命名"
            var canCreate = "失败"
            var ctl = "—"
            val t0 = System.currentTimeMillis()
            val ok = runCatching {
                val e = AudioEffect(d.type, d.implementation, 0, 0)
                try {
                    canCreate = "可以"
                    val has = runCatching { e.hasControl() }.getOrDefault(false)
                    ctl = if (has) "可以" else "否（已被占用）"
                    if (has) {
                        usable++
                        usableNames.add(name)
                    }
                } finally {
                    runCatching { e.release() }
                }
            }.isSuccess
            val cost = System.currentTimeMillis() - t0
            sb.appendLine("[${idx + 1}] $name")
            sb.appendLine("    type：${d.type}")
            sb.appendLine("    impl：${d.implementation}")
            sb.appendLine("    接入：${modeName(d.connectMode)}")
            sb.appendLine("    新建：$canCreate${if (ok) "（${cost}ms）" else "（构造抛异常，多为系统独占）"}")
            sb.appendLine("    控制权：$ctl")
            sb.appendLine()
        }

        sb.appendLine("二、关键链路逐条判定")
        sb.appendLine()
        TARGETS.forEach { (label, uuidStr) ->
            val uuid = runCatching { UUID.fromString(uuidStr) }.getOrNull()
            val hit = if (uuid == null) null else list.firstOrNull {
                it.type == uuid || it.implementation == uuid
            }
            val verdict = when {
                uuid == null -> "UUID 写法有误"
                hit == null -> "系统没有注册这个音效"
                else -> {
                    val e2 = runCatching {
                        val e = AudioEffect(hit.type, hit.implementation, 0, 0)
                        val has = runCatching { e.hasControl() }.getOrDefault(false)
                        runCatching { e.release() }
                        has
                    }
                    if (e2.getOrDefault(false)) "有注册，且能取得控制权 → 可用"
                    else "有注册，但拿不到控制权（可能被别的应用占着）"
                }
            }
            sb.appendLine("· $label")
            sb.appendLine("    $verdict")
        }
        sb.appendLine()

        sb.appendLine("三、结论")
        sb.appendLine("可用（能取得控制权）：$usable / ${list.size}")
        if (usableNames.isNotEmpty()) sb.appendLine("  " + usableNames.joinToString("、"))
        sb.appendLine()
        sb.appendLine("杜比实时层（GEQ / 音效参数）：" + if (usableNames.any {
                it.contains("Dolby", true) || it.contains("dap", true)
            }) "可用" else "未取得控制权，需先在杜比页确认控制权状态")
        sb.appendLine("DynamicsProcessing（音效页）：" + if (usableNames.any {
                it.contains("Dynamic", true) || it.contains("DP", false)
            }) "可用" else "见上表")
        sb.appendLine("系统混响：以第一/二节里 PresetReverb / EnvironmentalReverb 的判定为准")
        sb.appendLine()
        sb.appendLine("（报告结束）")

        val fileName = "AudioEffects_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".txt"
        val written = writeReport(ctx, fileName, sb.toString())
        return CapResult(
            total = list.size,
            usable = usable,
            file = if (written) fileName else ""
        )
    }

    private fun writeReport(ctx: Context, name: String, text: String): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/DolbyTuner"
            )
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) false
        else {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }
    } catch (t: Throwable) {
        false
    }
}

@Composable
internal fun CapabilitySection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    var res by remember { mutableStateOf<CapResult?>(null) }

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.08f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Tune, null, Modifier.size(20.dp), tint = Accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("音效能力检测", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "枚举本机全部音效实现，导出一份完整报告",
                    fontSize = 9.sp, lineHeight = 11.sp, color = cs.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { AudioCap.scan(ctx) }.getOrNull()
                        }
                        busy = false
                        if (r != null) res = r
                    }
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (busy) "检测中…" else "检测", fontSize = 11.sp)
            }
        }
    }

    val r = res
    if (r != null) {
        AlertDialog(
            onDismissRequest = { res = null },
            title = { Text("检测完成", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("枚举全部可用音效成功。", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "系统共上报 ${r.total} 个音效实现，其中 ${r.usable} 个能取得控制权。",
                        fontSize = 11.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    if (r.file.isBlank()) {
                        Text("文件写入失败：请检查存储权限。", fontSize = 11.sp)
                    } else {
                        Text("文件已导出到：", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text("内部存储 / Download / DolbyTuner /", fontSize = 10.sp)
                        Text(r.file, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { res = null }) { Text("知道了") }
            }
        )
    }
}