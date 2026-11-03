package com.zero16.dolbytuner

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ALLOWED_BRANDS = setOf("xiaomi", "redmi", "poco")
private val Accent = Color(0xFF22C55E)

private fun sysProp(key: String): String = runCatching {
    val c = Class.forName("android.os.SystemProperties")
    (c.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
}.getOrDefault("")

/** 真实市场机型名（HyperOS 用 ro.product.marketname，取不到回退 Build.MODEL） */
private fun realModelName(): String {
    val keys = listOf(
        "ro.product.marketname", "ro.product.odm.marketname",
        "ro.product.vendor.marketname", "ro.product.system.marketname"
    )
    val mkt = keys.firstNotNullOfOrNull { k -> sysProp(k).takeIf { it.isNotBlank() } }
    return (mkt ?: "${Build.MANUFACTURER} ${Build.MODEL}").trim()
}

private data class GateResult(val passed: Boolean, val reasons: List<String>, val deviceLine: String)
private data class DockTab(val label: String, val icon: ImageVector)

class MainActivity : ComponentActivity() {

    private fun prop(key: String): String = runCatching {
        val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val s = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        s
    }.getOrDefault("")

    private fun evaluateGate(): GateResult {
        val reasons = mutableListOf<String>()
        val sdk = Build.VERSION.SDK_INT
        if (sdk < 36) reasons += "需要 Android 16（API 36）及以上，当前 API $sdk"
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val vendorBrand = prop("ro.product.vendor.brand").lowercase()
        val propBrand = prop("ro.product.brand").lowercase()
        val isXiaomi = listOf(brand, manufacturer, vendorBrand, propBrand).any { v ->
            ALLOWED_BRANDS.any { it == v } || v.contains("xiaomi") || v.contains("redmi") || v.contains("poco")
        }
        if (!isXiaomi) reasons += "仅支持小米 / Redmi / POCO，当前：${Build.MANUFACTURER} ${Build.BRAND}"
        return GateResult(
            reasons.isEmpty(), reasons,
            "${realModelName()} · Android ${Build.VERSION.RELEASE} (API $sdk)"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gate = evaluateGate()
        setContent {
            DolbyTunerTheme {
                if (gate.passed) MainApp(this, gate.deviceLine)
                else GateDialog(gate.reasons) { finishAffinity() }
            }
        }
    }
}

@Composable
private fun GateDialog(reasons: List<String>, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("无法运行", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("当前设备不满足运行条件", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                reasons.forEach {
                    Text("· $it", fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("退出", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** 主框架：内容区 + 底部液态玻璃 Dock（与 K90 标准版同一套实现） */
@Composable
private fun MainApp(activity: Activity, deviceLine: String) {
    var currentTab by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.red * 0.299f + colors.background.green * 0.587f + colors.background.blue * 0.114f < 0.5f
    val ctx = activity.applicationContext
    var deviceTick by remember { mutableIntStateOf(0) }

    // 设备监听放到主层：不管当前在 GEQ / 音效 / 设置，插拔都能及时响应
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { deviceTick++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { deviceTick++ }
        }
        am?.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(cb) }
    }

    LaunchedEffect(deviceTick) {
        val d = withContext(Dispatchers.IO) { detectOutputDevice(ctx) }
        DeviceState.device = d
        DeviceState.bindings = withContext(Dispatchers.IO) { DeviceTuningStore.load(ctx) }
        val prefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        val prev = prefs.getString("device_last_seen", null)
        if (prev == null) {
            prefs.edit().putString("device_last_seen", d.key).apply()
            if (prefs.getString("device_last_applied", null) == null) {
                prefs.edit().putString("device_last_applied", d.key).apply()
            }
            return@LaunchedEffect
        }
        if (prev == d.key) return@LaunchedEffect
        prefs.edit().putString("device_last_seen", d.key).apply()

        val b = DeviceState.binding
        if (b != null) {
            val p = loadPresets(ctx).firstOrNull { it.name == b.presetName }
            if (p != null) {
                val err = applyPreset(ctx, p)
                if (err == null) {
                    prefs.edit().putString("device_last_applied", d.key).apply()
                    DeviceState.notifyApplied()
                }
            }
        } else {
            // 未绑定设备：自动恢复平直 + 默认音效，避免把上一个设备的调音带到外放
            val err = resetToDefault(ctx)
            if (err == null) {
                prefs.edit().putString("device_last_applied", d.key).apply()
                DeviceState.notifyApplied()
            }
        }
    }
    // 首次启动：不同机型的杜比默认参数不同，先引导检测一次，把结果当成「重置」的基准
    val scope = rememberCoroutineScope()
    val bootPrefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
    var showBasePrompt by remember {
        mutableStateOf(!DspBaseline.captured(ctx) && !bootPrefs.getBoolean("dsp_base_prompted", false))
    }
    var baseBusy by remember { mutableStateOf(false) }
    var baseMsg by remember { mutableStateOf<String?>(null) }

    if (showBasePrompt) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("先检测本机的杜比音效默认值", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "GEQ 出厂本来就是平直；但不同机型、不同 ROM，杜比出厂时的音效参数并不一样（环绕、人声增强、低音增强）。",
                        fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点下面的按钮会读取本机 DSP 当前的音效参数，记录下来作为「重置」时音效的基准；以后点重置，GEQ 归零、音效恢复成这套值。",
                        fontSize = 12.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant
                    )

                    baseMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFFB91C1C))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !baseBusy,
                    onClick = {
                        baseBusy = true
                        baseMsg = null
                        scope.launch {
                            val err = captureDspBaseline(ctx)
                            baseBusy = false
                            if (err == null) {
                                saveFx(ctx, DspBaseline.fx(ctx))
                                bootPrefs.edit().putBoolean("dsp_base_prompted", true).apply()
                                showBasePrompt = false
                                DeviceState.notifyApplied()
                                android.widget.Toast.makeText(
                                    ctx, "已记录本机音效默认值", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                baseMsg = err
                            }
                        }
                    }
                ) { Text(if (baseBusy) "检测中…" else "检测 Dolby DSP参数", fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { activity.finishAffinity() }) { Text("退出APP", fontSize = 12.sp) }
            }
        )
    }
    val backdrop = rememberLayerBackdrop { drawContent() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { it * dir } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it * dir } + fadeOut(tween(300)))
            },
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
        ) { tab ->
            when (tab) {
                0 -> GeqScreen(deviceLine)
                1 -> EffectsScreen()
                else -> SettingsScreen(activity)
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 16.dp)
                .height(62.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Final)
                            e.changes.forEach { if (!it.isConsumed) it.consume() }
                        }
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = {
                        vibrancy()
                        blur(6.dp.toPx())
                        lens(10.dp.toPx(), 18.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(if (isDark) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.12f))
                    }
                )
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    DockTab("GEQ", Icons.Default.Equalizer),
                    DockTab("音效", Icons.Default.Tune),
                    DockTab("设置", Icons.Default.Settings)
                )
                    .forEachIndexed { index, tab ->
                        val selected = currentTab == index
                        val tint = if (selected) colors.primary
                        else colors.onSurfaceVariant.copy(alpha = if (isDark) 0.5f else 0.6f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (selected) Modifier.background(
                                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.05f),
                                        RoundedCornerShape(14.dp)
                                    ) else Modifier
                                )
                                .clickable { currentTab = index }
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(tab.icon, tab.label, Modifier.size(20.dp), tint = tint)
                            Text(tab.label, fontSize = 9.sp, lineHeight = 11.sp, color = tint)
                        }
                    }
            }
        }
    }
}

private fun loadGeq(ctx: Context): IntArray {
    val raw = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE).getString("geq", null)
        ?: return IntArray(DapRealtime.BANDS)
    val a = raw.split(",").mapNotNull { it.toIntOrNull() }
    return if (a.size == DapRealtime.BANDS) a.toIntArray() else IntArray(DapRealtime.BANDS)
}

private fun saveGeq(ctx: Context, gains: IntArray) {
    ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        .edit().putString("geq", gains.joinToString(",")).apply()
}

@Composable
private fun GeqScreen(deviceLine: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var curve by remember { mutableStateOf(loadGeq(ctx)) }
    var status by remember { mutableStateOf("正在连接 Dolby DSP…") }
    var dolbyOn by remember { mutableStateOf<Boolean?>(null) }
    val device = DeviceState.device
    val allBindings = DeviceState.bindings
    val deviceBinding = DeviceState.binding
    var showBindDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val err = withContext(Dispatchers.IO) { DapRealtime.openSession() }
        if (err != null) {
            status = "打开失败：$err"
        } else {
            // 先按 DSP 中的真实曲线回读；读不到才把本地记录写下去
            val live = withContext(Dispatchers.IO) { DapRealtime.readLive() }
            if (live != null && live.size == DapRealtime.BANDS) {
                curve = live
                saveGeq(ctx, live)
                status = "已同步 · 当前为 DSP 中生效的曲线"
            } else {
                val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(curve) }
                status = if (e == null) "已连接 · 20 段实时生效（DSP 不支持回读）" else "写入失败：$e"
            }
        }
        dolbyOn = withContext(Dispatchers.IO) { DapRealtime.dolbyState() }
    }
    DisposableEffect(Unit) { onDispose { DapRealtime.closeSession() } }

    // 设备切换由主层统一处理，这里只跟随结果刷新曲线
    LaunchedEffect(DeviceState.changeTick) {
        if (DeviceState.changeTick > 0) curve = loadGeq(ctx)
    }

    fun push(index: Int, q4: Int) {
        val next = curve.copyOf()
        next[index] = q4
        curve = next
        saveGeq(ctx, next)
        status = "写入中…"
        kotlinx.coroutines.MainScope().launch {
            val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(next) }
            status = if (e == null) "已写入 · 全部 profile" else "写入失败：$e"
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 96.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Dolby Tuner", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(deviceLine, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.14f))
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎧", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("当前设备", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(device.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("绑定预设：${deviceBinding?.presetName ?: "未绑定"}", fontSize = 11.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showBindDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) { Text("绑定当前设备", fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { DeviceState.sync(ctx); showManageDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) { Text("管理绑定", fontSize = 11.sp) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "提示：切到未绑定的设备时会自动重置为平直（GEQ 归零 + 音效默认），不想丢调音就把外放也绑上预设。",
                    fontSize = 9.sp, lineHeight = 13.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Equalizer, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("20 段图形均衡器", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "47 Hz ~ 19.7 kHz · 实时写入 Dolby DSP",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DolbyBadge(on = dolbyOn)
                }
                Spacer(Modifier.height(6.dp))
                BandCurveCanvas(
                    points = DapRealtime.FREQS.mapIndexed { i, f -> f to curve[i] },
                    lineColor = Accent,
                    showDelta = false
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    items(DapRealtime.BANDS) { i ->
                        DapBandSlider(
                            freqLabel = if (DapRealtime.FREQS[i] >= 1000)
                                fmtFreq(DapRealtime.FREQS[i])
                            else "${DapRealtime.FREQS[i]}",
                            valueQ4 = curve[i],
                            onChange = { q4 -> push(i, q4) },
                            maxAbsQ4 = DapRealtime.MAX_BOOST_Q4,
                            trackHeight = 240.dp,
                            accent = Accent,
                            trackColor = cs.outline.copy(alpha = 0.30f),
                            tickColor = cs.onSurfaceVariant,
                            valueColor = cs.onSurfaceVariant,
                            freqColor = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Swipe, null,
                        tint = cs.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "左右滑动查看 20 段 · 上下拖动调节 · 双击某段归零",
                        color = cs.onSurfaceVariant.copy(alpha = 0.55f), fontSize = 9.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "单位：dB（1 段 = 1/16 dB）。合计建议不超过 +24 dB，超过 +10 dB 会标黄提示。",
                    color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.outline)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                status = "正在读取…"
                                val read = withContext(Dispatchers.IO) { DapRealtime.readCurve() }
                                if (read != null) {
                                    curve = read
                                    status = "已读取当前曲线"
                                } else {
                                    status = "读取失败：该机型不支持回读（setter-only）"
                                }
                                dolbyOn = withContext(Dispatchers.IO) { DapRealtime.dolbyState() }
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("读取当前", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val flat = IntArray(DapRealtime.BANDS) { 0 }
                            curve = flat
                            saveGeq(ctx, flat)
                            status = "写入中…"
                            scope.launch {
                                val e = withContext(Dispatchers.IO) { DapRealtime.writeLive(flat) }
                                status = if (e == null) "已置为平直曲线（点保存才会持久化）" else "失败：$e"
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309))
                    ) {
                        Icon(Icons.Default.HorizontalRule, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置平直", fontSize = 11.sp)
                    }
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            status = withContext(Dispatchers.IO) { DapRealtime.probeRead() }
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        "读不到实时值？点这里诊断", fontSize = 9.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(status, fontSize = 10.sp, color = cs.onSurfaceVariant, maxLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    if (showBindDialog) {
        val list = loadPresets(ctx)
        AlertDialog(
            onDismissRequest = { showBindDialog = false },
            title = { Text("绑定当前设备", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(device.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("选择一个预设，切到这台设备时自动套用", fontSize = 10.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (list.isEmpty()) {
                        Text("还没有预设，先在音效页保存一个", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    list.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                DeviceTuningStore.bind(ctx, device, p.name)
                                DeviceState.sync(ctx)
                                showBindDialog = false
                                status = "正在套用：${p.name}…"
                                scope.launch {
                                    val err = applyPreset(ctx, p)
                                    curve = loadGeq(ctx)
                                    status = if (err == null) "已绑定并套用：${p.name}" else "套用失败：$err"
                                    if (err == null) ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
                                        .edit().putString("device_last_applied", device.key).apply()
                                }
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("GEQ ${p.geq.count { it != 0 }} 段", fontSize = 9.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showBindDialog = false }) { Text("取消", fontSize = 12.sp) } }
        )
    }

    if (showManageDialog) {
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("管理绑定", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (allBindings.isEmpty()) {
                        Text("还没有任何设备绑定", fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    allBindings.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.deviceName, fontSize = 12.sp)
                                Text("→ ${b.presetName}", fontSize = 9.sp, color = cs.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                DeviceTuningStore.unbind(ctx, b.key)
                                DeviceState.sync(ctx)
                            }) { Text("删除", fontSize = 11.sp, color = Color(0xFFB45309)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showManageDialog = false }) { Text("关闭", fontSize = 12.sp) } }
        )
    }
}
internal data class FxState(
    val hpVirtual: Boolean = false,
    val spVirtual: Boolean = false,
    val bassOn: Boolean = false,
    val bassGain: Int = 0,
    val bassCutoff: Int = 150,
    val bassWidth: Int = 0,
    val dialogOn: Boolean = false,
    val dialogAmt: Int = 4
)

internal data class Preset(
    val name: String,
    val geq: IntArray,
    val fx: FxState
)

private fun loadFx(ctx: Context): FxState {
    val a = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
        .getString("fx", null)?.split(",") ?: return FxState()
    return try {
        FxState(
            a.getOrNull(0) == "1", a.getOrNull(1) == "1", a.getOrNull(2) == "1",
            a.getOrNull(3)?.toIntOrNull() ?: 0,
            a.getOrNull(4)?.toIntOrNull() ?: 150,
            a.getOrNull(5)?.toIntOrNull() ?: 0,
            a.getOrNull(6) == "1",
            a.getOrNull(7)?.toIntOrNull() ?: 4
        )
    } catch (_: Exception) { FxState() }
}

private fun saveFx(ctx: Context, f: FxState) {
    val v = listOf(
        if (f.hpVirtual) "1" else "0",
        if (f.spVirtual) "1" else "0",
        if (f.bassOn) "1" else "0",
        f.bassGain.toString(), f.bassCutoff.toString(), f.bassWidth.toString(),
        if (f.dialogOn) "1" else "0", f.dialogAmt.toString()
    ).joinToString(",")
    ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE).edit().putString("fx", v).apply()
}

private fun presetFile(ctx: Context) = File(ctx.filesDir, "presets.json")

internal fun loadPresets(ctx: Context): List<Preset> {
    return try {
        val f = presetFile(ctx)
        if (!f.exists()) return emptyList()
        val arr = org.json.JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val g = o.getJSONArray("geq")
                val geq = IntArray(g.length()) { g.getInt(it) }
                if (geq.size != DapRealtime.BANDS) null else Preset(
                    o.getString("name"), geq,
                    FxState(
                        o.optInt("hp", 0) == 1, o.optInt("sp", 0) == 1,
                        o.optInt("bass", 0) == 1, o.optInt("gain", 0),
                        o.optInt("cutoff", 150), o.optInt("width", 0),
                        o.optInt("dialog", 0) == 1, o.optInt("damt", 4)
                    )
                )
            }.getOrNull()
        }
    } catch (_: Exception) { emptyList() }
}

private fun writePresets(ctx: Context, list: List<Preset>) {
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
    presetFile(ctx).writeText(arr.toString(2))
}

/** 单个预设 → JSON 文本（导出用） */
private fun presetJsonString(p: Preset): String = org.json.JSONObject().apply {
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
}.toString(2)

/** 正式写入全部音效参数（失败返回原因） */
private suspend fun pushFx(next: FxState): String? = withContext(Dispatchers.IO) {
    var e = DapRealtime.setParamValue(101, if (next.hpVirtual) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(102, if (next.spVirtual) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(105, if (next.dialogOn) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(108, next.dialogAmt)
    if (e == null) e = DapRealtime.setParamValue(111, if (next.bassOn) 1 else 0)
    if (e == null) e = DapRealtime.setParamValue(233, next.bassGain)
    if (e == null) e = DapRealtime.setParamValue(234, next.bassCutoff)
    if (e == null) e = DapRealtime.setParamValue(235, next.bassWidth)
    e
}

/** 应用一条预设：GEQ 写入并提交 + 全部音效参数，成功时同步本地记录 */
internal suspend fun applyPreset(ctx: Context, p: Preset): String? {
    val e1 = withContext(Dispatchers.IO) { DapRealtime.writeLive(p.geq) }
    val e2 = if (e1 == null) withContext(Dispatchers.IO) { DapRealtime.commit(p.geq) } else e1
    val e3 = if (e2 == null) pushFx(p.fx) else e2
    if (e3 == null) {
        saveGeq(ctx, p.geq)
        saveFx(ctx, p.fx)
    }
    return e3
}

/**
 * DSP 出厂默认「音效」参数基准。
 * GEQ 出厂本来就是平直，不需要检测；真正会因机型 / ROM 而不同的是音效页这组参数
 *（环绕、人声增强、低音增强及其增益 / 截止 / 带宽），所以只把音效参数记成基准。
 */
internal object DspBaseline {
    private const val PREF = "dolby_tuner"
    private const val K_FX = "dsp_base_fx"
    private const val K_READY = "dsp_base_ready"

    fun captured(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(K_READY, false)

    fun fx(ctx: Context): FxState {
        val a = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(K_FX, null)?.split(",") ?: return FxState()
        return try {
            FxState(
                a.getOrNull(0) == "1", a.getOrNull(1) == "1", a.getOrNull(2) == "1",
                a.getOrNull(3)?.toIntOrNull() ?: 0,
                a.getOrNull(4)?.toIntOrNull() ?: 150,
                a.getOrNull(5)?.toIntOrNull() ?: 0,
                a.getOrNull(6) == "1",
                a.getOrNull(7)?.toIntOrNull() ?: 4
            )
        } catch (_: Exception) { FxState() }
    }

    fun save(ctx: Context, fx: FxState) {
        val v = listOf(
            if (fx.hpVirtual) "1" else "0",
            if (fx.spVirtual) "1" else "0",
            if (fx.bassOn) "1" else "0",
            fx.bassGain.toString(), fx.bassCutoff.toString(), fx.bassWidth.toString(),
            if (fx.dialogOn) "1" else "0", fx.dialogAmt.toString()
        ).joinToString(",")
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(K_FX, v)
            .putBoolean(K_READY, true)
            .apply()
    }
}

/**
 * 读取当前 DSP 的音效参数，作为「重置」时的音效默认值。
 * 成功返回 null；读不到时返回原因（此时保留原有基准不动）。
 */
internal suspend fun captureDspBaseline(ctx: Context): String? = withContext(Dispatchers.IO) {
    fun pick(id: Int, lo: Int, hi: Int): Int? =
        runCatching { DapRealtime.readParam(id) }.getOrNull()?.takeIf { it in lo..hi }

    val hp = pick(101, 0, 1)
    val sp = pick(102, 0, 1)
    val dialog = pick(105, 0, 1)
    val damt = pick(108, 0, 16)
    val bass = pick(111, 0, 1)
    val gain = pick(233, 0, 192)
    val cutoff = pick(234, 80, 300)
    val width = pick(235, 0, 64)
    val hit = listOf(hp, sp, dialog, damt, bass, gain, cutoff, width).count { it != null }
    if (hit == 0) return@withContext "读取失败：本机实时层不支持回读，暂时只能用内置默认值"

    DspBaseline.save(
        ctx,
        FxState(
            hpVirtual = hp == 1,
            spVirtual = sp == 1,
            bassOn = bass == 1,
            bassGain = gain ?: 0,
            bassCutoff = cutoff ?: 150,
            bassWidth = width ?: 0,
            dialogOn = dialog == 1,
            dialogAmt = damt ?: 4
        )
    )
    null
}

/** 恢复为已检测的 DSP 默认值（没检测过则退回平直 + 内置默认）；未绑定设备时也用这套 */
internal suspend fun resetToDefault(ctx: Context): String? {
    val flat = IntArray(DapRealtime.BANDS)
    val def = if (DspBaseline.captured(ctx)) DspBaseline.fx(ctx) else FxState()
    var e = withContext(Dispatchers.IO) { DapRealtime.writeLive(flat) }
    if (e == null) e = withContext(Dispatchers.IO) { DapRealtime.commit(flat) }
    if (e == null) e = pushFx(def)
    if (e == null) {
        saveGeq(ctx, flat)
        saveFx(ctx, def)
    }
    return e
}

/** 音效页：环绕 / 低音 / 重置 / 预设 */
@Composable
private fun EffectsScreen() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fx by remember { mutableStateOf(loadFx(ctx)) }
    // 滑杆实时写入通道：只保留最新值，避免拖动时写入排队
    val liveFx = remember { Channel<Pair<Int, Int>>(Channel.CONFLATED) }
    LaunchedEffect(Unit) {
        for ((id, v) in liveFx) {
            withContext(Dispatchers.IO) { DapRealtime.setParamValue(id, v) }
        }
    }
    var status by remember { mutableStateOf("实时层参数 · 改完立即生效") }
    var saving by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(loadPresets(ctx)) }
    // 设备切换（主层套用预设）后，音效页的开关与滑杆跟着刷新
    LaunchedEffect(DeviceState.changeTick) {
        if (DeviceState.changeTick > 0) fx = loadFx(ctx)
    }
    // 进入音效页时先读 DSP 真实值；读不到（或数值越界）就保留本地记录
    LaunchedEffect(Unit) {
        val synced = withContext(Dispatchers.IO) {
            fun pick(v: Int?, lo: Int, hi: Int) = v?.takeIf { it in lo..hi }
            var next = fx
            var hit = 0
            pick(DapRealtime.readParam(101), 0, 1)?.let { next = next.copy(hpVirtual = it == 1); hit++ }
            pick(DapRealtime.readParam(102), 0, 1)?.let { next = next.copy(spVirtual = it == 1); hit++ }
            pick(DapRealtime.readParam(105), 0, 1)?.let { next = next.copy(dialogOn = it == 1); hit++ }
            pick(DapRealtime.readParam(108), 0, 16)?.let { next = next.copy(dialogAmt = it); hit++ }
            pick(DapRealtime.readParam(111), 0, 1)?.let { next = next.copy(bassOn = it == 1); hit++ }
            pick(DapRealtime.readParam(233), 0, 192)?.let { next = next.copy(bassGain = it); hit++ }
            pick(DapRealtime.readParam(234), 80, 300)?.let { next = next.copy(bassCutoff = it); hit++ }
            pick(DapRealtime.readParam(235), 0, 64)?.let { next = next.copy(bassWidth = it); hit++ }
            if (hit > 0) next to hit else null
        }
        if (synced != null) {
            fx = synced.first
            saveFx(ctx, synced.first)
            status = "已同步 ${synced.second}/8 项 DSP 实时参数"
        }
    }
    DisposableEffect(Unit) { onDispose { DapRealtime.closeSession() } }
    fun reloadPresets() { presets = loadPresets(ctx) }

    fun apply(next: FxState) {
        fx = next
        saveFx(ctx, next)
        status = "写入中…"
        scope.launch {
            val err = pushFx(next)
            status = if (err == null) "已写入 · 重启后仍生效" else "写入失败：$err"
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }.getOrNull()
            }
            if (text == null) { status = "导入失败：读不到文件"; return@launch }
            try {
                val arr = org.json.JSONArray(text)
                val incoming = mutableListOf<Preset>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val g = o.getJSONArray("geq")
                    val geq = IntArray(g.length()) { g.getInt(it) }
                    if (geq.size == DapRealtime.BANDS) incoming += Preset(
                        o.getString("name"), geq,
                        FxState(o.optInt("hp", 0) == 1, o.optInt("sp", 0) == 1,
                            o.optInt("bass", 0) == 1, o.optInt("gain", 0),
                            o.optInt("cutoff", 150), o.optInt("width", 0),
                            o.optInt("dialog", 0) == 1, o.optInt("damt", 4))
                    )
                }
                val merged = (presets + incoming).distinctBy { it.name }
                writePresets(ctx, merged)
                reloadPresets()
                status = "已导入 ${incoming.size} 个预设"
            } catch (t: Throwable) { status = "导入失败：${t.message}" }
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val text = exportText.ifBlank { presetFile(ctx).readText() }
                    ctx.contentResolver.openOutputStream(uri)?.use {
                        it.write(text.toByteArray())
                    }
                }
            }
            status = "已导出预设"
            exportText = ""
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 96.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("音效", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Dolby 实时层 · 不写文件、不重启音频服务", fontSize = 11.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.85f))) {
            Column(Modifier.padding(14.dp)) {
                Text("环绕 / 声场", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FxSwitch("耳机虚拟环绕", "vdhe 101", fx.hpVirtual) { apply(fx.copy(hpVirtual = it)) }
                FxSwitch("扬声器虚拟环绕", "vspe 102", fx.spVirtual) { apply(fx.copy(spVirtual = it)) }
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.85f))) {
            Column(Modifier.padding(14.dp)) {
                Text("人声增强", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FxSwitch("启用", "deon 105", fx.dialogOn) { apply(fx.copy(dialogOn = it)) }
                FxSlider(
                    "增强量", fx.dialogAmt.toString(), fx.dialogAmt, 0..16, fx.dialogOn,
                    onLive = { fx = fx.copy(dialogAmt = it); liveFx.trySend(108 to it) }
                ) {
                    apply(fx.copy(dialogAmt = it))
                }
                Text("只提升人声频段，不压低背景（ded 未使用）", fontSize = 9.sp, color = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.85f))) {
            Column(Modifier.padding(14.dp)) {
                Text("低音", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FxSwitch("低音增强", "beon 111", fx.bassOn) { apply(fx.copy(bassOn = it)) }
                FxSlider(
                    "低音增益", fx.bassGain.toString(), fx.bassGain, 0..192, fx.bassOn,
                    onLive = { fx = fx.copy(bassGain = it); liveFx.trySend(233 to it) }
                ) {
                    apply(fx.copy(bassGain = it))
                }
                FxSlider(
                    "低音截止 · Hz", fx.bassCutoff.toString(), fx.bassCutoff, 80..300, fx.bassOn,
                    onLive = { fx = fx.copy(bassCutoff = it); liveFx.trySend(234 to it) }
                ) {
                    apply(fx.copy(bassCutoff = it))
                }
                FxSlider(
                    "低带宽", fx.bassWidth.toString(), fx.bassWidth, 0..64, fx.bassOn,
                    onLive = { fx = fx.copy(bassWidth = it); liveFx.trySend(235 to it) }
                ) {
                    apply(fx.copy(bassWidth = it))
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { showSaveConfirm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            enabled = !saving
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存到 Dolby（GEQ + 音效）", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "写入杜比引擎 + 参数存储 · 不重启音频服务 · 重启后沿用",
            fontSize = 9.sp, color = cs.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB45309))
        ) { Text("重置为 DSP 默认值（GEQ + 音效）", fontSize = 11.sp) }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.85f))) {
            Column(Modifier.padding(14.dp)) {
                Text("预设", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("保存时会把 GEQ 20 段一起收进去", fontSize = 9.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetButton(Icons.Default.Save, "保存预设", Modifier.weight(1f)) {
                        presetName = ""
                        showSaveDialog = true
                    }
                    PresetButton(Icons.Default.FolderOpen, "加载预设", Modifier.weight(1f)) {
                        reloadPresets()
                        showLoadDialog = true
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PresetButton(Icons.Default.Share, "导出预设", Modifier.weight(1f)) {
                        reloadPresets()
                        showExportDialog = true
                    }
                    PresetButton(Icons.Default.Add, "导入预设", Modifier.weight(1f)) {
                        importer.launch(arrayOf("application/json", "text/plain"))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (presets.isEmpty()) "还没有预设" else "已保存 ${presets.size} 个预设",
                    fontSize = 9.sp, color = cs.onSurfaceVariant
                )
            }
        }
Spacer(Modifier.height(10.dp))
        Text(status, fontSize = 10.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("保存预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("会把当前 GEQ 20 段与全部音效参数一起保存", fontSize = 10.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            label = { Text("预设名称", fontSize = 11.sp) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = presetName.trim().ifBlank { "预设 ${presets.size + 1}" }
                        writePresets(ctx, presets.filterNot { it.name == name } + Preset(name, loadGeq(ctx), fx))
                        reloadPresets()
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

        if (showLoadDialog) {
            AlertDialog(
                onDismissRequest = { showLoadDialog = false },
                title = { Text("加载预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (presets.isEmpty()) Text("还没有预设", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        presets.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 12.sp)
                                    Text(
                                        "GEQ ${p.geq.count { it != 0 }} 段非零 · 环绕 ${listOf(p.fx.hpVirtual, p.fx.spVirtual).count { it }}/2 · 人声${if (p.fx.dialogOn) "开" else "关"} · 低音${if (p.fx.bassOn) "开" else "关"}",
                                        fontSize = 9.sp, color = cs.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    status = "正在加载：${p.name}"
                                    showLoadDialog = false
                                    scope.launch {
                                        val e1 = withContext(Dispatchers.IO) { DapRealtime.writeLive(p.geq) }
                                        val e2 = if (e1 == null) withContext(Dispatchers.IO) { DapRealtime.commit(p.geq) } else e1
                                        val e3 = if (e2 == null) pushFx(p.fx) else e2
                                        if (e3 == null) {
                                            fx = p.fx
                                            saveFx(ctx, p.fx)
                                            saveGeq(ctx, p.geq)
                                            status = "已加载：${p.name}"
                                        } else status = "加载失败：$e3"
                                    }
                                }) { Text("应用", fontSize = 11.sp, color = Accent) }
                                TextButton(onClick = {
                                    writePresets(ctx, loadPresets(ctx).filterNot { it.name == p.name })
                                    reloadPresets()
                                    status = "已删除：${p.name}"
                                }) { Text("删除", fontSize = 11.sp, color = Color(0xFFB45309)) }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showLoadDialog = false }) { Text("关闭", fontSize = 12.sp) }
                }
            )
        }

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("导出预设", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (presets.isEmpty()) Text("还没有预设", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        presets.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    exportText = presetJsonString(p)
                                    showExportDialog = false
                                    exporter.launch("DolbyTuner_${p.name}.json")
                                }) { Text("导出", fontSize = 11.sp, color = Accent) }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) { Text("关闭", fontSize = 12.sp) }
                }
            )
        }
        if (showSaveConfirm) {
            AlertDialog(
                onDismissRequest = { showSaveConfirm = false },
                title = { Text("保存到 Dolby", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("将把当前 GEQ 20 段与全部音效参数写入：", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("① 杜比引擎内存（立刻生效）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text("② Dolby DAP 参数存储（重启后沿用）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("不重启音频服务。确定保存？", fontSize = 11.sp, color = Accent)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSaveConfirm = false
                        if (saving) return@TextButton
                        saving = true
                        status = "正在保存 GEQ + 音效…"
                        scope.launch {
                            val g = loadGeq(ctx)
                            var e = withContext(Dispatchers.IO) { DapRealtime.writeLive(g) }
                            if (e == null) e = withContext(Dispatchers.IO) { DapRealtime.commit(g) }
                            val err = if (e == null) pushFx(fx) else e
                            status = if (err == null) "已保存 · GEQ + 音效重启后沿用" else "保存失败：$err"
                            if (err == null) showSavedDialog = true
                            saving = false
                        }
                    }) { Text("保存", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveConfirm = false }) { Text("取消", fontSize = 12.sp) }
                }
            )
        }

        if (showResetConfirm) {
            val hasBase = DspBaseline.captured(ctx)
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("重置为 DSP 默认值", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("将执行：", fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("• GEQ 20 段全部归零（出厂即平直）", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        if (hasBase) {
                            Text("• 音效恢复为检测到的本机默认状态", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        } else {
                            Text("• 音效恢复内置默认：环绕 / 人声增强 / 低音增强全部关闭，低音增益与带宽归 0", fontSize = 11.sp, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("尚未检测本机音效默认参数，当前使用的是内置默认值。", fontSize = 11.sp, color = Color(0xFFB45309))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("会立即写入 Dolby，此操作不可撤销。", fontSize = 11.sp, color = Color(0xFFB45309))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        val def = if (hasBase) DspBaseline.fx(ctx) else FxState()
                        fx = def
                        saveFx(ctx, def)
                        saveGeq(ctx, IntArray(DapRealtime.BANDS))
                        status = "正在重置…"
                        scope.launch {
                            val e1 = resetToDefault(ctx)
                            status = when {
                                e1 != null -> "重置失败：$e1"
                                hasBase -> "已恢复为本机 DSP 默认值"
                                else -> "已恢复为内置默认值（未检测本机参数）"
                            }
                        }
                    }) { Text("重置", color = Color(0xFFB45309), fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) { Text("取消", fontSize = 12.sp) }
                }
            )
        }

        if (showSavedDialog) {
            AlertDialog(
                onDismissRequest = { showSavedDialog = false },
                title = { Text("已保存到杜比", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column {
                        Text("GEQ 20 段 + 全部音效参数已写入：", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("① 杜比引擎内存（立刻生效）", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
                        Text("② Dolby DAP 参数存储（重启后沿用）", fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("重启手机后会继续沿用这次保存的参数。", fontSize = 11.sp, color = Accent, lineHeight = 16.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSavedDialog = false }) {
                        Text("知道了", color = Accent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun PresetButton(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 11.sp)
    }
}

@Composable
private fun FxSwitch(title: String, code: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp)
            Text(code, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FxSlider(
    title: String, valueText: String, value: Int, range: IntRange,
    enabled: Boolean, onLive: (Int) -> Unit = {}, onDone: (Int) -> Unit
) {
    // 与 GEQ 滑杆同一套做法：拖动期间以本地值为准，不被外部 state 回灌打断
    var local by remember { mutableStateOf(value.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!dragging) local = value.toFloat()
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 12.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text(
                if (dragging) local.toInt().coerceIn(range.first, range.last).toString() else valueText,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Slider(
            value = local,
            onValueChange = {
                dragging = true
                local = it
                // 拖动过程中就写 DSP，松手再走完整保存
                onLive(it.toInt().coerceIn(range.first, range.last))
            },
            onValueChangeFinished = {
                dragging = false
                onDone(local.toInt().coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled
        )
    }
}

@Composable
private fun SettingsScreen(activity: Activity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var themeMode by remember { mutableStateOf(ThemePrefs.getMode(ctx)) }
    val prefs = ctx.getSharedPreferences("dolby_tuner", Context.MODE_PRIVATE)
    var bootWrite by remember { mutableStateOf(prefs.getBoolean("boot_writeback", false)) }
    var showBootDialog by remember { mutableStateOf(false) }
    var deviceService by remember { mutableStateOf(prefs.getBoolean("device_service", false)) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var bootDialogFailed by remember { mutableStateOf(false) }
    fun openAutostart(): Boolean = runCatching {
        ctx.startActivity(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
        )
        true
    }.getOrDefault(false)
    var showLicense by remember { mutableStateOf(false) }
    val licenseText = remember {
        runCatching { ctx.assets.open("license_apache2.txt").bufferedReader().use { it.readText() } }
            .getOrNull().orEmpty()
    }
    var wallpaperUri by remember { mutableStateOf(ThemePrefs.getWallpaperUri(ctx)) }
    var baseReady by remember { mutableStateOf(DspBaseline.captured(ctx)) }
    var baseBusy by remember { mutableStateOf(false) }
    val verName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.9.3"
    }

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
                    activity.recreate()
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp).padding(bottom = 96.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("设置", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Text("外观", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Default.LightMode, title = "浅色",
                selected = themeMode == ThemeMode.LIGHT
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.LIGHT); activity.recreate()
            }
            SettingRow(
                icon = Icons.Default.DarkMode, title = "深色",
                selected = themeMode == ThemeMode.DARK
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.DARK); activity.recreate()
            }
            SettingRow(
                icon = Icons.Default.Settings, title = "跟随系统",
                selected = themeMode == ThemeMode.SYSTEM
            ) {
                ThemePrefs.setMode(ctx, ThemeMode.SYSTEM); activity.recreate()
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RestartAlt, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("开机参数自动写回", fontSize = 13.sp)
                    Text(
                        if (bootWrite) "已开启 · 开机时重新下发 GEQ 与音效" else "关闭时开机不写入任何参数",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = bootWrite,
                    onCheckedChange = { on ->
                        bootWrite = on
                        prefs.edit().putBoolean("boot_writeback", on).apply()
                        if (on) {
                            bootDialogFailed = false
                            showBootDialog = true
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Tune, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("后台设备监听", fontSize = 13.sp)
                    Text(
                        if (deviceService) "息屏时也会按绑定预设自动切换" else "只在 APP 打开时检测设备",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                androidx.compose.material3.Switch(
                    checked = deviceService,
                    onCheckedChange = { on ->
                        deviceService = on
                        prefs.edit().putBoolean("device_service", on).apply()
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            runCatching {
                                ctx.startForegroundService(Intent(ctx, DeviceAutomationService::class.java))
                            }
                        } else {
                            runCatching { ctx.stopService(Intent(ctx, DeviceAutomationService::class.java)) }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("DSP音效默认值", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (baseReady) "重置基准：已按本机检测（音效参数）"
                else "重置基准：内置默认值（尚未检测本机）",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SettingRow(
                icon = Icons.Default.Refresh,
                title = if (baseReady) "重新检测" else "检测 Dolby DSP音效参数",
                subtitle = if (baseBusy) "检测中…" else "读取当前 DSP音效参数作为重置基准",
                selected = false
            ) {
                if (baseBusy) return@SettingRow
                baseBusy = true
                scope.launch {
                    val err = captureDspBaseline(ctx)
                    baseBusy = false
                    if (err == null) {
                        baseReady = true
                        android.widget.Toast.makeText(
                            ctx, "已记录本机音效默认值", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            ctx, err, android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            Text(
                "检测会读取当前 Dolby DSP 里的音效参数作为基准；GEQ 出厂即平直，不需要检测。",
                fontSize = 9.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("壁纸", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            SettingRow(
                icon = Icons.Default.Wallpaper, title = "自定义壁纸",
                subtitle = if (wallpaperUri != null) "已设置" else "未设置",
                selected = false
            ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            if (wallpaperUri != null) {
                SettingRow(
                    icon = Icons.Default.RestartAlt, title = "移除壁纸",
                    selected = false
                ) {
                    ThemePrefs.setWallpaperUri(ctx, null)
                    wallpaperUri = null
                    activity.recreate()
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("关于", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            AboutRow("开发者", "016. · Aoi")
            AboutRow("机型", realModelName())
            AboutRow("版本", "v$verName")
            SettingRow(
                icon = Icons.Default.Description, title = "开源许可证",
                subtitle = "Apache-2.0", selected = false
            ) { showLicense = true }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Dolby Tuner · 无 root 方案\nGEQ 通过 Dolby DAP 参数 110 实时写入",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showBootDialog) {
        AlertDialog(
            onDismissRequest = { showBootDialog = false },
            title = { Text("开机参数自动写回", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    if (bootDialogFailed) {
                        Text("无法自动打开自启动设置。", fontSize = 12.sp, color = Color(0xFFB45309))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("已开启。请允许 Dolby Tuner 自启动，否则重启后系统不会唤醒 APP 写回参数。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text("路径：设置 → 应用设置 → 授权管理 → 自启动 → 允许 Dolby Tuner", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (openAutostart()) showBootDialog = false else bootDialogFailed = true
                }) { Text("去设置", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showBootDialog = false }) { Text("稍后", fontSize = 12.sp) }
            }
        )
    }

    if (showLicense) {
        Dialog(onDismissRequest = { showLicense = false }) {
            LicenseDialogContent(
                title = "开源许可 — Apache-2.0",
                summary = "本工程以 Apache License 2.0 发布。\n" +
                    "可自由使用、修改、分发，须保留版权与许可声明，并附带 NOTICE 文件（如有）。",
                original = licenseText,
                onDismiss = { showLicense = false }
            )
        }
    }
}

@Composable
private fun LicenseDialogContent(
    title: String,
    summary: String,
    original: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title, modifier = Modifier.weight(1f),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Accent
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "返回", modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "许可证原文", fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    original.ifBlank { "未找到许可证原文。" },
                    fontSize = 9.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("返回", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * 只读状态徽章：外形是按钮，但不可点击。
 * 绿 = 杜比 DAP 生效中；红 = 杜比未生效；灰 = 无法判断（本机无 DAP）。
 */
@Composable
private fun DolbyBadge(on: Boolean?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val color = when (on) {
        true -> Color(0xFF22C55E)
        false -> Color(0xFFEF4444)
        null -> cs.onSurfaceVariant.copy(alpha = 0.55f)
    }
    val label = when (on) {
        true -> "生效中"
        false -> "未生效"
        null -> "未知"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text("Dolby", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.sp, color = color.copy(alpha = 0.85f))
    }
}
